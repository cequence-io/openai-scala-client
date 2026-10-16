package io.cequence.openaiscala.gemini.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  FunctionCallSpec,
  JsonSchema,
  NonOpenAIModelId,
  ToolMessage,
  UserMessage
}
import io.cequence.openaiscala.gemini.domain.ChatRole.Model
import io.cequence.openaiscala.gemini.domain.response.{
  Candidate,
  GenerateContentResponse,
  UsageMetadata,
  FinishReason => GeminiFinishReason
}
import io.cequence.openaiscala.gemini.domain.{Content, Part}
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
 * Gemini 3+ refuses a function call sent back without the thought signature it came with (400
 * "Function call is missing a thought_signature in functionCall parts", live 2026-10-13), and
 * an OpenAI-shaped assistant message has no place for it: the adapter remembers the signatures
 * of the calls it hands out - sync and typed stream - puts them back when the calls return,
 * and sends Google's placeholder for a call it does not know on the models that require one.
 */
class GeminiThoughtSignaturesSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("GeminiThoughtSignaturesSpec")
  private implicit val materializer: Materializer = Materializer(system)
  private implicit val ec: ExecutionContext = system.dispatcher

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override protected def afterAll(): Unit = system.terminate()

  private val weather = FunctionTool(
    name = "get_weather",
    description = Some("The current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  private val ask = UserMessage("What's the weather in Oslo and in Paris?")

  // Gemini signs the first call of a turn only
  private val calls = Seq(
    Part.FunctionCall(
      Some("call_134961"),
      "get_weather",
      Map("city" -> "Oslo"),
      Some("c2lnLW9zbG8=")
    ),
    Part.FunctionCall(Some("call_134962"), "get_weather", Map("city" -> "Paris"))
  )

  private def response(parts: Seq[Part]): GenerateContentResponse =
    GenerateContentResponse(
      candidates = Seq(
        Candidate(
          content = Content(parts, Some(Model)),
          finishReason = Some(GeminiFinishReason.STOP),
          index = Some(0)
        )
      ),
      usageMetadata = UsageMetadata(promptTokenCount = 10, totalTokenCount = 12),
      modelVersion = "gemini-4-argon"
    )

  // a Gemini service answering every request with `first`, then a text, recording the contents
  private class Recording(first: GenerateContentResponse) {
    val requests = mutable.ArrayBuffer.empty[Seq[Content]]
    val underlying: GeminiService = mock(classOf[GeminiService])

    when(underlying.generateContent(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        requests.synchronized {
          requests += invocation.getArgument[Seq[Content]](0)
          Future.successful(
            if (requests.size == 1) first else response(Seq(Part.Text("7 degrees in both.")))
          )
        }
    }
    when(underlying.generateContentStreamed(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        requests.synchronized(requests += invocation.getArgument[Seq[Content]](0))
        Source.single(first)
    }

    val adapter = new OpenAIGeminiChatCompletionService(underlying)
  }

  private def secondTurn(
    message: AssistantToolMessage
  ): Seq[io.cequence.openaiscala.domain.BaseMessage] =
    Seq(ask, message) ++ message.tool_calls.collect { case (id, call: FunctionCallSpec) =>
      ToolMessage(Some("""{"temperature_c": 7}"""), id, call.name)
    }

  // the function calls of the model's turn in a recorded request
  private def sentCalls(contents: Seq[Content]): Seq[Part.FunctionCall] =
    contents.filter(_.role.contains(Model)).flatMap(_.parts).collect {
      case call: Part.FunctionCall => call
    }

  "The Gemini adapter" should {

    "send a call's own signature back in the next turn - the sync tool path" in {
      val recording = new Recording(response(calls))
      val settings = CreateChatCompletionSettings(NonOpenAIModelId.gemini_4_argon)

      val first = recording.adapter
        .createChatToolCompletion(Seq(ask), Seq(weather), settings = settings)
        .futureValue
      recording.adapter
        .createChatToolCompletion(
          secondTurn(first.choices.head.message),
          Seq(weather),
          settings = settings
        )
        .futureValue

      // the signed call's own signature, the placeholder on the unsigned parallel one
      sentCalls(recording.requests(1)).map(c => c.id -> c.thoughtSignature) shouldBe Seq(
        Some("call_134961") -> Some("c2lnLW9zbG8="),
        Some("call_134962") -> Some(
          OpenAIGeminiChatCompletionService.SkipThoughtSignatureValidator
        )
      )
    }

    "send a call's own signature back in the next turn - the typed stream" in {
      val recording = new Recording(response(calls))
      val settings = CreateChatCompletionSettings(NonOpenAIModelId.gemini_3_8_flash)

      val chunks = recording.adapter
        .createChatToolCompletionStreamed(Seq(ask), Seq(weather), None, settings)
        .runWith(Sink.seq)
        .futureValue
      val toolCalls = chunks.collect {
        case io.cequence.openaiscala.domain.response.ChatChunk
              .ToolCall(_, id, name, args, _) =>
          (id, FunctionCallSpec(name, args): io.cequence.openaiscala.domain.ToolCallSpec)
      }

      recording.adapter
        .createChatToolCompletion(
          secondTurn(AssistantToolMessage(None, None, toolCalls)),
          Seq(weather),
          settings = settings
        )
        .futureValue

      sentCalls(recording.requests(1)).flatMap(_.thoughtSignature) shouldBe Seq(
        "c2lnLW9zbG8=",
        OpenAIGeminiChatCompletionService.SkipThoughtSignatureValidator
      )
    }

    "send the placeholder for a call it never handed out, on the models that require one only" in {
      val handMade = AssistantToolMessage(
        None,
        None,
        Seq("call_1" -> FunctionCallSpec("get_weather", """{"city":"Oslo"}"""))
      )

      for (
        (model, expected) <- Seq(
          NonOpenAIModelId.gemini_4_argon -> Some(
            OpenAIGeminiChatCompletionService.SkipThoughtSignatureValidator
          ),
          NonOpenAIModelId.gemini_flash_latest -> Some(
            OpenAIGeminiChatCompletionService.SkipThoughtSignatureValidator
          ),
          NonOpenAIModelId.gemini_2_5_flash -> None
        )
      ) withClue(model) {
        val recording = new Recording(response(Seq(Part.Text("ok"))))
        recording.adapter
          .createChatToolCompletion(
            secondTurn(handMade),
            Seq(weather),
            settings = CreateChatCompletionSettings(model)
          )
          .futureValue
        sentCalls(recording.requests.head).map(_.thoughtSignature) shouldBe Seq(expected)
      }
    }
  }

  "FunctionCallSignatures" should {

    "key a signature by the call's id, name and arguments, and drop the oldest past its capacity" in {
      val signatures =
        new OpenAIGeminiChatCompletionService.FunctionCallSignatures(maxEntries = 2)
      signatures.remember("call_1", "f", """{"a":1}""", "s1")
      signatures.get("call_1", "f", """{"a":2}""") shouldBe None
      signatures.get("call_1", "g", """{"a":1}""") shouldBe None

      signatures.remember("call_2", "f", "{}", "s2")
      signatures.remember("call_3", "f", "{}", "s3")
      signatures.get("call_1", "f", """{"a":1}""") shouldBe None
      signatures.get("call_3", "f", "{}") shouldBe Some("s3")
    }
  }
}
