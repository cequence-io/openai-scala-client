package io.cequence.openaiscala.gemini.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  AssistantToolMessage,
  BaseMessage,
  FunctionCallSpec,
  JsonSchema,
  NonOpenAIModelId,
  ReasoningBlock,
  ReasoningMessage,
  SystemMessage,
  ToolMessage,
  UserMessage
}
import io.cequence.openaiscala.gemini.domain.ChatRole.{Model, User}
import io.cequence.openaiscala.gemini.domain.response.{
  Candidate,
  GenerateContentResponse,
  UsageMetadata,
  FinishReason => GeminiFinishReason
}
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.settings.{GenerateContentSettings, ThinkingConfig}
import io.cequence.openaiscala.gemini.domain.{Content, Part, ThinkingLevel}
import io.cequence.openaiscala.gemini.service.GeminiService
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
 * Carried reasoning on Gemini: a reply's thought summaries (asked for with
 * `setGeminiIncludeThoughts`) come back as its reasoning and go back as thought parts - only
 * to the service that returned them; any other `ReasoningMessage` (e.g. after a router failed
 * over from a GPT-6 model) is left out, the history sent exactly as the one without it.
 */
class GeminiReasoningMessageSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("gemini-reasoning-message")
  private implicit val materializer: Materializer = Materializer(system)

  override protected def afterAll(): Unit = {
    system.terminate()
    ()
  }

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  private val reasoning =
    ReasoningMessage(
      Seq(ReasoningBlock(id = Some("rs_1"), encrypted = Some("enc")))
    )

  private val weather = FunctionTool(
    name = "get_weather",
    description = Some("The current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  private val settings = CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash)

  private val answer = GenerateContentResponse(
    candidates = Seq(
      Candidate(
        content = Content(Seq(Part.Text("ok")), Some(Model)),
        finishReason = Some(GeminiFinishReason.STOP),
        index = Some(0)
      )
    ),
    usageMetadata = UsageMetadata(promptTokenCount = 10, totalTokenCount = 12),
    modelVersion = "gemini-2.5-flash"
  )

  // a Gemini service answering with a text and recording what it was asked
  private class Recording(answer: GenerateContentResponse = answer) {
    val requests = mutable.ArrayBuffer.empty[(Seq[Content], GenerateContentSettings)]
    val underlying: GeminiService = mock(classOf[GeminiService])

    when(underlying.generateContent(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        requests.synchronized {
          requests += ((
            invocation.getArgument[Seq[Content]](0),
            invocation.getArgument[GenerateContentSettings](1)
          ))
        }
        Future.successful(answer)
    }

    when(underlying.generateContentStreamed(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        requests.synchronized {
          requests += ((
            invocation.getArgument[Seq[Content]](0),
            invocation.getArgument[GenerateContentSettings](1)
          ))
        }
        Source.single(answer)
    }

    val adapter = new OpenAIGeminiChatCompletionService(underlying)

    def lastThinkingConfig: Option[ThinkingConfig] =
      requests.synchronized(requests.last)._2.generationConfig.flatMap(_.thinkingConfig)
  }

  "The Gemini adapter" should {

    "send a tool loop's history like the one without the ReasoningMessage" in {
      val history: Seq[BaseMessage] = Seq(
        SystemMessage("Be brief."),
        UserMessage("What's the weather in Oslo?"),
        AssistantToolMessage(
          None,
          None,
          Seq("call_1" -> FunctionCallSpec("get_weather", """{"city":"Oslo"}"""))
        ),
        ToolMessage(Some("""{"temperature_c": 7}"""), "call_1", "get_weather")
      )

      def sent(messages: Seq[BaseMessage]): (Seq[Content], GenerateContentSettings) = {
        val recording = new Recording
        recording.adapter
          .createChatToolCompletion(messages, Seq(weather), settings = settings)
          .futureValue
        recording.requests.head
      }

      val (expectedContents, expectedSettings) = sent(history)
      // the system message goes to the instruction, the rest alternates user / model / user
      expectedSettings.systemInstruction should not be empty
      expectedContents.map(_.role) shouldBe Seq(Some(User), Some(Model), Some(User))

      // in front of the reply, where the Responses adapter's history puts it, and at the end
      sent(history.patch(2, Seq(reasoning), 0)) shouldBe ((expectedContents, expectedSettings))
      sent(history :+ reasoning) shouldBe ((expectedContents, expectedSettings))
    }

    "send a conversation like the one without the ReasoningMessage" in {
      val conversation: Seq[BaseMessage] = Seq(
        UserMessage("Remember 7."),
        AssistantMessage("ok"),
        UserMessage("What was it?")
      )

      def sent(messages: Seq[BaseMessage]): Seq[Content] = {
        val recording = new Recording
        recording.adapter.createChatCompletion(messages, settings).futureValue
        recording.requests.head._1
      }

      val expected = sent(conversation)
      expected.map(_.role) shouldBe Seq(Some(User), Some(Model), Some(User))

      sent(conversation.patch(1, Seq(reasoning), 0)) shouldBe expected
      sent(Seq(UserMessage("hi"), reasoning)) shouldBe sent(Seq(UserMessage("hi")))
    }

    "read a reply's thoughts as its reasoning and send them back as thought parts" in {
      val thoughtful = answer.copy(candidates =
        Seq(
          Candidate(
            content = Content(
              Seq(
                Part.Text("Oslo is closer.", thought = Some(true)),
                Part.FunctionCall(
                  id = Some("call_1"),
                  name = "get_weather",
                  args = Map("city" -> "Oslo"),
                  thoughtSignature = Some("sig-1")
                )
              ),
              Some(Model)
            ),
            finishReason = Some(GeminiFinishReason.STOP),
            index = Some(0)
          )
        )
      )
      val recording = new Recording(thoughtful)
      val question = UserMessage("What's the weather where the trains meet?")

      val first = recording.adapter
        .createChatToolCompletion(Seq(question), Seq(weather), settings = settings)
        .futureValue
      first.reasoningMessage shouldBe Some(
        ReasoningMessage(Seq(ReasoningBlock(text = Some("Oslo is closer."))))
      )

      val toolResult =
        ToolMessage(Some("4C"), first.assistantToolMessage.tool_calls.head._1, "get_weather")
      recording.adapter
        .createChatToolCompletion(
          Seq(question) ++ first.historyMessages :+ toolResult,
          Seq(weather),
          settings = settings
        )
        .futureValue

      val modelTurn = recording.requests.last._1(1)
      modelTurn.role shouldBe Some(Model)
      modelTurn.parts.head shouldBe Part.Text("Oslo is closer.", thought = Some(true))
      modelTurn.parts.collect { case call: Part.FunctionCall =>
        call.thoughtSignature
      } shouldBe Seq(
        Some("sig-1")
      )

      // a thought this service never returned stays out
      recording.adapter
        .createChatToolCompletion(
          Seq(
            question,
            ReasoningMessage(Seq(ReasoningBlock(text = Some("Made up.")))),
            first.assistantToolMessage,
            toolResult
          ),
          Seq(weather),
          settings = settings
        )
        .futureValue
      recording.requests.last._1(1).parts.collect {
        case text: Part.Text if text.thought.contains(true) => text
      } shouldBe Nil
    }

    "ask a sync call for thought summaries when told to, with or without a reasoning effort" in {
      val recording = new Recording
      val flash = CreateChatCompletionSettings(NonOpenAIModelId.gemini_3_8_flash)
      val question = Seq(UserMessage("Why is the sky blue?"))

      // not told to: no thinking config without an effort, none of the summaries with one
      recording.adapter.createChatCompletion(question, flash).futureValue
      recording.lastThinkingConfig shouldBe None
      recording.adapter
        .createChatCompletion(
          question,
          flash.copy(reasoning_effort = Some(ReasoningEffort.high))
        )
        .futureValue
      recording.lastThinkingConfig.flatMap(_.includeThoughts) shouldBe Some(false)

      // told to: Gemini 3.x / 2.5 think by default, so the flag alone is a thinking config
      recording.adapter
        .createChatCompletion(question, flash.setGeminiIncludeThoughts())
        .futureValue
      recording.lastThinkingConfig shouldBe Some(ThinkingConfig(includeThoughts = Some(true)))
      recording.adapter
        .createChatToolCompletion(
          question,
          Seq(weather),
          settings = settings.setGeminiIncludeThoughts()
        )
        .futureValue
      recording.lastThinkingConfig shouldBe Some(ThinkingConfig(includeThoughts = Some(true)))

      // with an effort the flag joins its config
      recording.adapter
        .createChatCompletion(
          question,
          flash.copy(reasoning_effort = Some(ReasoningEffort.high)).setGeminiIncludeThoughts()
        )
        .futureValue
      recording.lastThinkingConfig shouldBe Some(
        ThinkingConfig(includeThoughts = Some(true), thinkingLevel = Some(ThinkingLevel.HIGH))
      )

      // a model that takes no thinking configuration is asked nothing
      recording.adapter
        .createChatCompletion(
          question,
          CreateChatCompletionSettings("gemini-2.0-flash").setGeminiIncludeThoughts()
        )
        .futureValue
      recording.lastThinkingConfig shouldBe None
    }

    "ask the typed stream for thought summaries as before - and when told to, without an effort" in {
      val recording = new Recording
      val flash = CreateChatCompletionSettings(NonOpenAIModelId.gemini_3_8_flash)
      val question = Seq(UserMessage("Why is the sky blue?"))

      def stream(settings: CreateChatCompletionSettings) =
        recording.adapter
          .createChatToolCompletionStreamed(question, Nil, None, settings)
          .runWith(Sink.ignore)
          .futureValue

      // the default: summaries within the config an effort derives, none without one
      stream(flash.copy(reasoning_effort = Some(ReasoningEffort.low)))
      recording.lastThinkingConfig.flatMap(_.includeThoughts) shouldBe Some(true)
      stream(flash)
      recording.lastThinkingConfig shouldBe None

      // told to: the flag alone
      stream(flash.setGeminiIncludeThoughts())
      recording.lastThinkingConfig shouldBe Some(ThinkingConfig(includeThoughts = Some(true)))

      // told not to
      stream(
        flash
          .copy(reasoning_effort = Some(ReasoningEffort.low))
          .setGeminiIncludeThoughts(false)
      )
      recording.lastThinkingConfig.flatMap(_.includeThoughts) shouldBe Some(false)
    }
  }
}
