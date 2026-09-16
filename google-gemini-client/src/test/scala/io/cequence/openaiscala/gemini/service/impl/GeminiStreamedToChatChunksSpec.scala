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

    "label an mcpServers call server-side, pair its functionResponse, and keep one Finish" in {
      // what Gemini 2.5 streams for a native MCP call (live-verified 2026-09-16): the call in
      // a chunk carrying its own STOP, the result, then the answer with the real STOP
      val out = Source(
        List(
          response(
            Seq(Part.FunctionCall(None, "exa_web_search_exa", Map("query" -> "jupiter"))),
            finishReason = Some(GeminiFinishReason.STOP)
          ),
          response(
            Seq(
              Part.FunctionResponse(
                None,
                "exa_web_search_exa",
                Map("result" -> "Title: Juice flyby\nURL: https://ex.com/j")
              )
            )
          ),
          response(
            Seq(Part.Text("Juice flew by.")),
            finishReason = Some(GeminiFinishReason.STOP)
          )
        )
      ).via(OpenAIGeminiChatCompletionService.chatChunksFlow(Seq("exa")))
        .runWith(Sink.seq)
        .futureValue

      val callId = out.collectFirst { case ToolCallStart(0, id, _, _) => id }.get

      out shouldBe Seq(
        Start("gemini", "gemini-test"),
        ToolCallStart(0, callId, "exa_web_search_exa", serverSide = true),
        ToolCall(
          0,
          callId,
          "exa_web_search_exa",
          "{\"query\":\"jupiter\"}",
          serverSide = true
        ),
        ToolResult(
          callId,
          "exa_web_search_exa",
          Json.obj("result" -> "Title: Juice flyby\nURL: https://ex.com/j"),
          Some("Title: Juice flyby\nURL: https://ex.com/j"),
          isError = false
        ),
        Text("Juice flew by."),
        // the STOP of the chunk that made the call was a round boundary - dropped
        Finish(FinishReason.stop, Some("STOP")),
        Usage(OpenAIGeminiChatCompletionService.toOpenAIUsage(usage))
      )
    }

    "summarize a call_tool_result_json functionResponse, incl. its isError" in {
      val callToolResult =
        """{"content":[{"type":"text","text":"line one"},{"type":"text","text":"line two"}],"isError":true}"""

      val out = Source(
        List(
          response(Seq(Part.FunctionCall(None, "github_search", Map.empty[String, Any]))),
          response(
            Seq(
              Part.FunctionResponse(
                None,
                "github_search",
                Map("call_tool_result_json" -> callToolResult)
              )
            ),
            finishReason = Some(GeminiFinishReason.STOP)
          )
        )
      ).via(OpenAIGeminiChatCompletionService.chatChunksFlow(Seq("github")))
        .runWith(Sink.seq)
        .futureValue

      out.collect { case tr: ToolResult => (tr.toolName, tr.text, tr.isError) } shouldBe Seq(
        ("github_search", Some("line one\nline two"), true)
      )
      out.collect { case f: Finish => f } shouldBe Seq(Finish(FinishReason.stop, Some("STOP")))
    }

    "report an mcpServers call the stream never answered as an error result, not an empty end" in {
      // the transient Gemini failure (live-observed 2026-09-16): the stream closes right after
      // the functionCall chunk
      val out = Source(
        List(
          response(
            Seq(
              Part.FunctionCall(None, "github_search_repositories", Map("query" -> "org:x"))
            ),
            finishReason = Some(GeminiFinishReason.STOP)
          )
        )
      ).via(OpenAIGeminiChatCompletionService.chatChunksFlow(Seq("github")))
        .runWith(Sink.seq)
        .futureValue

      val callId = out.collectFirst { case ToolCallStart(0, id, _, _) => id }.get

      out.map {
        case tr: ToolResult => tr.copy(content = Json.obj())
        case other          => other
      } shouldBe Seq(
        Start("gemini", "gemini-test"),
        ToolCallStart(0, callId, "github_search_repositories", serverSide = true),
        ToolCall(
          0,
          callId,
          "github_search_repositories",
          "{\"query\":\"org:x\"}",
          serverSide = true
        ),
        ToolResult(
          callId,
          "github_search_repositories",
          Json.obj(),
          Some(
            OpenAIGeminiChatCompletionService.danglingMcpCallMessage(
              "github_search_repositories"
            )
          ),
          isError = true
        ),
        // not tool_calls: there is nothing for the caller to run
        Finish(FinishReason.stop, Some("STOP")),
        Usage(OpenAIGeminiChatCompletionService.toOpenAIUsage(usage))
      )
      out.collect { case tr: ToolResult => tr.text.get } should contain(
        OpenAIGeminiChatCompletionService.danglingMcpCallMessage("github_search_repositories")
      )
    }

    "keep a client function call client-side next to MCP servers" in {
      val out = Source(
        List(
          response(
            Seq(Part.FunctionCall(Some("c1"), "get_weather", Map("location" -> "Oslo"))),
            finishReason = Some(GeminiFinishReason.STOP)
          )
        )
      ).via(OpenAIGeminiChatCompletionService.chatChunksFlow(Seq("exa")))
        .runWith(Sink.seq)
        .futureValue

      out(1) shouldBe ToolCallStart(0, "c1", "get_weather", serverSide = false)
      out(3) shouldBe Finish(FinishReason.tool_calls, Some("STOP"))
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

  "danglingMcpCalls / mcpResultSummary" should {

    "find MCP calls without a matching functionResponse, pairing by name in order" in {
      val r = response(
        Seq(
          Part.FunctionCall(None, "exa_search", Map.empty[String, Any]),
          Part.FunctionResponse(None, "exa_search", Map("result" -> "ok")),
          Part.FunctionCall(None, "exa_search", Map("q" -> 2)),
          Part.FunctionCall(None, "get_weather", Map.empty[String, Any]) // client tool
        )
      )

      OpenAIGeminiChatCompletionService
        .danglingMcpCalls(r, Seq("exa"))
        .map(_.args) shouldBe Seq(Map("q" -> 2))
      OpenAIGeminiChatCompletionService.danglingMcpCalls(r, Nil) shouldBe empty
    }

    "read `result` and `call_tool_result_json` shapes" in {
      OpenAIGeminiChatCompletionService.mcpResultSummary(Map("result" -> "text")) shouldBe
        (Some("text"), false)
      OpenAIGeminiChatCompletionService.mcpResultSummary(
        Map("call_tool_result_json" -> """{"content":[{"type":"text","text":"a"}]}""")
      ) shouldBe (Some("a"), false)
      OpenAIGeminiChatCompletionService.mcpResultSummary(Map("other" -> 1)) shouldBe
        (None, false)
    }
  }
}
