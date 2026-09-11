package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.JsonFormats.chatChunkFormat
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.{ChatRole, FunctionCallChunkSpec, ToolCallChunkSpec}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import java.{util => ju}
import scala.concurrent.ExecutionContext

/**
 * [[ChatChunks.fromOpenAIChunks]] / [[ChatChunks.toOpenAIChunks]], the [[ChatChunk]] JSON
 * format and [[AssembledChatCompletion]].
 */
class ChatChunksSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("chat-chunks-spec")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private val created = new ju.Date(0)

  private def chunk(
    delta: ChunkMessageSpec,
    finishReason: Option[String] = None,
    usage: Option[UsageInfo] = None,
    choiceIndex: Int = 0
  ): ChatCompletionChunkResponse =
    ChatCompletionChunkResponse(
      id = "chatcmpl-1",
      created = created,
      model = "gpt-test",
      system_fingerprint = None,
      choices = Seq(ChatCompletionChoiceChunkInfo(delta, choiceIndex, finishReason)),
      usage = usage
    )

  private def usageChunk(usage: UsageInfo): ChatCompletionChunkResponse =
    ChatCompletionChunkResponse("chatcmpl-1", created, "gpt-test", None, Nil, Some(usage))

  private def text(t: String) = ChunkMessageSpec(role = None, content = Some(t))

  private def toolDelta(
    index: Int,
    id: Option[String] = None,
    name: Option[String] = None,
    arguments: Option[String] = None
  ) =
    ChunkMessageSpec(
      role = None,
      content = None,
      tool_calls = Some(
        Seq(
          ToolCallChunkSpec(
            index = index,
            id = id,
            `type` = id.map(_ => "function"),
            function = Some(FunctionCallChunkSpec(name, arguments))
          )
        )
      )
    )

  private val usage =
    UsageInfo(prompt_tokens = 12, total_tokens = 20, completion_tokens = Some(8))

  private def run(chunks: ChatCompletionChunkResponse*): Seq[ChatChunk] =
    Source(chunks.toList).via(ChatChunks.fromOpenAIChunks).runWith(Sink.seq).futureValue

  "ChatChunks.fromOpenAIChunks" should {

    "map a text stream with a trailing usage-only chunk" in {
      val out = run(
        chunk(ChunkMessageSpec(role = Some(ChatRole.Assistant), content = Some(""))),
        chunk(text("Hel")),
        chunk(text("lo")),
        chunk(ChunkMessageSpec(role = None, content = None), finishReason = Some("stop")),
        usageChunk(usage)
      )

      out shouldBe Seq(
        Start("chatcmpl-1", "gpt-test"),
        Text("Hel"),
        Text("lo"),
        Finish(FinishReason.stop, Some("stop")),
        Usage(usage)
      )
    }

    "emit a usage repeated on every chunk (Groq-style) only once" in {
      val out = run(
        chunk(text("Hi"), usage = Some(usage)),
        chunk(
          ChunkMessageSpec(role = None, content = None),
          finishReason = Some("stop"),
          usage = Some(usage)
        ),
        usageChunk(usage)
      )

      out shouldBe Seq(
        Start("chatcmpl-1", "gpt-test"),
        Text("Hi"),
        Usage(usage),
        Finish(FinishReason.stop, Some("stop"))
      )
    }

    "assemble interleaved tool-call fragments and flush them on finish_reason" in {
      val out = run(
        chunk(
          toolDelta(0, id = Some("call_a"), name = Some("get_weather"), arguments = Some(""))
        ),
        chunk(toolDelta(0, arguments = Some("{\"loc"))),
        chunk(
          toolDelta(1, id = Some("call_b"), name = Some("get_time"), arguments = Some("{}"))
        ),
        chunk(toolDelta(0, arguments = Some("ation\": \"Oslo\"}"))),
        chunk(ChunkMessageSpec(role = None, content = None), finishReason = Some("tool_calls"))
      )

      out shouldBe Seq(
        Start("chatcmpl-1", "gpt-test"),
        ToolCallStart(0, "call_a", "get_weather", serverSide = false),
        ToolCallDelta(0, "{\"loc"),
        ToolCallStart(1, "call_b", "get_time", serverSide = false),
        ToolCallDelta(1, "{}"),
        ToolCallDelta(0, "ation\": \"Oslo\"}"),
        ToolCall(0, "call_a", "get_weather", "{\"location\": \"Oslo\"}", serverSide = false),
        ToolCall(1, "call_b", "get_time", "{}", serverSide = false),
        Finish(FinishReason.tool_calls, Some("tool_calls"))
      )
    }

    "map reasoning_content and reasoning deltas to Thinking" in {
      val out = run(
        chunk(ChunkMessageSpec(role = None, content = None, reasoning_content = Some("hmm"))),
        chunk(ChunkMessageSpec(role = None, content = None, reasoning = Some(" ok"))),
        chunk(text("42"), finishReason = Some("length"))
      )

      out shouldBe Seq(
        Start("chatcmpl-1", "gpt-test"),
        Thinking("hmm"),
        Thinking(" ok"),
        Text("42"),
        Finish(FinishReason.length, Some("length"))
      )
    }

    "pass additional choices through as Other" in {
      val out = run(chunk(text("second"), choiceIndex = 1))

      out.head shouldBe Start("chatcmpl-1", "gpt-test")
      out(1) match {
        case Other("choice[1]", raw) =>
          (raw \ "delta" \ "content").as[String] shouldBe "second"
        case other => fail(s"Unexpected $other")
      }
    }
  }

  "ChatChunks.toOpenAIChunks" should {

    "round-trip text, thinking, tool calls, finish and usage through fromOpenAIChunks" in {
      val typed = Seq(
        Start("id-1", "model-1"),
        Thinking("let me see"),
        Text("Hi"),
        ToolCallStart(0, "call_1", "get_weather", serverSide = false),
        ToolCallDelta(0, "{\"a\":1}"),
        Finish(FinishReason.tool_calls, Some("tool_calls")),
        Usage(usage)
      )

      val roundTripped = Source(typed.toList)
        .via(ChatChunks.toOpenAIChunks("id-1", "model-1"))
        .via(ChatChunks.fromOpenAIChunks)
        .runWith(Sink.seq)
        .futureValue

      roundTripped shouldBe Seq(
        Start("id-1", "model-1"),
        Thinking("let me see"),
        Text("Hi"),
        ToolCallStart(0, "call_1", "get_weather", serverSide = false),
        ToolCallDelta(0, "{\"a\":1}"),
        ToolCall(0, "call_1", "get_weather", "{\"a\":1}", serverSide = false),
        Finish(FinishReason.tool_calls, Some("tool_calls")),
        Usage(usage)
      )
    }
  }

  "ChatChunk JSON format" should {

    "round-trip every chunk type" in {
      val chunks: Seq[ChatChunk] = Seq(
        Start("id", "model"),
        Text("t"),
        Thinking("th"),
        ThinkingSignature("sig"),
        RedactedThinking("data"),
        ToolCallStart(0, "c", "n", serverSide = true),
        ToolCallDelta(0, "{"),
        ToolCall(0, "c", "n", "{}", serverSide = false),
        ToolResult("c", "web_search", Json.obj("k" -> 1), Some("txt"), isError = false),
        ToolResult("c", "mcp", Json.arr(1, 2), None, isError = true),
        Citation(Some("cited"), Some("http://x"), None, Json.obj("cited_text" -> "cited")),
        CodeExecution("c", Some("python"), "print(1)"),
        CodeExecutionResult("c", Some("1"), isError = false, Json.obj("outcome" -> "OK")),
        WebSearch("w", Seq("q1", "q2")),
        WebSearchResult(
          "w",
          Seq(
            WebSearchResultItem(Some("T"), "https://u"),
            WebSearchResultItem(None, "https://v")
          ),
          Json.arr()
        ),
        Image(Some("image/png"), Some("AAAA"), None),
        Image(None, None, Some("https://img")),
        Refusal("no"),
        Finish(FinishReason.content_filter, Some("refusal")),
        Finish(FinishReason.unknown, None),
        Usage(usage),
        Other("ping", Json.obj("type" -> "ping"))
      )

      chunks.foreach { c =>
        val json = Json.toJson(c)
        withClue(Json.prettyPrint(json)) {
          json.as[ChatChunk] shouldBe c
        }
      }

      Json.toJson(Finish(FinishReason.tool_calls, None): ChatChunk).toString shouldBe
        """{"type":"finish","reason":"tool_calls"}"""
    }
  }

  "AssembledChatCompletion" should {

    "fold a typed stream, ignoring argument deltas and excluding server-side calls from the assistant message" in {
      val assembled = Source(
        List[ChatChunk](
          Start("id", "model"),
          Thinking("a"),
          Thinking("b"),
          Text("Hello"),
          Text(" world"),
          ThinkingSignature("s1"),
          ToolCallStart(0, "c1", "f", serverSide = false),
          ThinkingSignature("sig-c1", Some("c1")),
          ToolCallDelta(0, "{\"x\":1}"),
          ToolCall(0, "c1", "f", "{\"x\":1}", serverSide = false),
          ToolCall(1, "srv", "web_search", "{}", serverSide = true),
          ToolResult("srv", "web_search", Json.obj(), Some("r"), isError = false),
          WebSearch("srv", Seq("q")),
          WebSearchResult("srv", Nil, Json.obj()),
          CodeExecution("ce", None, "x"),
          CodeExecutionResult("ce", None, isError = true, Json.obj()),
          Image(None, None, Some("u")),
          Refusal("no"),
          Refusal("pe"),
          Citation(None, Some("u"), None, Json.obj()),
          Other("ping", Json.obj()),
          Finish(FinishReason.tool_calls, Some("tool_use")),
          Usage(usage)
        )
      ).runWith(ChatChunk.assembleSink).futureValue

      assembled.id shouldBe Some("id")
      assembled.text shouldBe "Hello world"
      assembled.thinking shouldBe "ab"
      assembled.thinkingSignatures shouldBe Seq("s1", "sig-c1")
      assembled.toolCallSignatures shouldBe Map("c1" -> "sig-c1")
      assembled.toolCalls.map(_.callId) shouldBe Seq("c1", "srv")
      assembled.clientToolCalls.map(_.callId) shouldBe Seq("c1")
      assembled.toolResults.size shouldBe 1
      assembled.webSearches.map(_.queries) shouldBe Seq(Seq("q"))
      assembled.webSearchResults.size shouldBe 1
      assembled.codeExecutions.map(_.code) shouldBe Seq("x")
      assembled.codeExecutionResults.map(_.isError) shouldBe Seq(true)
      assembled.images.flatMap(_.url) shouldBe Seq("u")
      assembled.refusal shouldBe "nope"
      assembled.citations.size shouldBe 1
      assembled.other.map(_.kind) shouldBe Seq("ping")
      assembled.finishReason shouldBe Some(FinishReason.tool_calls)
      assembled.providerFinishReason shouldBe Some("tool_use")
      assembled.usage shouldBe Some(usage)

      val message = assembled.toAssistantToolMessage
      message.content shouldBe Some("Hello world")
      message.tool_calls.map(_._1) shouldBe Seq("c1")
      message.tool_calls.head._2
        .asInstanceOf[io.cequence.openaiscala.domain.FunctionCallSpec]
        .arguments shouldBe "{\"x\":1}"
    }

    "expose the legacy text view through the source ops" in {
      val texts = Source(
        List[ChatChunk](Start("i", "m"), Text("a"), Thinking("x"), Text("b"))
      ).texts.runWith(Sink.seq).futureValue

      texts shouldBe Seq("a", "b")
    }

    "expose every typed view through the source ops" in {
      val toolCall = ToolCall(0, "c1", "f", "{}", serverSide = false)
      val toolResult = ToolResult("c1", "f", Json.obj(), None, isError = false)
      val citation = Citation(None, Some("u"), None, Json.obj())
      val codeExecution = CodeExecution("ce", None, "x")
      val webSearch = WebSearch("ws", Seq("q"))
      val image = Image(None, None, Some("u"))

      val chunks = List[ChatChunk](
        Start("i", "m"),
        Thinking("t1"),
        Text("a"),
        Thinking("t2"),
        toolCall,
        toolResult,
        citation,
        codeExecution,
        webSearch,
        image,
        Finish(FinishReason.stop, None)
      )

      def source = Source(chunks)

      source.thinkingTexts.runWith(Sink.seq).futureValue shouldBe Seq("t1", "t2")
      source.toolCalls.runWith(Sink.seq).futureValue shouldBe Seq(toolCall)
      source.toolResults.runWith(Sink.seq).futureValue shouldBe Seq(toolResult)
      source.citations.runWith(Sink.seq).futureValue shouldBe Seq(citation)
      source.codeExecutions.runWith(Sink.seq).futureValue shouldBe Seq(codeExecution)
      source.webSearches.runWith(Sink.seq).futureValue shouldBe Seq(webSearch)
      source.images.runWith(Sink.seq).futureValue shouldBe Seq(image)
    }

    "fold an empty stream and reuse the sink across materializations" in {
      val sink = ChatChunk.assembleSink

      Source.empty[ChatChunk].runWith(sink).futureValue shouldBe AssembledChatCompletion.empty
      Source(List[ChatChunk](Text("a"))).runWith(sink).futureValue.text shouldBe "a"
      Source(List[ChatChunk](Text("b"))).runWith(sink).futureValue.text shouldBe "b"
    }
  }

  "ChatChunks.toOpenAIChunks finish reasons" should {

    "normalize a provider-specific finish reason to the OpenAI vocabulary" in {
      val chunks = Source(
        List[ChatChunk](
          Start("id", "m"),
          Finish(FinishReason.stop, Some("completed")),
          Finish(FinishReason.tool_calls, Some("tool_calls")),
          Finish(FinishReason.length, Some("max_output_tokens"))
        )
      ).via(ChatChunks.toOpenAIChunks).runWith(Sink.seq).futureValue

      chunks.flatMap(_.choices.flatMap(_.finish_reason)) shouldBe
        Seq("stop", "tool_calls", "length")
    }
  }
}
