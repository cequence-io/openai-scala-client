package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.JsonFormatsSpec.{Compact, JsonPrintMode, Pretty}
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{
  AssistantMessage,
  AssistantMessageContent,
  UserMessage,
  UserMessageContent
}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.TextBlock
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json}

object JsonFormatsSpec {
  sealed trait JsonPrintMode
  case object Compact extends JsonPrintMode
  case object Pretty extends JsonPrintMode
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
        UserMessageContent(Seq(TextBlock("Hello, world!"), TextBlock("How are you?")))
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
        AssistantMessageContent(Seq(TextBlock("Hello, world!"), TextBlock("How are you?")))
      val json =
        """{"role":"assistant","content":[{"type":"text","text":"Hello, world!"},{"type":"text","text":"How are you?"}]}"""
      testCodec[Message](assistantMessage, json)
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
