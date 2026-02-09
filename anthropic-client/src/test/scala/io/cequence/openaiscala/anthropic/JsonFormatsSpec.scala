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
import io.cequence.openaiscala.anthropic.domain.tools.{MCPToolConfig, MCPToolset, Tool}
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
        """{"role":"user","content":[{"text":"Hello, world!","type":"text"},{"text":"How are you?","type":"text"}]}"""
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
        """{"role":"assistant","content":[{"text":"Hello, world!","type":"text"},{"text":"How are you?","type":"text"}]}"""
      testCodec[Message](assistantMessage, json)
    }

    val expectedImageContentJson =
      """{
        |  "role" : "user",
        |  "content" : [ {
        |    "source" : {
        |      "type" : "base64",
        |      "media_type" : "image/jpeg",
        |      "data" : "/9j/4AAQSkZJRg..."
        |    },
        |    "type" : "image"
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
              ContentBlockBase(TextBlock("Hello, world!"), Some(Ephemeral())),
              ContentBlockBase(TextBlock("How are you?"))
            )
          )
        val json =
          """{"role":"user","content":[{"text":"Hello, world!","type":"text","cache_control":{"type":"ephemeral"}},{"text":"How are you?","type":"text"}]}"""
        testCodec[Message](userMessage, json)
      }

      "serialize and deserialize arbitrary (second) user message with caching" in {
        val userMessage =
          UserMessageContent(
            Seq(
              ContentBlockBase(TextBlock("Hello, world!")),
              ContentBlockBase(TextBlock("How are you?"), Some(Ephemeral()))
            )
          )
        val json =
          """{"role":"user","content":[{"text":"Hello, world!","type":"text"},{"text":"How are you?","type":"text","cache_control":{"type":"ephemeral"}}]}"""
        testCodec[Message](userMessage, json)
      }

      "serialize and deserialize arbitrary (first) image content with caching" in {
        val userMessage =
          UserMessageContent(
            Seq(
              MediaBlock.jpeg("/9j/4AAQSkZJRg...", Some(Ephemeral())),
              ContentBlockBase(TextBlock("How are you?"))
            )
          )

        val imageJson =
          """{"source":{"type":"base64","media_type":"image/jpeg","data":"/9j/4AAQSkZJRg..."},"type":"image","cache_control":{"type":"ephemeral"}}""".stripMargin
        val json =
          s"""{"role":"user","content":[$imageJson,{"text":"How are you?","type":"text"}]}"""
        testCodec[Message](userMessage, json)
      }
    }

    // TEST MCP TOOLSET
    "serialize and deserialize MCPToolset" should {

      "serialize MCPToolConfig with all fields" in {
        val config = MCPToolConfig(enabled = Some(true), deferLoading = Some(true))
        val json = """{"enabled":true,"defer_loading":true}"""
        testCodec[MCPToolConfig](config, json)
      }

      "serialize MCPToolConfig with only enabled" in {
        val config = MCPToolConfig(enabled = Some(false))
        val json = """{"enabled":false}"""
        testCodec[MCPToolConfig](config, json)
      }

      "deserialize MCPToolConfig with defaults from empty JSON" in {
        val json = """{}"""
        val parsed = Json.parse(json).as[MCPToolConfig]
        parsed shouldBe MCPToolConfig()
        parsed.enabled shouldBe None
        parsed.deferLoading shouldBe None
      }

      "serialize MCPToolset with only mcp_server_name (defaults)" in {
        val toolset = MCPToolset(mcpServerName = "my-mcp")
        val json = """{"mcp_server_name":"my-mcp","type":"mcp_toolset"}"""
        testCodec[MCPToolset](toolset, json)
      }

      "serialize MCPToolset with default_config" in {
        val toolset = MCPToolset(
          mcpServerName = "google-calendar-mcp",
          defaultConfig = Some(MCPToolConfig(deferLoading = Some(true)))
        )
        val json =
          """{"mcp_server_name":"google-calendar-mcp","default_config":{"defer_loading":true},"type":"mcp_toolset"}"""
        testCodec[MCPToolset](toolset, json)
      }

      "serialize MCPToolset with configs overrides" in {
        val toolset = MCPToolset(
          mcpServerName = "google-calendar-mcp",
          defaultConfig = Some(MCPToolConfig(deferLoading = Some(true))),
          configs = Map(
            "search_events" -> MCPToolConfig(enabled = Some(false))
          )
        )
        val json =
          """{"mcp_server_name":"google-calendar-mcp","default_config":{"defer_loading":true},"configs":{"search_events":{"enabled":false}},"type":"mcp_toolset"}"""
        testCodec[MCPToolset](toolset, json)
      }

      "deserialize MCPToolset with defaults from minimal JSON" in {
        val json = """{"mcp_server_name":"my-mcp","type":"mcp_toolset"}"""
        val parsed = Json.parse(json).as[MCPToolset]
        parsed.mcpServerName shouldBe "my-mcp"
        parsed.defaultConfig shouldBe None
        parsed.configs shouldBe Map.empty
        parsed.cacheControl shouldBe None
      }

      "serialize and deserialize MCPToolset as Tool" in {
        val toolset: Tool = MCPToolset(mcpServerName = "my-mcp")
        val jsValue = Json.toJson(toolset)
        (jsValue \ "type").as[String] shouldBe "mcp_toolset"
        (jsValue \ "mcp_server_name").as[String] shouldBe "my-mcp"

        val parsed = jsValue.as[Tool]
        parsed shouldBe a[MCPToolset]
        parsed.asInstanceOf[MCPToolset].mcpServerName shouldBe "my-mcp"
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
