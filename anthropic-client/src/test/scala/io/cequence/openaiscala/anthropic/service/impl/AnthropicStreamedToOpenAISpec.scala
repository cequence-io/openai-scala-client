package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.OpenAIScalaRateLimitException
import io.cequence.openaiscala.anthropic.domain.ChatRole
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.ToolUseBlock
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlocks
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.DeltaBlock.{
  DeltaInputJson,
  DeltaText,
  DeltaThinking
}
import io.cequence.openaiscala.anthropic.domain.response.MessageStreamEvent._
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse,
  MessageDeltaUsage,
  MessageStreamEvent
}
import io.cequence.openaiscala.anthropic.service.AnthropicScalaRateLimitException
import io.cequence.openaiscala.domain.ChatRole.Assistant
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

/**
 * Coverage for (A2): the [[toOpenAIChunks]] flow mapping a raw Anthropic
 * [[MessageStreamEvent]] stream to OpenAI-shaped chat-completion-chunk responses, and for
 * [[toOpenAIException]] error repackaging on the streamed adapter path.
 */
class AnthropicStreamedToOpenAISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("anthropic-streamed-to-openai-spec")
  private implicit val materializer: Materializer = Materializer(system)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  private def messageStart(
    id: String = "msg_1",
    model: String = "claude-x",
    inputTokens: Int = 10,
    outputTokens: Int = 1
  ): MessageStart =
    MessageStart(
      CreateMessageResponse(
        id = id,
        role = ChatRole.Assistant,
        content = ContentBlocks(Nil),
        model = model,
        stop_reason = None,
        stop_sequence = None,
        usage = UsageInfo(
          input_tokens = inputTokens,
          output_tokens = outputTokens,
          cache_creation_input_tokens = None,
          cache_read_input_tokens = None
        )
      )
    )

  private def textDelta(
    index: Int,
    text: String
  ): ContentBlockDeltaEvent =
    ContentBlockDeltaEvent(ContentBlockDelta("content_block_delta", index, DeltaText(text)))

  private def thinkingDelta(
    index: Int,
    text: String
  ): ContentBlockDeltaEvent =
    ContentBlockDeltaEvent(
      ContentBlockDelta("content_block_delta", index, DeltaThinking(text))
    )

  private def inputJsonDelta(
    index: Int,
    partial: String
  ): ContentBlockDeltaEvent =
    ContentBlockDeltaEvent(
      ContentBlockDelta("content_block_delta", index, DeltaInputJson(partial))
    )

  "toOpenAIChunks" should {

    "map a text-only conversation to chunks carrying id/model/index 0, and a final finish_reason+usage chunk" in {
      val events: Seq[MessageStreamEvent] = Seq(
        messageStart(id = "msg_1", model = "claude-x", inputTokens = 10, outputTokens = 1),
        textDelta(0, "Hel"),
        textDelta(0, "lo"),
        MessageDelta(Some("end_turn"), None, Some(MessageDeltaUsage(output_tokens = 5)))
      )

      val chunks = Source(events.toList).via(toOpenAIChunks).runWith(Sink.seq).futureValue

      chunks.foreach { c =>
        c.id shouldBe "msg_1"
        c.model shouldBe "claude-x"
        c.choices.head.index shouldBe 0
      }

      chunks.head.choices.head.delta.role shouldBe Some(Assistant)

      val texts = chunks.flatMap(_.choices.flatMap(_.delta.content))
      texts.filter(_.nonEmpty) shouldBe Seq("Hel", "lo")

      val last = chunks.last
      last.choices.head.finish_reason shouldBe Some("end_turn")
      last.usage.map(_.prompt_tokens) shouldBe Some(10)
      last.usage.flatMap(_.completion_tokens) shouldBe Some(5)
      last.usage.map(_.total_tokens) shouldBe Some(15)
    }

    "keep choice index 0 for text after a thinking block (thinking deltas produce no content chunks)" in {
      val events: Seq[MessageStreamEvent] = Seq(
        messageStart(),
        ContentBlockStart(0, "thinking", None),
        thinkingDelta(0, "pondering..."),
        ContentBlockStop(0),
        ContentBlockStart(1, "text", None),
        textDelta(1, "answer"),
        ContentBlockStop(1),
        MessageDelta(Some("end_turn"), None, None)
      )

      val chunks = Source(events.toList).via(toOpenAIChunks).runWith(Sink.seq).futureValue

      // thinking deltas must not surface as content chunks (the only non-empty content is
      // the text delta - the message-start chunk carries content = Some(""))
      chunks.flatMap(_.choices.flatMap(_.delta.content)).filter(_.nonEmpty) shouldBe Seq(
        "answer"
      )
      // every chunk uses OpenAI's choice index (0), never Anthropic's content-block index (1)
      chunks.foreach(_.choices.head.index shouldBe 0)
    }

    "map two tool_use blocks to distinct tool-call ordinals with streamed arguments" in {
      val events: Seq[MessageStreamEvent] = Seq(
        messageStart(),
        ContentBlockStart(0, "text", None),
        ContentBlockStop(0),
        ContentBlockStart(
          1,
          "tool_use",
          Some(ToolUseBlock("tool_1", "get_weather", Json.obj()))
        ),
        inputJsonDelta(1, "{\"city\":"),
        inputJsonDelta(1, "\"Oslo\"}"),
        ContentBlockStop(1),
        ContentBlockStart(2, "tool_use", Some(ToolUseBlock("tool_2", "get_time", Json.obj()))),
        inputJsonDelta(2, "{\"tz\":\"UTC\"}"),
        ContentBlockStop(2),
        MessageDelta(Some("tool_use"), None, Some(MessageDeltaUsage(output_tokens = 3)))
      )

      val chunks = Source(events.toList).via(toOpenAIChunks).runWith(Sink.seq).futureValue

      val toolCallFragments =
        chunks.flatMap(_.choices.flatMap(_.delta.tool_calls.getOrElse(Nil)))

      val firstTool1 = toolCallFragments.find(_.id.contains("tool_1")).get
      firstTool1.index shouldBe 0
      firstTool1.`type` shouldBe Some("function")
      firstTool1.function.flatMap(_.name) shouldBe Some("get_weather")
      firstTool1.function.flatMap(_.arguments) shouldBe Some("")

      val firstTool2 = toolCallFragments.find(_.id.contains("tool_2")).get
      firstTool2.index shouldBe 1
      firstTool2.function.flatMap(_.name) shouldBe Some("get_time")

      val tool1Args = toolCallFragments
        .filter(f => f.index == 0 && f.id.isEmpty)
        .flatMap(_.function.flatMap(_.arguments))
        .mkString
      tool1Args shouldBe "{\"city\":\"Oslo\"}"

      val tool2Args = toolCallFragments
        .filter(f => f.index == 1 && f.id.isEmpty)
        .flatMap(_.function.flatMap(_.arguments))
        .mkString
      tool2Args shouldBe "{\"tz\":\"UTC\"}"

      chunks.last.choices.head.finish_reason shouldBe Some("tool_use")
    }

    "emit no chunks for Ping / UnknownEvent / ContentBlockStop / MessageStop" in {
      val events: Seq[MessageStreamEvent] = Seq(
        Ping,
        UnknownEvent("some_future_event", Json.obj()),
        ContentBlockStop(0),
        MessageStop
      )

      val chunks = Source(events.toList).via(toOpenAIChunks).runWith(Sink.seq).futureValue

      chunks shouldBe empty
    }

    "still emit a finish_reason chunk with usage None when MessageDelta carries no usage" in {
      val events: Seq[MessageStreamEvent] = Seq(
        messageStart(),
        MessageDelta(Some("max_tokens"), None, None)
      )

      val chunks = Source(events.toList).via(toOpenAIChunks).runWith(Sink.seq).futureValue

      val last = chunks.last
      last.choices.head.finish_reason shouldBe Some("max_tokens")
      last.usage shouldBe None
    }
  }

  "toOpenAIException" should {

    "map an AnthropicScalaRateLimitException to OpenAIScalaRateLimitException" in {
      toOpenAIException(new AnthropicScalaRateLimitException("rate limited")) shouldBe a[
        OpenAIScalaRateLimitException
      ]
    }

    "leave an unrelated RuntimeException unchanged" in {
      val original = new RuntimeException("boom")
      toOpenAIException(original) should be theSameInstanceAs original
    }

    "fail a mapError'd Source with the repackaged exception" in {
      val failed = Source
        .failed[Int](new AnthropicScalaRateLimitException("rate limited"))
        .mapError(toOpenAIException)
        .runWith(Sink.ignore)

      whenReady(failed.failed) { e =>
        e shouldBe a[OpenAIScalaRateLimitException]
      }
    }
  }
}
