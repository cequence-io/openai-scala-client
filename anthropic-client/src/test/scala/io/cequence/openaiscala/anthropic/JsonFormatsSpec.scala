package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.JsonFormatsSpec.JsonPrintMode
import io.cequence.openaiscala.anthropic.JsonFormatsSpec.JsonPrintMode.{Compact, Pretty}
import io.cequence.openaiscala.anthropic.domain.CacheControl.Ephemeral
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{MediaBlock, TextBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{
  AssistantMessage,
  AssistantMessageContent,
  SystemMessage,
  UserMessage,
  UserMessageContent
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}

object JsonFormatsSpec {
  sealed trait JsonPrintMode
  object JsonPrintMode {
    case object Compact extends JsonPrintMode
    case object Pretty extends JsonPrintMode
  }
}

class JsonFormatsSpec extends AnyWordSpecLike with Matchers with JsonFormats {

  "JSON Formats" should {
    "serialize and deserialize a user message with a single string content" in {
      val userMessage = UserMessage("Hello, world!")
      val json = """{"role":"user","content":"Hello, world!"}"""
      testCodec[Message](userMessage, json)
    }

    "serialize and deserialize a user message with text content blocks" in {
      val userMessage =
        UserMessageContent(
          Seq(
            ContentBlockBase(TextBlock("Hello, world!")),
            ContentBlockBase(TextBlock("How are you?"))
          )
        )
      val json =
        """{"role":"user","content":[{"type":"text","text":"Hello, world!"},{"type":"text","text":"How are you?"}]}"""
      testCodec[Message](userMessage, json)
    }

    "serialize and deserialize an assistant message with a single string content" in {
      val assistantMessage = AssistantMessage("Hello, world!")
      val json = """{"role":"assistant","content":"Hello, world!"}"""
      testCodec[Message](assistantMessage, json)
    }

    "serialize and deserialize an assistant message with text content blocks" in {
      val assistantMessage =
        AssistantMessageContent(
          Seq(
            ContentBlockBase(TextBlock("Hello, world!")),
            ContentBlockBase(TextBlock("How are you?"))
          )
        )
      val json =
        """{"role":"assistant","content":[{"type":"text","text":"Hello, world!"},{"type":"text","text":"How are you?"}]}"""
      testCodec[Message](assistantMessage, json)
    }

    val expectedImageContentJson =
      """{
        |  "role" : "user",
        |  "content" : [ {
        |    "type" : "image",
        |    "source" : {
        |      "type" : "base64",
        |      "media_type" : "image/jpeg",
        |      "data" : "/9j/4AAQSkZJRg..."
        |    }
        |  } ]
        |}""".stripMargin

    "serialize and deserialize a message with an image content" in {
      val userMessage =
        UserMessageContent(
          Seq(
            ContentBlockBase(MediaBlock("image", "base64", "image/jpeg", "/9j/4AAQSkZJRg..."))
          )
        )
      testCodec[Message](userMessage, expectedImageContentJson, Pretty)
    }

    // TEST CACHING
    "serialize and deserialize Cache control" should {
      "serialize and deserialize arbitrary (first) user message with caching" in {
        val userMessage =
          UserMessageContent(
            Seq(
              ContentBlockBase(TextBlock("Hello, world!"), Some(Ephemeral)),
              ContentBlockBase(TextBlock("How are you?"))
            )
          )
        val json =
          """{"role":"user","content":[{"type":"text","text":"Hello, world!","cache_control":{"type":"ephemeral"}},{"type":"text","text":"How are you?"}]}"""
        testCodec[Message](userMessage, json)
      }

      "serialize and deserialize arbitrary (second) user message with caching" in {
        val userMessage =
          UserMessageContent(
            Seq(
              ContentBlockBase(TextBlock("Hello, world!")),
              ContentBlockBase(TextBlock("How are you?"), Some(Ephemeral))
            )
          )
        val json =
          """{"role":"user","content":[{"type":"text","text":"Hello, world!"},{"type":"text","text":"How are you?","cache_control":{"type":"ephemeral"}}]}"""
        testCodec[Message](userMessage, json)
      }

      "serialize and deserialize arbitrary (first) image content with caching" in {
        val userMessage =
          UserMessageContent(
            Seq(
              MediaBlock.jpeg("/9j/4AAQSkZJRg...", Some(Ephemeral)),
              ContentBlockBase(TextBlock("How are you?"))
            )
          )

        val imageJson =
          """{"type":"image","source":{"type":"base64","media_type":"image/jpeg","data":"/9j/4AAQSkZJRg..."},"cache_control":{"type":"ephemeral"}}""".stripMargin
        val json =
          s"""{"role":"user","content":[$imageJson,{"type":"text","text":"How are you?"}]}"""
        testCodec[Message](userMessage, json)
      }
    }

  }

  private def testCodec[A](
    value: A,
    json: String,
    printMode: JsonPrintMode = Compact
  )(
    implicit format: Format[A]
  ): Unit = {
    val jsValue = Json.toJson(value)
    val serialized = printMode match {
      case Compact => jsValue.toString()
      case Pretty  => Json.prettyPrint(jsValue)
    }
    serialized shouldBe json

    Json.parse(json).as[A] shouldBe value
  }

}
