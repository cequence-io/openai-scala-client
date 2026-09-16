package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{
  McpToolResultBlock,
  McpToolUseBlock,
  TextBlock,
  ThinkingBlock
}
import io.cequence.openaiscala.anthropic.domain.Content.{
  ContentBlock,
  ContentBlockBase,
  ContentBlocks
}
import io.cequence.openaiscala.anthropic.domain.Message.{
  AssistantMessageContent,
  UserMessage => AnthropicUserMessage
}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.DeltaBlock._
import io.cequence.openaiscala.anthropic.domain.response.MessageStreamEvent._
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse,
  MessageDeltaUsage,
  MessageStreamEvent
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.tools.MCPServerURLDefinition
import io.cequence.openaiscala.anthropic.domain.{ChatRole, McpToolResultString, Message}
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{NonOpenAIModelId, UserMessage}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

/**
 * The Anthropic adapter's MCP-server passthrough and `pause_turn` continuation, driven by a
 * scripted underlying service: what reaches the API (settings, the continuation messages) and
 * what one turn looks like on the typed stream when it spans several rounds.
 */
class AnthropicStreamedContinuationSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("anthropic-streamed-continuation")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private val server = MCPServerURLDefinition("deepwiki", "https://mcp.deepwiki.com/mcp")

  private val settings =
    CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_5)
      .setAnthropicMcpServers(Seq(server))

  private val userTurn = Seq(UserMessage("What does scala/scala say about `given`?"))

  private val continuePrompt =
    AnthropicUserMessage(OpenAIAnthropicChatCompletionService.ContinuationPrompt)

  // -- scripted events --

  private def start(
    id: String,
    inputTokens: Int
  ): MessageStart =
    MessageStart(
      CreateMessageResponse(
        id = id,
        role = ChatRole.Assistant,
        content = ContentBlocks(Nil),
        model = "claude-x",
        stop_reason = None,
        stop_sequence = None,
        usage = UsageInfo(inputTokens, 1, None, None)
      )
    )

  private def delta(
    index: Int,
    block: io.cequence.openaiscala.anthropic.domain.response.DeltaBlock
  ) = ContentBlockDeltaEvent(ContentBlockDelta("content_block_delta", index, block))

  private def end(
    stop: String,
    outputTokens: Int
  ) = MessageDelta(Some(stop), None, Some(MessageDeltaUsage(outputTokens, None, None, None)))

  private val mcpUse = McpToolUseBlock("mcp_1", "read_wiki", "deepwiki", Json.obj())
  private val mcpResult =
    McpToolResultBlock(McpToolResultString("given = implicit"), false, "mcp_1")

  /** Round 1: thinking, an MCP call + result, some text, then the API pauses the turn. */
  private val pausedRound: List[MessageStreamEvent] = List(
    start("msg_1", 10),
    ContentBlockStart(0, "thinking", None),
    delta(0, DeltaThinking("hmm")),
    delta(0, DeltaSignature("sig-1")),
    ContentBlockStop(0),
    ContentBlockStart(1, "mcp_tool_use", Some(mcpUse)),
    delta(1, DeltaInputJson("{\"repo\": \"scala/scala\"}")),
    ContentBlockStop(1),
    ContentBlockStart(2, "mcp_tool_result", Some(mcpResult)),
    ContentBlockStop(2),
    ContentBlockStart(3, "text", None),
    delta(3, DeltaText("Looking")),
    ContentBlockStop(3),
    end("pause_turn", 20),
    MessageStop
  )

  /** Round 2: a second MCP call and the final answer. */
  private val finalRound: List[MessageStreamEvent] = List(
    start("msg_2", 30),
    ContentBlockStart(
      0,
      "mcp_tool_use",
      Some(mcpUse.copy(id = "mcp_2", input = Json.obj("q" -> 1)))
    ),
    ContentBlockStop(0),
    ContentBlockStart(1, "text", None),
    delta(1, DeltaText(" done.")),
    ContentBlockStop(1),
    end("end_turn", 5),
    MessageStop
  )

  private val round1Blocks = Seq(
    ContentBlockBase(ThinkingBlock("hmm", "sig-1")),
    ContentBlockBase(mcpUse.copy(input = Json.obj("repo" -> "scala/scala"))),
    ContentBlockBase(mcpResult),
    ContentBlockBase(TextBlock("Looking"))
  )

  /** A scripted underlying service recording every streamed / plain request it receives. */
  private final class Scripted(
    streamed: Seq[Seq[MessageStreamEvent]] = Nil,
    plain: Seq[CreateMessageResponse] = Nil
  ) {
    val streamedRequests = ListBuffer.empty[(Seq[Message], AnthropicCreateMessageSettings)]
    val plainRequests = ListBuffer.empty[(Seq[Message], AnthropicCreateMessageSettings)]

    val underlying: AnthropicService = mock(classOf[AnthropicService])

    when(underlying.createMessageStreamedEvents(any(), any())).thenAnswer {
      (invocation: InvocationOnMock) =>
        val messages = invocation.getArgument[Seq[Message]](0)
        val settings = invocation.getArgument[AnthropicCreateMessageSettings](1)
        streamedRequests += ((messages, settings))
        val events = streamed(streamedRequests.size - 1)
        Source(events.toList): Source[MessageStreamEvent, NotUsed]
    }

    when(underlying.createMessage(any(), any())).thenAnswer { (invocation: InvocationOnMock) =>
      val messages = invocation.getArgument[Seq[Message]](0)
      val settings = invocation.getArgument[AnthropicCreateMessageSettings](1)
      plainRequests += ((messages, settings))
      Future.successful(plain(plainRequests.size - 1))
    }

    val adapter = new OpenAIAnthropicChatCompletionService(underlying)
  }

  private def stream(
    scripted: Scripted,
    settings: CreateChatCompletionSettings = settings
  ): Seq[ChatChunk] =
    scripted.adapter
      .createChatToolCompletionStreamed(userTurn, Nil, None, settings)
      .runWith(Sink.seq)
      .futureValue

  "createChatToolCompletionStreamed" should {

    "send the MCP servers from the settings" in {
      val scripted = new Scripted(streamed = Seq(finalRound))

      stream(scripted)

      scripted.streamedRequests.map(_._2.mcp_servers) shouldBe Seq(Seq(server))
    }

    "continue a pause_turn on the same stream: one Start, one Finish, one summed Usage" in {
      val scripted = new Scripted(streamed = Seq(pausedRound, finalRound))

      val out = stream(scripted)

      out shouldBe Seq(
        Start("msg_1", "claude-x"),
        Thinking("hmm"),
        ThinkingSignature("sig-1"),
        ToolCallStart(0, "mcp_1", "read_wiki", serverSide = true),
        ToolCallDelta(0, "{\"repo\": \"scala/scala\"}"),
        ToolCall(0, "mcp_1", "read_wiki", "{\"repo\": \"scala/scala\"}", serverSide = true),
        ToolResult(
          "mcp_1",
          "mcp",
          Json.toJson(mcpResult: ContentBlock)(
            io.cequence.openaiscala.anthropic.JsonFormats.contentBlockFormat
          ),
          Some("given = implicit"),
          isError = false
        ),
        Text("Looking"),
        // round boundary: no Finish / Usage, no second Start; ordinals keep counting
        ToolCallStart(1, "mcp_2", "read_wiki", serverSide = true),
        ToolCall(1, "mcp_2", "read_wiki", "{\"q\":1}", serverSide = true),
        Text(" done."),
        Finish(FinishReason.stop, Some("end_turn")),
        Usage(toOpenAI(UsageInfo(10 + 30, 20 + 5, None, None)))
      )
    }

    "resume with the assistant content echoed back plus the continuation prompt" in {
      val scripted = new Scripted(streamed = Seq(pausedRound, finalRound))

      stream(scripted)

      val Seq((first, _), (second, secondSettings)) = scripted.streamedRequests.toSeq
      second shouldBe first ++ Seq(AssistantMessageContent(round1Blocks), continuePrompt)
      secondSettings.mcp_servers shouldBe Seq(server)
    }

    "stop at the continuation cap and report the pause_turn as the Finish" in {
      val scripted = new Scripted(streamed = Seq(pausedRound, pausedRound, pausedRound))

      val out = stream(scripted, settings.setAnthropicMaxContinuations(1))

      scripted.streamedRequests should have size 2
      out.collect { case s: Start => s } should have size 1
      out.collect { case f: Finish => f } shouldBe Seq(
        Finish(FinishReason.unknown, Some("pause_turn"))
      )
      out.last shouldBe Usage(toOpenAI(UsageInfo(20, 40, None, None)))
    }

    "not continue at all when the cap is zero" in {
      val scripted = new Scripted(streamed = Seq(pausedRound))

      val out = stream(scripted, settings.setAnthropicMaxContinuations(0))

      scripted.streamedRequests should have size 1
      out.collect { case f: Finish => f.providerReason } shouldBe Seq(Some("pause_turn"))
    }

    "also continue a tool_use that ends in an unrun MCP call, stripping that call from the echo" in {
      val unrun: List[MessageStreamEvent] = List(
        start("msg_1", 10),
        ContentBlockStart(0, "text", None),
        delta(0, DeltaText("Let me check.")),
        ContentBlockStop(0),
        ContentBlockStart(1, "mcp_tool_use", Some(mcpUse)),
        ContentBlockStop(1),
        end("tool_use", 3),
        MessageStop
      )
      val scripted = new Scripted(streamed = Seq(unrun, finalRound))

      val out = stream(scripted)

      scripted.streamedRequests(1)._1 shouldBe scripted.streamedRequests(0)._1 ++ Seq(
        AssistantMessageContent(Seq(ContentBlockBase(TextBlock("Let me check.")))),
        continuePrompt
      )
      out.collect { case f: Finish => f.providerReason } shouldBe Seq(Some("end_turn"))
    }

    "leave a plain end_turn stream untouched" in {
      val scripted = new Scripted(streamed = Seq(finalRound))

      val out = stream(scripted)

      scripted.streamedRequests should have size 1
      out.head shouldBe Start("msg_2", "claude-x")
      out.last shouldBe Usage(toOpenAI(UsageInfo(30, 5, None, None)))
    }
  }

  private def response(
    id: String,
    stop: String,
    blocks: Seq[ContentBlockBase],
    usage: UsageInfo
  ): CreateMessageResponse =
    CreateMessageResponse(
      id = id,
      role = ChatRole.Assistant,
      content = ContentBlocks(blocks),
      model = "claude-x",
      stop_reason = Some(stop),
      stop_sequence = None,
      usage = usage
    )

  "createChatToolCompletion / createChatCompletion" should {

    "send the MCP servers and continue a pause_turn into one response" in {
      val paused =
        response("msg_1", "pause_turn", round1Blocks, UsageInfo(10, 20, Some(4), None))
      val done = response(
        "msg_2",
        "end_turn",
        Seq(ContentBlockBase(TextBlock(" done."))),
        UsageInfo(30, 5, None, Some(2))
      )
      val scripted = new Scripted(plain = Seq(paused, done))

      val out =
        scripted.adapter.createChatToolCompletion(userTurn, Nil, None, settings).futureValue

      scripted.plainRequests.map(_._2.mcp_servers) shouldBe Seq(Seq(server), Seq(server))
      scripted.plainRequests(1)._1 shouldBe
        scripted.plainRequests(0)._1 ++ Seq(
          AssistantMessageContent(round1Blocks),
          continuePrompt
        )

      out.choices.head.finish_reason shouldBe Some("end_turn")
      out.choices.head.message.content shouldBe Some("Looking\n done.")
      out.usage.map(_.prompt_tokens) shouldBe Some(10 + 4 + 30 + 2)
      out.usage.flatMap(_.completion_tokens) shouldBe Some(25)
      // the raw Anthropic response rides along, with both rounds' blocks
      out.originalResponse.collect { case r: CreateMessageResponse =>
        r.content.blocks.size
      } shouldBe
        Some(round1Blocks.size + 1)
    }

    "give up at the cap on the plain path too" in {
      val paused = response("msg_1", "pause_turn", round1Blocks, UsageInfo(10, 20, None, None))
      val scripted = new Scripted(plain = Seq(paused, paused, paused))

      val out = scripted.adapter
        .createChatCompletion(userTurn, settings.setAnthropicMaxContinuations(2))
        .futureValue

      scripted.plainRequests should have size 3
      out.choices.head.finish_reason shouldBe Some("pause_turn")
    }
  }
}
