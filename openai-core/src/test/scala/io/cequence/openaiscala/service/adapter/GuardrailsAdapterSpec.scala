package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.{OpenAIScalaClientException, OpenAIScalaGuardrailException}
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.guardrails._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.guardrails.{InputGuardrail, OutputGuardrail}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class GuardrailsAdapterSpec extends AnyWordSpec with Matchers {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private def await[T](future: Future[T]): T = Await.result(future, 10.seconds)

  private val adapters = OpenAIServiceAdapters.forChatCompletionService

  private val usage =
    UsageInfo(prompt_tokens = 50, total_tokens = 60, completion_tokens = Some(10))

  // replies with the scripted contents in turn and records the calls
  private class ScriptedChat(replies: String*) extends OpenAIChatCompletionService {
    private var remaining = replies.toList
    val calls = ListBuffer[Seq[BaseMessage]]()
    @volatile var closed = false

    private def next(messages: Seq[BaseMessage]): String = synchronized {
      calls += messages
      val reply =
        remaining.headOption.getOrElse(throw new IllegalStateException("No more replies."))
      remaining = remaining.drop(1)
      reply
    }

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] = {
      val reply = next(messages)
      Future.successful(
        ChatCompletionResponse(
          id = s"call-${calls.size}",
          created = new java.util.Date(0L),
          model = settings.model,
          system_fingerprint = None,
          choices =
            Seq(ChatCompletionChoiceInfo(AssistantMessage(reply), 0, Some("stop"), None)),
          usage = Some(usage),
          originalResponse = Some("native")
        )
      )
    }

    override def createChatToolCompletion(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool],
      responseToolChoice: Option[String],
      settings: CreateChatCompletionSettings
    ): Future[ChatToolCompletionResponse] = {
      val reply = next(messages)
      val message =
        if (reply.startsWith("call:"))
          AssistantToolMessage(tool_calls = Seq("c1" -> FunctionCallSpec(reply.drop(5), "{}")))
        else AssistantToolMessage(content = Some(reply))
      Future.successful(
        ChatToolCompletionResponse(
          id = s"call-${calls.size}",
          created = new java.util.Date(0L),
          model = settings.model,
          system_fingerprint = None,
          choices = Seq(ChatToolCompletionChoiceInfo(message, 0, Some("stop"))),
          usage = Some(usage)
        )
      )
    }

    override def close(): Unit = closed = true
  }

  // flags any text containing one of the words, as the check named by the word
  private class WordGuardrail(
    override val name: String,
    words: String*
  ) extends InputGuardrail
      with OutputGuardrail {
    val checked = ListBuffer[String]()

    private def verdict(
      stage: GuardrailStage,
      text: String
    ) = synchronized {
      checked += text
      GuardrailVerdict(
        name,
        stage,
        words.map(word => GuardrailCheckResult(word, text.contains(word)))
      )
    }

    override def checkInput(messages: Seq[BaseMessage]): Future[GuardrailVerdict] =
      Future(
        verdict(
          GuardrailStage.Input,
          messages.flatMap(BaseMessage.getTextContent).mkString(" ")
        )
      )

    override def checkOutput(
      messages: Seq[BaseMessage],
      reply: String
    ): Future[GuardrailVerdict] =
      Future(verdict(GuardrailStage.Output, reply))
  }

  private val settings = CreateChatCompletionSettings("main-model")

  "The guardrails adapter" should {

    "pass a clean call through, checking its input and its reply" in {
      val chat = new ScriptedChat("A NDA is a non-disclosure agreement.")
      val guard = new WordGuardrail("words", "hack", "secret")
      val verdicts = ListBuffer[GuardrailVerdict]()

      val guarded = adapters.guardrails(
        input = Seq(guard),
        output = Seq(guard),
        onVerdict = v => verdicts.synchronized(verdicts += v)
      )(chat)

      val response =
        await(guarded.createChatCompletion(Seq(UserMessage("What is a NDA?")), settings))

      response.contentHead shouldBe "A NDA is a non-disclosure agreement."
      response.originalResponse shouldBe Some("native")
      guard.checked shouldBe Seq("What is a NDA?", "A NDA is a non-disclosure agreement.")
      verdicts.map(_.stage) shouldBe Seq(GuardrailStage.Input, GuardrailStage.Output)
    }

    "reject a flagged input without making the call" in {
      val chat = new ScriptedChat("should not be asked")
      val guarded = adapters.guardrails(input = Seq(new WordGuardrail("words", "hack")))(chat)

      val e = intercept[OpenAIScalaGuardrailException](
        await(
          guarded
            .createChatCompletion(Seq(UserMessage("How do I hack my boss's email?")), settings)
        )
      )

      e.stage shouldBe GuardrailStage.Input
      e.verdict.category shouldBe Some("hack")
      e.getMessage shouldBe "The input was blocked - the guardrail 'words' flagged hack."
      e shouldBe an[OpenAIScalaClientException]
      chat.calls shouldBe empty
    }

    "answer a flagged input with a block message when asked to respond" in {
      val chat = new ScriptedChat()
      val guarded = adapters.guardrails(
        input = Seq(new WordGuardrail("words", "hack")),
        onViolation = GuardrailAction.Respond()
      )(chat)

      val response =
        await(guarded.createChatCompletion(Seq(UserMessage("hack it")), settings))

      response.contentHead shouldBe
        "I can't help with this request. It was flagged by the content policy (hack)."
      response.choices.head.finish_reason shouldBe Some("content_filter")
      response.model shouldBe "main-model"
      response.usage shouldBe None
      response.originalResponse.collect { case block: GuardrailBlock =>
        block.verdicts.map(_.guardrail)
      } shouldBe Some(Seq("words"))
      chat.calls shouldBe empty
    }

    "run every input guardrail and report all their verdicts" in {
      val chat = new ScriptedChat()
      val first = new WordGuardrail("first", "hack")
      val second = new WordGuardrail("second", "secret")
      val verdicts = ListBuffer[GuardrailVerdict]()

      val guarded = adapters.guardrails(
        input = Seq(first, second),
        onVerdict = v => verdicts.synchronized(verdicts += v)
      )(chat)

      val e = intercept[OpenAIScalaGuardrailException](
        await(guarded.createChatCompletion(Seq(UserMessage("hack the secret")), settings))
      )

      e.verdicts.map(_.guardrail) shouldBe Seq("first", "second")
      verdicts.map(_.guardrail).sorted shouldBe Seq("first", "second")
    }

    "reject a flagged reply, keeping nothing of it" in {
      val chat = new ScriptedChat("The secret is 42.")
      val guarded =
        adapters.guardrails(output = Seq(new WordGuardrail("words", "secret")))(chat)

      val e = intercept[OpenAIScalaGuardrailException](
        await(guarded.createChatCompletion(Seq(UserMessage("Tell me")), settings))
      )

      e.stage shouldBe GuardrailStage.Output
      e.getMessage shouldBe "The output was blocked - the guardrail 'words' flagged secret."
    }

    "answer a flagged reply with a block message, keeping the call's id and usage" in {
      val chat = new ScriptedChat("The secret is 42.")
      val guarded = adapters.guardrails(
        output = Seq(new WordGuardrail("words", "secret")),
        onViolation = GuardrailAction.respond("Sorry, I can't share that.")
      )(chat)

      val response = await(guarded.createChatCompletion(Seq(UserMessage("Tell me")), settings))

      response.contentHead shouldBe "Sorry, I can't share that."
      response.choices.head.finish_reason shouldBe Some("content_filter")
      response.id shouldBe "call-1"
      response.usage shouldBe Some(usage)
      response.originalResponse shouldBe Some(
        GuardrailBlock(
          response.originalResponse.get.asInstanceOf[GuardrailBlock].verdicts,
          Some("native")
        )
      )
    }

    "ask again for a flagged reply, summing the usage" in {
      val chat = new ScriptedChat("The secret is 42.", "I can't share that.")
      val guarded = adapters.guardrails(
        output = Seq(new WordGuardrail("words", "secret")),
        outputReprompts = 1
      )(chat)

      val response = await(guarded.createChatCompletion(Seq(UserMessage("Tell me")), settings))

      response.contentHead shouldBe "I can't share that."
      response.usage.map(_.total_tokens) shouldBe Some(120)
      chat.calls(1) shouldBe Seq(
        UserMessage("Tell me"),
        AssistantMessage("The secret is 42."),
        UserMessage(
          "Your previous reply was blocked by a content check (secret). Answer my previous message again, without that problem."
        )
      )
    }

    "block once the reprompts are used up" in {
      val chat = new ScriptedChat("secret one", "secret two", "secret three")
      val guarded = adapters.guardrails(
        output = Seq(new WordGuardrail("words", "secret")),
        outputReprompts = 2
      )(chat)

      intercept[OpenAIScalaGuardrailException](
        await(guarded.createChatCompletion(Seq(UserMessage("Tell me")), settings))
      )
      chat.calls should have size 3
      // each attempt follows the original messages, not the earlier attempts
      chat.calls(2).map(BaseMessage.getTextContent(_).get).take(2) shouldBe Seq(
        "Tell me",
        "secret two"
      )
    }

    "not ask again when the guard was unavailable" in {
      val chat = new ScriptedChat("Hello", "never asked")
      val down = new OutputGuardrail {
        override val name = "down"
        override def checkOutput(
          messages: Seq[BaseMessage],
          reply: String
        ) =
          Future.successful(
            GuardrailVerdict
              .failedClosed(name, GuardrailStage.Output, new RuntimeException("503"))
          )
      }
      val guarded = adapters.guardrails(
        output = Seq(down),
        onViolation = GuardrailAction.Respond(),
        outputReprompts = 3
      )(chat)

      val response = await(guarded.createChatCompletion(Seq(UserMessage("Hi")), settings))

      response.contentHead shouldBe
        "I can't answer right now - the content check is unavailable. Please try again later."
      chat.calls should have size 1
    }

    "fail the call when a guardrail fails, and ignore a failing verdict callback" in {
      val chat = new ScriptedChat("Hello")
      val broken = new InputGuardrail {
        override val name = "broken"
        override def checkInput(messages: Seq[BaseMessage]) =
          throw new IllegalStateException("broken guardrail")
      }

      val guarded = adapters.guardrails(input = Seq(broken))(chat)
      intercept[IllegalStateException](
        await(guarded.createChatCompletion(Seq(UserMessage("Hi")), settings))
      ).getMessage shouldBe "broken guardrail"

      val withBadCallback = adapters.guardrails(
        input = Seq(new WordGuardrail("words", "hack")),
        onVerdict = _ => throw new RuntimeException("callback")
      )(chat)
      await(
        withBadCallback.createChatCompletion(Seq(UserMessage("Hi")), settings)
      ).contentHead shouldBe "Hello"
    }

    "guard tool completions, leaving a tool-call-only reply unchecked" in {
      val chat = new ScriptedChat("call:weather", "The secret is 42.")
      val guard = new WordGuardrail("words", "secret")
      val guarded = adapters.guardrails(
        input = Seq(guard),
        output = Seq(guard),
        onViolation = GuardrailAction.respond("Blocked.")
      )(chat)

      val tools = Seq(AssistantTool.FunctionTool("weather"))

      val toolCall = await(
        guarded.createChatToolCompletion(Seq(UserMessage("Weather?")), tools, None, settings)
      )
      toolCall.choices.head.message.tool_calls.map(_._1) shouldBe Seq("c1")
      guard.checked shouldBe Seq("Weather?")

      val blocked = await(
        guarded.createChatToolCompletion(Seq(UserMessage("Weather?")), tools, None, settings)
      )
      blocked.choices.head.message.content shouldBe Some("Blocked.")
      blocked.choices.head.message.tool_calls shouldBe Nil
      blocked.choices.head.finish_reason shouldBe Some("content_filter")
      blocked.id shouldBe "call-2"
    }

    "answer only the flagged replies of several choices" in {
      val twoChoices = new OpenAIChatCompletionService {
        override def createChatCompletion(
          messages: Seq[BaseMessage],
          settings: CreateChatCompletionSettings
        ): Future[ChatCompletionResponse] =
          Future.successful(
            ChatCompletionResponse(
              id = "two",
              created = new java.util.Date(0L),
              model = settings.model,
              system_fingerprint = None,
              choices = Seq(
                ChatCompletionChoiceInfo(
                  AssistantMessage("A clean answer."),
                  0,
                  Some("stop"),
                  None
                ),
                ChatCompletionChoiceInfo(
                  AssistantMessage("The secret is 42."),
                  1,
                  Some("stop"),
                  None
                )
              ),
              usage = Some(usage),
              originalResponse = Some("native")
            )
          )

        override def close(): Unit = ()
      }
      val guarded = adapters.guardrails(
        output = Seq(new WordGuardrail("words", "secret")),
        onViolation = GuardrailAction.respond("Blocked.")
      )(twoChoices)

      val response = await(
        guarded.createChatCompletion(Seq(UserMessage("Tell me")), settings.copy(n = Some(2)))
      )

      response.choices.map(_.message.content) shouldBe Seq("A clean answer.", "Blocked.")
      response.choices.map(_.finish_reason) shouldBe Seq(Some("stop"), Some("content_filter"))
      response.choices.map(_.index) shouldBe Seq(0, 1)
      response.id shouldBe "two"
      response.usage shouldBe Some(usage)
      response.originalResponse.collect { case block: GuardrailBlock =>
        block.originalResponse
      } shouldBe Some(Some("native"))
    }

    "not ask again when the call resumes a paused run - its approval decisions are one-shot" in {
      val chat = new ScriptedChat("The secret is 42.", "never asked")
      val guarded = adapters.guardrails(
        output = Seq(new WordGuardrail("words", "secret")),
        onViolation = GuardrailAction.respond("Blocked."),
        outputReprompts = 2
      )(chat)
      val request =
        ChatChunk.ToolApprovalRequest("r1", "ask", "{}", None, "resp_1", Json.obj())

      val response = await(
        guarded.createChatCompletion(
          Seq(UserMessage("Tell me")),
          settings.setToolApprovalDecisions(Seq(request.approve))
        )
      )

      response.contentHead shouldBe "Blocked."
      chat.calls should have size 1
    }

    "close the underlying service" in {
      val chat = new ScriptedChat()
      adapters.guardrails(input = Seq(new WordGuardrail("words", "x")))(chat).close()
      chat.closed shouldBe true
    }

    "refuse negative reprompts" in {
      an[IllegalArgumentException] should be thrownBy
        adapters.guardrails(outputReprompts = -1)(new ScriptedChat())
    }
  }
}
