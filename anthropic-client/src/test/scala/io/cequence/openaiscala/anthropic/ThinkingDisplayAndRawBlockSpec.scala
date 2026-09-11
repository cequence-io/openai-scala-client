package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.JsonFormats._
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.WebSearchToolResultBlock
import io.cequence.openaiscala.anthropic.domain.response.MessageStreamEvent
import io.cequence.openaiscala.anthropic.domain.response.MessageStreamEvent.ContentBlockStart
import io.cequence.openaiscala.anthropic.domain.settings.{ThinkingDisplay, ThinkingSettings}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/**
 * `ThinkingSettings.display` on the wire and `ContentBlockStart.raw` for large server-tool
 * blocks.
 */
class ThinkingDisplayAndRawBlockSpec extends AnyWordSpec with Matchers {

  "ThinkingSettings JSON" should {

    "write display when set and omit it otherwise" in {
      Json.toJson(ThinkingSettings.adaptiveSummarized) shouldBe
        Json.obj("type" -> "adaptive", "display" -> "summarized")

      Json.toJson(ThinkingSettings.adaptive) shouldBe Json.obj("type" -> "adaptive")

      Json.toJson(ThinkingSettings.enabled(2048).withDisplay(ThinkingDisplay.omitted)) shouldBe
        Json.obj("type" -> "enabled", "budget_tokens" -> 2048, "display" -> "omitted")
    }
  }

  "content_block_start parsing" should {

    "keep the raw JSON of a large web_search_tool_result block and still parse it" in {
      // ~25 KB of encrypted content, mirroring the block that used to overflow the 20 KB
      // SSE frame limit
      val encrypted = "x" * 25000
      val json = Json.parse(
        s"""{
           |  "type": "content_block_start",
           |  "index": 1,
           |  "content_block": {
           |    "type": "web_search_tool_result",
           |    "tool_use_id": "srvtoolu_1",
           |    "content": [
           |      {"type": "web_search_result", "title": "T", "url": "https://u", "encrypted_content": "$encrypted", "page_age": null}
           |    ]
           |  }
           |}""".stripMargin
      )

      json.as[MessageStreamEvent] match {
        case ContentBlockStart(1, "web_search_tool_result", Some(block), Some(raw)) =>
          block shouldBe a[WebSearchToolResultBlock]
          (raw \ "tool_use_id").as[String] shouldBe "srvtoolu_1"
        case other => fail(s"Unexpected $other")
      }
    }

    "expose the raw JSON even when the block cannot be parsed" in {
      val json = Json.parse(
        """{"type": "content_block_start", "index": 0, "content_block": {"type": "thinking", "thinking": ""}}"""
      )

      json.as[MessageStreamEvent] match {
        case ContentBlockStart(0, "thinking", _, Some(raw)) =>
          (raw \ "type").as[String] shouldBe "thinking"
        case other => fail(s"Unexpected $other")
      }
    }
  }
}
