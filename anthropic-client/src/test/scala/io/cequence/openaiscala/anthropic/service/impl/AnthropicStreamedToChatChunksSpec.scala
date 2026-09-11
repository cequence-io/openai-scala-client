package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.anthropic.domain.ChatRole
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{
  McpToolResultBlock,
  RedactedThinkingBlock,
  ServerToolUseBlock,
  ToolUseBlock,
  WebSearchToolResultBlock
}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlocks
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.DeltaBlock._
import io.cequence.openaiscala.anthropic.domain.response.MessageStreamEvent._
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse,
  MessageDeltaUsage,
  MessageStreamEvent
}
import io.cequence.openaiscala.anthropic.domain.{
  McpToolResultStructured,
  MCPToolResultItem,
  ServerToolName,
  WebSearchToolResultContent
}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

/** [[toChatChunks]]: raw Anthropic [[MessageStreamEvent]]s -> typed [[ChatChunk]]s. */
class AnthropicStreamedToChatChunksSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("anthropic-streamed-to-chat-chunks")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def messageStart(inputTokens: Int = 10): MessageStart =
    MessageStart(
      CreateMessageResponse(
        id = "msg_1",
        role = ChatRole.Assistant,
        content = ContentBlocks(Nil),
        model = "claude-x",
        stop_reason = None,
        stop_sequence = None,
        usage = UsageInfo(inputTokens, 1, None, None)
      )
    )

  private def textDelta(
    index: Int,
    text: String
  ) = ContentBlockDeltaEvent(ContentBlockDelta("content_block_delta", index, DeltaText(text)))

  private def thinkingDelta(
    index: Int,
    text: String
  ) = ContentBlockDeltaEvent(
    ContentBlockDelta("content_block_delta", index, DeltaThinking(text))
  )

  private def signatureDelta(
    index: Int,
    sig: String
  ) = ContentBlockDeltaEvent(
    ContentBlockDelta("content_block_delta", index, DeltaSignature(sig))
  )

  private def inputJsonDelta(
    index: Int,
    json: String
  ) = ContentBlockDeltaEvent(
    ContentBlockDelta("content_block_delta", index, DeltaInputJson(json))
  )

  private def messageDelta(
    stop: String,
    outputTokens: Int
  ) = MessageDelta(Some(stop), None, Some(MessageDeltaUsage(outputTokens, None, None, None)))

  private def run(events: MessageStreamEvent*): Seq[ChatChunk] =
    Source(events.toList).via(toChatChunks).runWith(Sink.seq).futureValue

  "toChatChunks" should {

    "map thinking, signature and text deltas with a merged usage" in {
      val out = run(
        messageStart(inputTokens = 10),
        ContentBlockStart(0, "thinking", None),
        thinkingDelta(0, "let me"),
        thinkingDelta(0, " think"),
        signatureDelta(0, "sig"),
        ContentBlockStop(0),
        ContentBlockStart(1, "text", None),
        textDelta(1, "Hello"),
        ContentBlockStop(1),
        messageDelta("end_turn", 7),
        MessageStop
      )

      out shouldBe Seq(
        Start("msg_1", "claude-x"),
        Thinking("let me"),
        Thinking(" think"),
        ThinkingSignature("sig"),
        Text("Hello"),
        Finish(FinishReason.stop, Some("end_turn")),
        Usage(toOpenAI(UsageInfo(10, 7, None, None)))
      )
    }

    "assemble two tool_use blocks in order and finish with tool_calls" in {
      val out = run(
        messageStart(),
        ContentBlockStart(0, "text", None),
        textDelta(0, "Sure."),
        ContentBlockStop(0),
        ContentBlockStart(
          1,
          "tool_use",
          Some(ToolUseBlock("tool_1", "get_weather", Json.obj()))
        ),
        inputJsonDelta(1, "{\"loc"),
        inputJsonDelta(1, "ation\": \"Oslo\"}"),
        ContentBlockStop(1),
        ContentBlockStart(2, "tool_use", Some(ToolUseBlock("tool_2", "get_time", Json.obj()))),
        ContentBlockStop(2),
        messageDelta("tool_use", 20)
      )

      out shouldBe Seq(
        Start("msg_1", "claude-x"),
        Text("Sure."),
        ToolCallStart(0, "tool_1", "get_weather", serverSide = false),
        ToolCallDelta(0, "{\"loc"),
        ToolCallDelta(0, "ation\": \"Oslo\"}"),
        ToolCall(0, "tool_1", "get_weather", "{\"location\": \"Oslo\"}", serverSide = false),
        ToolCallStart(1, "tool_2", "get_time", serverSide = false),
        ToolCall(1, "tool_2", "get_time", "{}", serverSide = false),
        Finish(FinishReason.tool_calls, Some("tool_use")),
        Usage(toOpenAI(UsageInfo(10, 20, None, None)))
      )
    }

    "map server_tool_use + web_search_tool_result to a server-side ToolCall and a ToolResult" in {
      val results = WebSearchToolResultContent.Success(
        Seq(
          WebSearchToolResultContent.Item("enc", None, "Jupiter news", "https://ex.com/j")
        )
      )

      val out = run(
        messageStart(),
        ContentBlockStart(
          0,
          "server_tool_use",
          Some(ServerToolUseBlock("srv_1", ServerToolName.web_search, Json.obj()))
        ),
        inputJsonDelta(0, "{\"query\": \"jupiter\"}"),
        ContentBlockStop(0),
        ContentBlockStart(
          1,
          "web_search_tool_result",
          Some(WebSearchToolResultBlock(results, "srv_1"))
        ),
        ContentBlockStop(1),
        ContentBlockStart(2, "text", None),
        ContentBlockDeltaEvent(
          ContentBlockDelta(
            "content_block_delta",
            2,
            DeltaCitations(Json.obj("cited_text" -> "Jupiter", "url" -> "https://ex.com/j"))
          )
        ),
        textDelta(2, "Jupiter..."),
        ContentBlockStop(2),
        messageDelta("end_turn", 30)
      )

      out(1) shouldBe ToolCallStart(0, "srv_1", "web_search", serverSide = true)
      out(2) shouldBe ToolCallDelta(0, "{\"query\": \"jupiter\"}")
      out(3) shouldBe ToolCall(
        0,
        "srv_1",
        "web_search",
        "{\"query\": \"jupiter\"}",
        serverSide = true
      )
      // the semantic layer follows the tool layer with the same call id
      out(4) shouldBe WebSearch("srv_1", Seq("jupiter"))
      out(5) match {
        case ToolResult("srv_1", "web_search", content, Some(text), false) =>
          text shouldBe "Jupiter news - https://ex.com/j"
          (content \ "type").as[String] shouldBe "web_search_tool_result"
        case other => fail(s"Unexpected $other")
      }
      out(6) match {
        case WebSearchResult("srv_1", items, _) =>
          items shouldBe Seq(WebSearchResultItem(Some("Jupiter news"), "https://ex.com/j"))
        case other => fail(s"Unexpected $other")
      }
      out(7) match {
        case Citation(Some("Jupiter"), Some("https://ex.com/j"), None, _) => succeed
        case other => fail(s"Unexpected $other")
      }
      out(8) shouldBe Text("Jupiter...")
      out(9) shouldBe Finish(FinishReason.stop, Some("end_turn"))
    }

    "flag web search errors and MCP errors as ToolResult(isError = true)" in {
      val out = run(
        messageStart(),
        ContentBlockStart(
          0,
          "web_search_tool_result",
          Some(
            WebSearchToolResultBlock(
              WebSearchToolResultContent.Error(
                WebSearchToolResultContent.WebSearchErrorCode.max_uses_exceeded
              ),
              "srv_1"
            )
          )
        ),
        ContentBlockStart(
          1,
          "mcp_tool_result",
          Some(
            McpToolResultBlock(
              McpToolResultStructured(Seq(MCPToolResultItem("boom"))),
              isError = true,
              "mcp_1"
            )
          )
        )
      )

      out.collect { case tr: ToolResult => (tr.toolName, tr.text, tr.isError) } shouldBe Seq(
        ("web_search", Some("max_uses_exceeded"), true),
        ("mcp", Some("boom"), true)
      )
      out.collect { case w: WebSearchResult => w.results } shouldBe Seq(Nil)
    }

    "map redacted thinking, unknown events / deltas / blocks to typed chunks or Other" in {
      val out = run(
        messageStart(),
        ContentBlockStart(0, "redacted_thinking", Some(RedactedThinkingBlock("opaque"))),
        ContentBlockStart(
          1,
          "container_upload",
          None,
          Some(Json.obj("type" -> "container_upload"))
        ),
        ContentBlockDeltaEvent(
          ContentBlockDelta("content_block_delta", 1, DeltaUnknown("weird_delta", Json.obj()))
        ),
        Ping,
        UnknownEvent("mystery", Json.obj("type" -> "mystery")),
        MessageStop
      )

      out shouldBe Seq(
        Start("msg_1", "claude-x"),
        RedactedThinking("opaque"),
        Other("container_upload", Json.obj("type" -> "container_upload")),
        Other("weird_delta", Json.obj()),
        Other("mystery", Json.obj("type" -> "mystery"))
      )
    }

    "flush a tool call still pending at message_delta and map max_tokens to length" in {
      val out = run(
        messageStart(),
        ContentBlockStart(0, "tool_use", Some(ToolUseBlock("t", "f", Json.obj("a" -> 1)))),
        messageDelta("max_tokens", 5)
      )

      out shouldBe Seq(
        Start("msg_1", "claude-x"),
        ToolCallStart(0, "t", "f", serverSide = false),
        ToolCall(0, "t", "f", "{\"a\":1}", serverSide = false),
        Finish(FinishReason.length, Some("max_tokens")),
        Usage(toOpenAI(UsageInfo(10, 5, None, None)))
      )
    }
  }
}
