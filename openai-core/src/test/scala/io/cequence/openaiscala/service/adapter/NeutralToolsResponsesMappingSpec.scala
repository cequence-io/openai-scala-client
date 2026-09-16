package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.ChatCompletionTool.{
  MCPServerTool,
  SkillSource,
  SkillTool
}
import io.cequence.openaiscala.domain.responsesapi.tools.JsonFormats.toolFormat
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.{
  MCPAllowedTools,
  MCPRequireApproval,
  MCPTool
}
import io.cequence.openaiscala.domain.responsesapi.tools.{
  ShellEnvironment,
  ShellSkill,
  ShellTool,
  Tool,
  WebSearchTool,
  FunctionTool => ResponsesFunctionTool
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.{ChatCompletionTool, JsonSchema, ModelId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.concurrent.duration._

/**
 * The provider-neutral [[MCPServerTool]] / [[SkillTool]] on OpenAI: they force the Responses
 * API and map onto its `mcp` and hosted `shell` tools.
 */
class NeutralToolsResponsesMappingSpec extends AnyWordSpec with Matchers {

  private val weather = FunctionTool(
    name = "get_weather",
    parameters = JsonSchema.Object(
      properties = Seq("location" -> JsonSchema.String()),
      required = Seq("location")
    )
  )

  private val deepwiki = MCPServerTool(
    name = "deepwiki",
    url = "https://mcp.deepwiki.com/mcp",
    authorizationToken = Some("tok"),
    headers = Map("x-api-key" -> "k"),
    allowedTools = Seq("ask_question"),
    description = Some("wiki"),
    timeout = Some(2.minutes)
  )

  private val settings = CreateChatCompletionSettings(ModelId.gpt_5_4)

  "chatToolsRequireResponsesAPI(model, tools)" should {

    "stay with the chat completions API for function tools on a chat-capable model" in {
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        ModelId.gpt_5_4,
        Seq(weather)
      ) shouldBe false
    }

    "route to the Responses API for a neutral MCP server or skill, whatever the model" in {
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        ModelId.gpt_5_4,
        Seq(weather, deepwiki)
      ) shouldBe true
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        ModelId.gpt_5_4,
        Seq(SkillTool("sk_1"))
      ) shouldBe true
    }

    "keep the GPT-6 rule" in {
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        ModelId.gpt_6_astra,
        Seq(weather)
      ) shouldBe true
    }
  }

  "toResponsesTools" should {

    "map an MCPServerTool onto the Responses mcp tool, approval never required by default" in {
      val tools =
        OpenAIResponsesChatCompletionService.toResponsesTools(Seq(deepwiki), settings)

      tools shouldBe Seq(
        MCPTool(
          serverLabel = "deepwiki",
          serverUrl = Some("https://mcp.deepwiki.com/mcp"),
          authorization = Some("tok"),
          headers = Some(Map("x-api-key" -> "k")),
          allowedTools = Some(MCPAllowedTools.ToolNames(Seq("ask_question"))),
          requireApproval = Some(MCPRequireApproval.Setting.Never),
          serverDescription = Some("wiki")
        )
      )
    }

    "ask for approval only when requested" in {
      val tools = OpenAIResponsesChatCompletionService.toResponsesTools(
        Seq(deepwiki.copy(requireApproval = true, headers = Map.empty, allowedTools = Nil)),
        settings
      )

      tools.head.asInstanceOf[MCPTool].requireApproval shouldBe
        Some(MCPRequireApproval.Setting.Always)
      tools.head.asInstanceOf[MCPTool].headers shouldBe None
      tools.head.asInstanceOf[MCPTool].allowedTools shouldBe None
    }

    "load every SkillTool into ONE hosted shell tool, after the function and MCP tools" in {
      val tools = OpenAIResponsesChatCompletionService.toResponsesTools(
        Seq(
          SkillTool("sk_1", version = Some("2")),
          weather,
          SkillTool("sk_2", version = Some("latest"), source = SkillSource.Provider),
          deepwiki
        ),
        settings.setResponsesTools(Seq(WebSearchTool()))
      )

      tools.map(_.getClass.getSimpleName) shouldBe
        Seq("FunctionTool", "MCPTool", "ShellTool", "WebSearchTool")
      tools.head shouldBe ResponsesFunctionTool("get_weather", weather.parameters, false, None)
      tools(2) shouldBe ShellTool(
        ShellEnvironment.ContainerAuto(
          skills = Seq(
            ShellSkill.Reference("sk_1", Some("2")),
            ShellSkill.Reference("sk_2", Some("latest"))
          )
        )
      )
    }

    "send no shell tool without skills" in {
      OpenAIResponsesChatCompletionService.toResponsesTools(Seq(weather), settings).collect {
        case t: ShellTool => t
      } shouldBe empty
    }
  }

  "ShellTool JSON" should {

    "match the documented request shape and round-trip" in {
      val tool: Tool = ShellTool(
        ShellEnvironment.ContainerAuto(
          skills = Seq(
            ShellSkill.Reference("sk_1", Some("2")),
            ShellSkill.Reference("sk_2", Some("latest")),
            ShellSkill.Inline("my_skill", "does things", "UEsDBA==")
          ),
          fileIds = Seq("file_1")
        )
      )

      val json = Json.toJson(tool)
      json shouldBe Json.parse(
        """{"type":"shell","environment":{"type":"container_auto","skills":[
          |{"type":"skill_reference","skill_id":"sk_1","version":2},
          |{"type":"skill_reference","skill_id":"sk_2","version":"latest"},
          |{"type":"inline","name":"my_skill","description":"does things","source":{"type":"base64","media_type":"application/zip","data":"UEsDBA=="}}
          |],"file_ids":["file_1"]}}""".stripMargin
      )
      json.as[Tool] shouldBe tool

      Json.toJson(ShellTool(): Tool) shouldBe
        Json.parse("""{"type":"shell","environment":{"type":"container_auto"}}""")
      Json.toJson(ShellTool(ShellEnvironment.ContainerId("cntr_1")): Tool) shouldBe
        Json.parse("""{"type":"shell","environment":{"type":"container","id":"cntr_1"}}""")
    }
  }

  "the neutral tools' chat-completion writes" should {

    "render descriptively without leaking secrets" in {
      import io.cequence.openaiscala.JsonFormats.chatCompletionToolWrites

      val json = Json.toJson(deepwiki: ChatCompletionTool)
      (json \ "type").as[String] shouldBe "mcp_server"
      (json \ "authorization").as[String] shouldBe "***"
      json.toString should not include "tok"
      (json \ "headers").as[Seq[String]] shouldBe Seq("x-api-key")
      json.toString should not include "\"k\""

      (Json.toJson(
        SkillTool("sk_1", source = SkillSource.Provider): ChatCompletionTool
      ) \ "source").as[String] shouldBe "provider"
    }
  }
}
