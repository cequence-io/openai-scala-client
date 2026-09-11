package io.cequence.openaiscala.gemini.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.gemini.domain.ChatRole.Model
import io.cequence.openaiscala.gemini.domain.response.{
  Candidate,
  FinishReason => GeminiFinishReason,
  GenerateContentResponse,
  GroundingChunk,
  GroundingMetadata,
  UsageMetadata,
  Web
}
import io.cequence.openaiscala.gemini.domain.{Content, Part}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

/**
 * [[OpenAIGeminiChatCompletionService.toChatChunks]]: streamed Gemini responses -> typed
 * chunks.
 */
class GeminiStreamedToChatChunksSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("gemini-streamed-to-chat-chunks")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private val usage = UsageMetadata(
    promptTokenCount = 10,
    candidatesTokenCount = Some(5),
    totalTokenCount = 18,
    thoughtsTokenCount = Some(3)
  )

  private def response(
    parts: Seq[Part],
    finishReason: Option[GeminiFinishReason] = None,
    grounding: Option[GroundingMetadata] = None,
    candidateIndex: Int = 0
  ): GenerateContentResponse =
    GenerateContentResponse(
      candidates = Seq(
        Candidate(
          content = Content(parts, Some(Model)),
          finishReason = finishReason,
          groundingMetadata = grounding,
          index = Some(candidateIndex)
        )
      ),
      usageMetadata = usage,
      modelVersion = "gemini-test"
    )

  private def run(responses: GenerateContentResponse*): Seq[ChatChunk] =
    Source(responses.toList)
      .via(OpenAIGeminiChatCompletionService.toChatChunks)
      .runWith(Sink.seq)
      .futureValue

  "toChatChunks" should {

    "map thought parts, text, a function call and STOP to typed chunks" in {
      val out = run(
        response(Seq(Part.Text("thinking...", thought = Some(true)))),
        response(Seq(Part.Text("Let me check."))),
        response(
          Seq(
            Part.FunctionCall(
              Some("call_1"),
              "get_weather",
              Map("location" -> "Oslo"),
              thoughtSignature = Some("sig")
            )
          ),
          finishReason = Some(GeminiFinishReason.STOP)
        )
      )

      out shouldBe Seq(
        Start("gemini", "gemini-test"),
        Thinking("thinking..."),
        Text("Let me check."),
        ToolCallStart(0, "call_1", "get_weather", serverSide = false),
        ToolCall(0, "call_1", "get_weather", "{\"location\":\"Oslo\"}", serverSide = false),
        ThinkingSignature("sig", Some("call_1")),
        Finish(FinishReason.tool_calls, Some("STOP")),
        Usage(OpenAIGeminiChatCompletionService.toOpenAIUsage(usage))
      )
    }

    "pair executable code with its execution result as a server-side tool call" in {
      val out = run(
        response(Seq(Part.ExecutableCode("PYTHON", "print(1)"))),
        response(
          Seq(Part.CodeExecutionResult("OUTCOME_OK", Some("1\n")), Part.Text("Done.")),
          finishReason = Some(GeminiFinishReason.STOP)
        )
      )

      val callId = out.collectFirst { case ToolCallStart(0, id, "code_execution", true) =>
        id
      }.get

      out(2) shouldBe ToolCall(
        0,
        callId,
        "code_execution",
        "{\"language\":\"PYTHON\",\"code\":\"print(1)\"}",
        serverSide = true
      )
      out(3) shouldBe CodeExecution(callId, Some("python"), "print(1)")
      out(4) shouldBe ToolResult(
        callId,
        "code_execution",
        Json.obj("outcome" -> "OUTCOME_OK", "output" -> "1\n"),
        Some("1\n"),
        isError = false
      )
      out(5) shouldBe CodeExecutionResult(
        callId,
        Some("1\n"),
        isError = false,
        Json.obj("outcome" -> "OUTCOME_OK", "output" -> "1\n")
      )
      out(6) shouldBe Text("Done.")
      out(7) shouldBe Finish(FinishReason.stop, Some("STOP"))
    }

    "map grounding chunks to citations, MAX_TOKENS to length, and unknown parts / candidates to Other" in {
      val grounding = GroundingMetadata(
        groundingChunks = Seq(GroundingChunk(Web("https://ex.com", "Example"))),
        webSearchQueries = Seq("example query")
      )

      val out = run(
        response(
          Seq(Part.Text("Grounded."), Part.Unknown(Json.obj("videoMetadata" -> Json.obj()))),
          finishReason = Some(GeminiFinishReason.MAX_TOKENS),
          grounding = Some(grounding)
        ),
        response(Seq(Part.Text("second candidate")), candidateIndex = 1)
      )

      out shouldBe Seq(
        Start("gemini", "gemini-test"),
        Text("Grounded."),
        Other("unknown", Json.obj("videoMetadata" -> Json.obj())),
        WebSearch("", Seq("example query")),
        WebSearchResult(
          "",
          Seq(WebSearchResultItem(Some("Example"), "https://ex.com")),
          Json.arr(Json.obj("uri" -> "https://ex.com", "title" -> "Example"))
        ),
        Citation(
          None,
          Some("https://ex.com"),
          Some("Example"),
          Json.obj("uri" -> "https://ex.com", "title" -> "Example")
        ),
        Finish(FinishReason.length, Some("MAX_TOKENS")),
        Usage(OpenAIGeminiChatCompletionService.toOpenAIUsage(usage)),
        Other(
          "candidate[1]",
          Json.toJson(
            response(Seq(Part.Text("second candidate")), candidateIndex = 1).candidates.head
          )(io.cequence.openaiscala.gemini.JsonFormats.candidateFormat)
        )
      )
    }
  }
}
