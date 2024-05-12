package io.cequence.openaiscala.anthropic

import io.cequence.openaiscala.anthropic.JsonFormatsSpec.JsonPrintMode
import io.cequence.openaiscala.anthropic.JsonFormatsSpec.JsonPrintMode.{Compact, Pretty}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{
  ImageBlock,
  TextBlock,
  ToolUseBlock
}
import io.cequence.openaiscala.anthropic.domain.Content.{ContentBlock, ContentBlocks}
import io.cequence.openaiscala.anthropic.domain.{ChatRole, Message, ToolSpec}
import io.cequence.openaiscala.anthropic.domain.Message.{
  AssistantMessage,
  AssistantMessageContent,
  UserMessage,
  UserMessageContent
}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{Format, Json, Reads, Writes}

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

    "deserialize a tool_use content block" in {
      val json =
        """    {
          |      "type": "tool_use",
          |      "id": "toolu_01A09q90qw90lq917835lq9",
          |      "name": "get_weather",
          |      "input": {"location": "San Francisco, CA", "unit": "celsius"}
          |    }""".stripMargin

      val toolUseBlock = ToolUseBlock(
        id = "toolu_01A09q90qw90lq917835lq9",
        name = "get_weather",
        input = Map(
          "location" -> "\"San Francisco, CA\"",
          "unit" -> "\"celsius\""
        )
      )
      testDeserialization[ContentBlock](json, toolUseBlock)
    }

    // TODO: add deserialization tests for:
    // 1. ToolUseBlock - success - flat content
    // 2. ToolUseBlock - success - TextBlock content
    // 3. ToolUseBlock - failure - flat content
    // 4. ToolUseBlock - failure - TextBlock content

    val expectedToolSpecJson =
      """{
        |  "name" : "get_stock_price",
        |  "description" : "Get the current stock price for a given ticker symbol.",
        |  "input_schema" : {
        |    "type" : "object",
        |    "properties" : {
        |      "ticker" : {
        |        "type" : "string",
        |        "description" : "The stock ticker symbol, e.g. AAPL for Apple Inc."
        |      }
        |    },
        |    "required" : [ "ticker" ]
        |  }
        |}""".stripMargin

    "serialize tools" in {
      val toolSpec = ToolSpec(
        name = "get_stock_price",
        description = Some("Get the current stock price for a given ticker symbol."),
        inputSchema = Map(
          "type" -> "object",
          "properties" -> Map(
            "ticker" -> Map(
              "type" -> "string",
              "description" -> "The stock ticker symbol, e.g. AAPL for Apple Inc."
            )
          ),
          "required" -> Seq("ticker")
        )
      )

      testSerialization(toolSpec, expectedToolSpecJson, Pretty)
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
        UserMessageContent(Seq(ImageBlock("base64", "image/jpeg", "/9j/4AAQSkZJRg...")))
      testCodec[Message](userMessage, expectedImageContentJson, Pretty)
    }

    val createToolMessageResponseJson =
      """{
        |  "id": "msg_01Aq9w938a90dw8q",
        |  "model": "claude-3-opus-20240229",
        |  "stop_reason": "tool_use",
        |  "role": "assistant",
        |  "content": [
        |    {
        |      "type": "text",
        |      "text": "<thinking>I need to use the get_weather, and the user wants SF, which is likely San Francisco, CA.</thinking>"
        |    },
        |    {
        |      "type": "tool_use",
        |      "id": "toolu_01A09q90qw90lq917835lq9",
        |      "name": "get_weather",
        |      "input": {"location": "San Francisco, CA", "unit": "celsius"}
        |    }
        |  ]
        |}""".stripMargin

    "deserialize tool use content block" in {
      val toolUseResponse = CreateMessageResponse(
        id = "msg_01Aq9w938a90dw8q",
        role = ChatRole.Assistant,
        content = ContentBlocks(
          Seq(
            // TODO: check, shouldn't this get to description of a tool use block?
            TextBlock(
              "<thinking>I need to use the get_weather, and the user wants SF, which is likely San Francisco, CA.</thinking>"
            ),
            ToolUseBlock(
              id = "toolu_01A09q90qw90lq917835lq9",
              name = "get_weather",
              input = Map(
                "location" -> "\"San Francisco, CA\"",
                "unit" -> "\"celsius\""
              )
            )
          )
        ),
        model = "claude-3-opus-20240229",
        stop_reason = Some("tool_use"),
        stop_sequence = None,
        usage = None
      )
      testDeserialization(createToolMessageResponseJson, toolUseResponse)

    }

  }

  private def testCodec[A](
    value: A,
    json: String,
    printMode: JsonPrintMode = Compact
  )(
    implicit format: Format[A]
  ): Unit = {
    testSerialization(value, json, printMode)
    testDeserialization(json, value)
  }

  private def testSerialization[A](
    value: A,
    json: String,
    printMode: JsonPrintMode = Compact
  )(
    implicit writes: Writes[A]
  ): Unit = {
    val jsValue = Json.toJson(value)
    val serialized = printMode match {
      case Compact => jsValue.toString()
      case Pretty  => Json.prettyPrint(jsValue)
    }
    serialized shouldBe json
  }

  private def testDeserialization[A](
    json: String,
    value: A
  )(
    implicit format: Reads[A]
  ): Unit = {
    Json.parse(json).as[A] shouldBe value
  }

}
