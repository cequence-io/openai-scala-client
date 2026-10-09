package io.cequence.openaiscala.service.adapter

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.OpenAIScalaGuardrailException
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.guardrails._
import io.cequence.openaiscala.domain.response.ChatChunk.FinishReason
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.service.guardrails.{InputGuardrail, OutputGuardrail}
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

class GuardrailsStreamedAdapterSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("guardrails-streamed-spec")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(20, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private val settings = CreateChatCompletionSettings("main-model")
  private val usage =
    UsageInfo(prompt_tokens = 50, total_tokens = 60, completion_tokens = Some(10))

  // streams the scripted typed chunks, one script per call; the OpenAI-shaped stream is
  // derived from the same script (Thinking is left out of it)
  private class ScriptedStreams(scripts: Seq[ChatChunk]*)
      extends OpenAIChatCompletionService
      with OpenAIChatCompletionStreamedServiceExtra {
    private var remaining = scripts.toList
    val calls = ListBuffer[Seq[BaseMessage]]()

    private def next(messages: Seq[BaseMessage]): Seq[ChatChunk] = synchronized {
      calls += messages
      val script = remaining.headOption.getOrElse(sys.error("No more scripts."))
      remaining = remaining.drop(1)
      script
    }

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] = {
      val text = next(messages).collect { case ChatChunk.Text(text) => text }.mkString
      Future.successful(
        ChatCompletionResponse(
          "sync",
          new java.util.Date(0L),
          settings.model,
          None,
          Seq(ChatCompletionChoiceInfo(AssistantMessage(text), 0, Some("stop"), None)),
          Some(usage),
          None
        )
      )
    }

    override def createChatToolCompletionStreamed(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool],
      responseToolChoice: Option[String],
      settings: CreateChatCompletionSettings
    ): Source[ChatChunk, NotUsed] =
      Source.lazySource(() => Source(next(messages).toList)).mapMaterializedValue(_ => NotUsed)

    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Source[ChatCompletionChunkResponse, NotUsed] =
      Source
        .lazySource(() => Source(next(messages).flatMap(openAIChunk).toList))
        .mapMaterializedValue(_ => NotUsed)

    override def close(): Unit = ()
  }

  private def openAIChunk(chunk: ChatChunk): Option[ChatCompletionChunkResponse] = {
    def response(
      delta: ChunkMessageSpec,
      finish: Option[String] = None,
      chunkUsage: Option[UsageInfo] = None
    ) =
      ChatCompletionChunkResponse(
        "oa-1",
        new java.util.Date(0L),
        "main-model",
        None,
        if (chunkUsage.isDefined) Nil
        else Seq(ChatCompletionChoiceChunkInfo(delta, 0, finish)),
        chunkUsage
      )

    chunk match {
      case _: ChatChunk.Start =>
        Some(response(ChunkMessageSpec(Some(ChatRole.Assistant), None)))
      case ChatChunk.Text(text) => Some(response(ChunkMessageSpec(None, Some(text))))
      case ChatChunk.Finish(reason, _) =>
        Some(response(ChunkMessageSpec(None, None), finish = Some(reason.toString)))
      case ChatChunk.Usage(chunkUsage) =>
        Some(response(ChunkMessageSpec(None, None), chunkUsage = Some(chunkUsage)))
      case _ => None
    }
  }

  // flags a text containing one of the words, as the check named by the word
  private class WordGuardrail(
    override val name: String,
    words: String*
  ) extends InputGuardrail
      with OutputGuardrail {
    val checked = ListBuffer[String]()

    private def verdict(
      stage: GuardrailStage,
      text: String
    ) = Future {
      synchronized(checked += text)
      GuardrailVerdict(
        name,
        stage,
        words.map(word => GuardrailCheckResult(word, text.contains(word)))
      )
    }

    override def checkInput(messages: Seq[BaseMessage]): Future[GuardrailVerdict] =
      verdict(GuardrailStage.Input, messages.flatMap(BaseMessage.getTextContent).mkString(" "))

    override def checkOutput(
      messages: Seq[BaseMessage],
      reply: String
    ): Future[GuardrailVerdict] =
      verdict(GuardrailStage.Output, reply)
  }

  private def run[T](source: Source[T, NotUsed]): Seq[T] =
    source.runWith(Sink.seq).futureValue

  // the elements before a failure, then the failure
  private def runToFailure[T](source: Source[T, NotUsed]): (Seq[T], Throwable) = {
    val all = source
      .map(Right(_): Either[Throwable, T])
      .recover { case e => Left(e) }
      .runWith(Sink.seq)
      .futureValue
    (all.collect { case Right(chunk) => chunk }, all.collectFirst { case Left(e) => e }.orNull)
  }

  private val inputBlockMessage =
    "I can't help with this request. It was flagged by the content policy (hack)."

  private val outputBlockMessage =
    "I can't share this answer. It was flagged by the content policy (secret)."

  private val clean = Seq(
    ChatChunk.Start("r1", "main-model"),
    ChatChunk.Thinking("Let me think."),
    ChatChunk.Text("Hello "),
    ChatChunk.Text("world, "),
    ChatChunk.Text("this is fine."),
    ChatChunk.Finish(FinishReason.stop, Some("stop")),
    ChatChunk.Usage(usage)
  )

  private val leaking = Seq(
    ChatChunk.Start("r1", "main-model"),
    ChatChunk.Text("Sure. "),
    ChatChunk.Text("The secret is 42."),
    ChatChunk.Finish(FinishReason.stop, Some("stop")),
    ChatChunk.Usage(usage)
  )

  private val adapters = OpenAIServiceAdapters

  "The streamed guardrails adapter" should {

    "fail a stream whose input is blocked, without starting it" in {
      val chat = new ScriptedStreams(clean)
      val guarded =
        adapters.guardrailsWithStreaming(input = Seq(new WordGuardrail("words", "hack")))(chat)

      val (chunks, error) =
        runToFailure(
          guarded.createChatCompletionStreamedTyped(Seq(UserMessage("hack it")), settings)
        )

      chunks shouldBe Nil
      error shouldBe an[OpenAIScalaGuardrailException]
      chat.calls shouldBe empty
    }

    "answer a blocked input with the block message, on both stream shapes" in {
      val chat = new ScriptedStreams()
      val guarded = adapters.guardrailsWithStreaming(
        input = Seq(new WordGuardrail("words", "hack")),
        onViolation = GuardrailAction.Respond()
      )(chat)

      val typed =
        run(guarded.createChatCompletionStreamedTyped(Seq(UserMessage("hack it")), settings))
      typed.head shouldBe a[ChatChunk.Start]
      typed.head.asInstanceOf[ChatChunk.Start].model shouldBe "main-model"
      typed.tail shouldBe Seq(
        ChatChunk.Text(inputBlockMessage),
        ChatChunk.Other(
          "guardrail_block",
          GuardrailsStreamedAdapter.verdictsJson(
            Seq(
              GuardrailVerdict(
                "words",
                GuardrailStage.Input,
                Seq(GuardrailCheckResult("hack", flagged = true))
              )
            )
          )
        ),
        ChatChunk.Finish(FinishReason.content_filter, Some("content_filter"))
      )

      val openAI =
        run(guarded.createChatCompletionStreamed(Seq(UserMessage("hack it")), settings))
      openAI.map(_.choices.head.delta.content) shouldBe Seq(Some(inputBlockMessage), None)
      openAI.map(_.choices.head.finish_reason) shouldBe Seq(None, Some("content_filter"))
      openAI.head.choices.head.delta.role shouldBe Some(ChatRole.Assistant)
      chat.calls shouldBe empty
    }

    "pass a clean stream through unchanged, the output checked once when it finished" in {
      val chat = new ScriptedStreams(clean)
      val guard = new WordGuardrail("words", "hack", "secret")
      val guarded =
        adapters.guardrailsWithStreaming(input = Seq(guard), output = Seq(guard))(chat)

      // Thinking comes only from the typed stream, not from the OpenAI-shaped one
      run(
        guarded.createChatCompletionStreamedTyped(Seq(UserMessage("Hi")), settings)
      ) shouldBe clean
      guard.checked shouldBe Seq("Hi", "Hello world, this is fine.")
    }

    "hold the reply back until the stream finishes, and answer a flagged one with the block message" in {
      val chat = new ScriptedStreams(leaking)
      val guarded = adapters.guardrailsWithStreaming(
        output = Seq(new WordGuardrail("words", "secret")),
        onViolation = GuardrailAction.Respond()
      )(chat)

      run(
        guarded.createChatCompletionStreamedTyped(Seq(UserMessage("Tell me")), settings)
      ) shouldBe Seq(
        ChatChunk.Start("r1", "main-model"),
        ChatChunk.Text(outputBlockMessage),
        ChatChunk.Other(
          "guardrail_block",
          GuardrailsStreamedAdapter.verdictsJson(
            Seq(
              GuardrailVerdict(
                "words",
                GuardrailStage.Output,
                Seq(GuardrailCheckResult("secret", flagged = true))
              )
            )
          )
        ),
        ChatChunk.Finish(FinishReason.content_filter, Some("content_filter")),
        ChatChunk.Usage(usage)
      )
    }

    "ask again for a flagged reply, summing the usage" in {
      val chat = new ScriptedStreams(leaking, clean)
      val guarded = adapters.guardrailsWithStreaming(
        output = Seq(new WordGuardrail("words", "secret")),
        outputReprompts = 1
      )(chat)

      val chunks =
        run(guarded.createChatCompletionStreamedTyped(Seq(UserMessage("Tell me")), settings))

      chunks.init shouldBe clean.init
      chunks.last shouldBe ChatChunk.Usage(UsageInfo.sum(usage, usage))
      chat.calls(1) shouldBe Seq(
        UserMessage("Tell me"),
        AssistantMessage("Sure. The secret is 42."),
        UserMessage(
          "Your previous reply was blocked by a content check (secret). Answer my previous message again, without that problem."
        )
      )
    }

    "report the usage of every attempt even when the released one reports none" in {
      val noUsage = clean.filterNot(_.isInstanceOf[ChatChunk.Usage])

      def guarded(chat: ScriptedStreams) = adapters.guardrailsWithStreaming(
        output = Seq(new WordGuardrail("words", "secret")),
        outputReprompts = 1
      )(chat)

      // the first attempt's usage, before the terminator
      run(
        guarded(new ScriptedStreams(leaking, noUsage :+ ChatChunk.Done))
          .createChatCompletionStreamedTyped(Seq(UserMessage("Tell me")), settings)
      ) shouldBe noUsage :+ ChatChunk.Usage(usage) :+ ChatChunk.Done

      run(
        guarded(new ScriptedStreams(leaking, noUsage))
          .createChatCompletionStreamed(Seq(UserMessage("Tell me")), settings)
      ).last.usage shouldBe Some(usage)
    }

    "not ask again when the call resumes a paused run - its approval decisions are one-shot" in {
      val chat = new ScriptedStreams(leaking, clean)
      val guarded = adapters.guardrailsWithStreaming(
        output = Seq(new WordGuardrail("words", "secret")),
        onViolation = GuardrailAction.respond("Blocked."),
        outputReprompts = 2
      )(chat)
      val request =
        ChatChunk.ToolApprovalRequest("r1", "ask", "{}", None, "resp_1", Json.obj())

      val chunks = run(
        guarded.createChatToolCompletionStreamed(
          Seq(UserMessage("Tell me")),
          Nil,
          None,
          settings.setToolApprovalDecisions(Seq(request.approve))
        )
      )

      chunks.collect { case ChatChunk.Text(text) => text } shouldBe Seq("Blocked.")
      chat.calls should have size 1
    }

    "answer only the flagged reply of several choices in the OpenAI-shaped stream" in {
      def chunk(
        index: Int,
        content: Option[String],
        finish: Option[String] = None
      ) =
        ChatCompletionChunkResponse(
          "oa-2",
          new java.util.Date(0L),
          "main-model",
          None,
          Seq(ChatCompletionChoiceChunkInfo(ChunkMessageSpec(None, content), index, finish)),
          None
        )

      val twoChoices: Seq[ChatCompletionChunkResponse] = Seq(
        chunk(0, Some("A clean ")),
        chunk(1, Some("The secret ")),
        chunk(0, Some("answer.")),
        chunk(1, Some("is 42.")),
        chunk(0, None, Some("stop")),
        chunk(1, None, Some("stop")),
        chunk(0, None).copy(choices = Nil, usage = Some(usage))
      )
      val streams: OpenAIChatCompletionService with OpenAIChatCompletionStreamedServiceExtra =
        new OpenAIChatCompletionService with OpenAIChatCompletionStreamedServiceExtra {
          override def createChatCompletion(
            messages: Seq[BaseMessage],
            settings: CreateChatCompletionSettings
          ): Future[ChatCompletionResponse] = Future.failed(new UnsupportedOperationException)

          override def createChatCompletionStreamed(
            messages: Seq[BaseMessage],
            settings: CreateChatCompletionSettings
          ): Source[ChatCompletionChunkResponse, NotUsed] = Source(twoChoices.toList)

          override def close(): Unit = ()
        }
      val guarded = adapters.guardrailsWithStreaming(
        output = Seq(new WordGuardrail("words", "secret")),
        onViolation = GuardrailAction.respond("Blocked.")
      )(streams)

      val chunks = run(
        guarded.createChatCompletionStreamed(
          Seq(UserMessage("Tell me")),
          settings.copy(n = Some(2))
        )
      )

      def choices(index: Int) = chunks.flatMap(_.choices.filter(_.index == index))
      choices(0).flatMap(_.delta.content).mkString shouldBe "A clean answer."
      choices(0).flatMap(_.finish_reason) shouldBe Seq("stop")
      choices(1).flatMap(_.delta.content).mkString shouldBe "Blocked."
      choices(1).flatMap(_.finish_reason) shouldBe Seq("content_filter")
      chunks.map(_.id).distinct shouldBe Seq("oa-2")
      // the usage still last
      chunks.last.usage shouldBe Some(usage)
      chunks.last.choices shouldBe Nil
    }

    "fail a flagged reply's stream without releasing any of it" in {
      val guarded = adapters.guardrailsWithStreaming(
        output = Seq(new WordGuardrail("words", "secret"))
      )(new ScriptedStreams(leaking))

      val (released, error) = runToFailure(
        guarded.createChatCompletionStreamedTyped(Seq(UserMessage("Tell me")), settings)
      )

      released shouldBe Nil
      error shouldBe an[OpenAIScalaGuardrailException]
    }

    "check only what follows a restart" in {
      val restarted = Seq(
        ChatChunk.Start("a", "main-model"),
        ChatChunk.Text("a secret draft"),
        ChatChunk.Retry(2),
        ChatChunk.Start("b", "main-model"),
        ChatChunk.Text("a fine answer"),
        ChatChunk.Finish(FinishReason.stop, Some("stop"))
      )
      val guard = new WordGuardrail("words", "secret")
      val guarded =
        adapters.guardrailsWithStreaming(output = Seq(guard))(new ScriptedStreams(restarted))

      run(guarded.createChatCompletionStreamedTyped(Seq(UserMessage("Hi")), settings)) shouldBe
        restarted.drop(2)
      guard.checked shouldBe Seq("a fine answer")
    }

    "guard the OpenAI-shaped stream's reply" in {
      val guarded = adapters.guardrailsWithStreaming(
        output = Seq(new WordGuardrail("words", "secret")),
        onViolation = GuardrailAction.Respond()
      )(new ScriptedStreams(leaking))

      val chunks =
        run(guarded.createChatCompletionStreamed(Seq(UserMessage("Tell me")), settings))

      chunks.map(_.id).distinct shouldBe Seq("oa-1")
      chunks.flatMap(_.choices.flatMap(_.delta.content)) shouldBe Seq(outputBlockMessage)
      chunks.last.choices.head.finish_reason shouldBe Some("content_filter")
      chunks.last.usage shouldBe Some(usage)
    }

    "guard the calls that are not streamed as well" in {
      val guarded = adapters.guardrailsWithStreaming(
        input = Seq(new WordGuardrail("words", "hack"))
      )(new ScriptedStreams(clean))

      guarded
        .createChatCompletion(Seq(UserMessage("hack it")), settings)
        .failed
        .futureValue shouldBe an[OpenAIScalaGuardrailException]
    }
  }
}
