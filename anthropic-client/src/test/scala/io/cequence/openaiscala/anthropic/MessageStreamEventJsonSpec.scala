package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.ToolUseBlock
import io.cequence.openaiscala.anthropic.domain.response.DeltaBlock.{
  DeltaCitations,
  DeltaInputJson,
  DeltaSignature,
  DeltaText,
  DeltaThinking,
  DeltaUnknown
}
import io.cequence.openaiscala.anthropic.domain.response.MessageStreamEvent.{
  ContentBlockDeltaEvent,
  ContentBlockStart,
  ContentBlockStop,
  MessageDelta,
  MessageStart,
  MessageStop,
  Ping,
  UnknownEvent
}
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  DeltaBlock,
  MessageStreamEvent
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/**
 * Coverage for parsing REAL Anthropic streaming Messages API SSE event JSON
 * (https://docs.anthropic.com/en/api/messages-streaming) into [[MessageStreamEvent]] - the fix
 * for (A2): an unrecognized event/delta type must never fail parsing.
 */
class MessageStreamEventJsonSpec extends AnyWordSpec with Matchers with JsonFormats {

  private def parse(json: String): MessageStreamEvent =
    Json.parse(json).as[MessageStreamEvent]

  "MessageStreamEvent JSON parsing" should {

    "parse message_start (with usage incl. cache fields)" in {
      val event = parse("""
        |{
        |  "type": "message_start",
        |  "message": {
        |    "id": "msg_01ABC",
        |    "type": "message",
        |    "role": "assistant",
        |    "content": [],
        |    "model": "claude-sonnet-4-6",
        |    "stop_reason": null,
        |    "stop_sequence": null,
        |    "usage": {
        |      "input_tokens": 25,
        |      "output_tokens": 1,
        |      "cache_creation_input_tokens": 10,
        |      "cache_read_input_tokens": 5
        |    }
        |  }
        |}
        |""".stripMargin)

      event match {
        case MessageStart(message) =>
          message.id shouldBe "msg_01ABC"
          message.model shouldBe "claude-sonnet-4-6"
          message.usage.input_tokens shouldBe 25
          message.usage.output_tokens shouldBe 1
          message.usage.cache_creation_input_tokens shouldBe Some(10)
          message.usage.cache_read_input_tokens shouldBe Some(5)
        case other => fail(s"Expected MessageStart, got $other")
      }
    }

    "parse content_block_start for a text block" in {
      val event = parse(
        """{"type":"content_block_start","index":0,""" +
          """"content_block":{"type":"text","text":""}}"""
      )

      event match {
        case ContentBlockStart(0, "text", Some(_), _) => succeed
        case other => fail(s"Expected ContentBlockStart, got $other")
      }
    }

    "parse content_block_start for a tool_use block" in {
      val event = parse(
        """{"type":"content_block_start","index":1,"content_block":""" +
          """{"type":"tool_use","id":"toolu_01","name":"get_weather","input":{}}}"""
      )

      event match {
        case ContentBlockStart(1, "tool_use", Some(ToolUseBlock(id, name, _)), _) =>
          id shouldBe "toolu_01"
          name shouldBe "get_weather"
        case other => fail(s"Expected ContentBlockStart(tool_use), got $other")
      }
    }

    "parse content_block_start for a thinking block without a signature (lenient)" in {
      val event = parse(
        """{"type":"content_block_start","index":0,"content_block":""" +
          """{"type":"thinking","thinking":""}}"""
      )

      event match {
        case ContentBlockStart(0, "thinking", None, _) => succeed
        case other => fail(s"Expected ContentBlockStart(thinking, None), got $other")
      }
    }

    "parse content_block_delta text_delta" in {
      val event = parse(
        """{"type":"content_block_delta","index":0,"delta":""" +
          """{"type":"text_delta","text":"Hello"}}"""
      )

      event match {
        case ContentBlockDeltaEvent(ContentBlockDelta(_, 0, DeltaText("Hello"))) => succeed
        case other => fail(s"Expected text_delta, got $other")
      }
    }

    "parse content_block_delta thinking_delta" in {
      val event = parse(
        """{"type":"content_block_delta","index":0,"delta":""" +
          """{"type":"thinking_delta","thinking":"pondering"}}"""
      )

      event match {
        case ContentBlockDeltaEvent(ContentBlockDelta(_, 0, DeltaThinking("pondering"))) =>
          succeed
        case other => fail(s"Expected thinking_delta, got $other")
      }
    }

    "parse content_block_delta signature_delta" in {
      val event = parse(
        """{"type":"content_block_delta","index":0,"delta":""" +
          """{"type":"signature_delta","signature":"sig123"}}"""
      )

      event match {
        case ContentBlockDeltaEvent(ContentBlockDelta(_, 0, DeltaSignature("sig123"))) =>
          succeed
        case other => fail(s"Expected signature_delta, got $other")
      }
    }

    "parse content_block_delta input_json_delta" in {
      val event = parse(
        """{"type":"content_block_delta","index":1,"delta":""" +
          """{"type":"input_json_delta","partial_json":"{\"city\":"}}"""
      )

      event match {
        case ContentBlockDeltaEvent(ContentBlockDelta(_, 1, DeltaInputJson(partial))) =>
          partial shouldBe "{\"city\":"
        case other => fail(s"Expected input_json_delta, got $other")
      }
    }

    "parse content_block_delta citations_delta" in {
      val event = parse(
        """{"type":"content_block_delta","index":0,"delta":{"type":"citations_delta",""" +
          """"citation":{"type":"web_search_result_location","url":"https://example.com"}}}"""
      )

      event match {
        case ContentBlockDeltaEvent(ContentBlockDelta(_, 0, DeltaCitations(citation))) =>
          (citation \ "url").as[String] shouldBe "https://example.com"
        case other => fail(s"Expected citations_delta, got $other")
      }
    }

    "parse an unknown content_block_delta type as DeltaUnknown - not an exception" in {
      // parsing itself must not throw - this call would blow up the test if it did
      val event = parse(
        """{"type":"content_block_delta","index":0,"delta":{"type":"foo_delta","bar":42}}"""
      )

      event match {
        case ContentBlockDeltaEvent(ContentBlockDelta(_, 0, DeltaUnknown("foo_delta", raw))) =>
          (raw \ "bar").as[Int] shouldBe 42
        case other => fail(s"Expected DeltaUnknown, got $other")
      }
    }

    "parse content_block_stop" in {
      val event = parse("""{"type":"content_block_stop","index":2}""")

      event match {
        case ContentBlockStop(2) => succeed
        case other               => fail(s"Expected ContentBlockStop, got $other")
      }
    }

    "parse message_delta" in {
      val event = parse(
        """{"type":"message_delta","delta":{"stop_reason":"tool_use",""" +
          """"stop_sequence":null},"usage":{"output_tokens":42}}"""
      )

      event match {
        case MessageDelta(Some("tool_use"), None, Some(usage)) =>
          usage.output_tokens shouldBe 42
        case other => fail(s"Expected MessageDelta, got $other")
      }
    }

    "parse message_stop" in {
      parse("""{"type":"message_stop"}""") shouldBe MessageStop
    }

    "parse ping" in {
      parse("""{"type":"ping"}""") shouldBe Ping
    }

    "parse an unknown top-level event type as UnknownEvent - not an exception" in {
      // parsing itself must not throw - this call would blow up the test if it did
      val event = parse("""{"type":"some_future_event","x":1}""")

      event match {
        case UnknownEvent("some_future_event", raw) =>
          (raw \ "x").as[Int] shouldBe 1
        case other => fail(s"Expected UnknownEvent, got $other")
      }
    }

    "round-trip DeltaInputJson via deltaBlockFormat" in {
      val delta: DeltaBlock = DeltaInputJson("""{"city":"Oslo"}""")
      val jsValue = Json.toJson(delta)
      (jsValue \ "type").as[String] shouldBe "input_json_delta"
      jsValue.as[DeltaBlock] shouldBe delta
    }
  }
}
