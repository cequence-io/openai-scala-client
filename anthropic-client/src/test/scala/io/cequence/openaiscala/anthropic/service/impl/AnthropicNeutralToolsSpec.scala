package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.domain.skills.{Container, SkillParams, SkillSource}
import io.cequence.openaiscala.anthropic.domain.tools.{
  CodeExecutionTool,
  CustomTool,
  MCPServerURLDefinition,
  MCPToolConfiguration,
  Tool
}
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.ChatCompletionTool.{MCPServerTool, SkillTool}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{
  ChatCompletionTool,
  JsonSchema,
  NonOpenAIModelId,
  UserMessage
}
import org.mockito.Mockito.mock
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext

/** The provider-neutral MCPServerTool / SkillTool on the Anthropic adapter. */
class AnthropicNeutralToolsSpec extends AnyWordSpec with Matchers {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val adapter = new OpenAIAnthropicChatCompletionService(
    mock(classOf[AnthropicService])
  )

  private val settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_sonnet_5)

  private def request(
    tools: Seq[ChatCompletionTool],
    settings: CreateChatCompletionSettings = settings
  ) = adapter.toAnthropicToolRequest(Seq(UserMessage("hi")), tools, None, settings)._2

  "MCPServerTool" should {

    "become an mcp_servers entry with the bearer token and the allowed tools" in {
      val out = request(
        Seq(
          MCPServerTool(
            "deepwiki",
            "https://mcp.deepwiki.com/mcp",
            authorizationToken = Some("tok"),
            allowedTools = Seq("ask_question")
          )
        )
      )

      out.mcp_servers shouldBe Seq(
        MCPServerURLDefinition(
          "deepwiki",
          "https://mcp.deepwiki.com/mcp",
          Some("tok"),
          Some(MCPToolConfiguration(Seq("ask_question"), Some(true)))
        )
      )
      out.tools shouldBe empty
    }

    "leave the tool configuration out when all tools are allowed, and merge with setAnthropicMcpServers" in {
      val fromSettings = MCPServerURLDefinition("exa", "https://mcp.exa.ai/mcp")
      val out = request(
        Seq(MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp")),
        settings.setAnthropicMcpServers(Seq(fromSettings))
      )

      out.mcp_servers shouldBe Seq(
        fromSettings,
        MCPServerURLDefinition("deepwiki", "https://mcp.deepwiki.com/mcp", None, None)
      )
    }

    "be refused with custom headers, which the connector cannot send" in {
      val e = the[OpenAIScalaClientException] thrownBy request(
        Seq(MCPServerTool("exa", "https://mcp.exa.ai/mcp", headers = Map("x-api-key" -> "k")))
      )

      e.getMessage should include("x-api-key")
      e.getMessage should include("bearer token only")
    }
  }

  "SkillTool" should {

    "load the skills into the container and add the code execution tool" in {
      val out = request(
        Seq(
          SkillTool("pptx", Some("latest"), ChatCompletionTool.SkillSource.Provider),
          SkillTool("skill_01", None, ChatCompletionTool.SkillSource.Custom),
          FunctionTool(
            "get_weather",
            parameters = JsonSchema.Object(properties = Seq("location" -> JsonSchema.String()))
          )
        )
      )

      out.container shouldBe Some(
        Container(skills =
          Seq(
            SkillParams("pptx", SkillSource.anthropic, Some("latest")),
            SkillParams("skill_01", SkillSource.custom, None)
          )
        )
      )
      out.tools.collect { case t: CustomTool => t.name } shouldBe Seq("get_weather")
      out.tools.collect { case t: CodeExecutionTool => t } should have size 1
      out.tool_choice shouldBe defined
    }

    "not duplicate a code execution tool the caller already set" in {
      val out = request(
        Seq(SkillTool("pptx", source = ChatCompletionTool.SkillSource.Provider)),
        settings.setAnthropicTools(Seq(Tool.codeExecution()))
      )

      out.tools.collect { case t: CodeExecutionTool => t } should have size 1
    }

    "leave the container untouched without skills" in {
      request(
        Seq(MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp"))
      ).container shouldBe None
    }
  }
}
