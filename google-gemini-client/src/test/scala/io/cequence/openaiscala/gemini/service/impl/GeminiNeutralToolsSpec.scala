package io.cequence.openaiscala.gemini.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.ChatCompletionTool.{MCPServerTool, SkillTool}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{JsonSchema, NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.gemini.domain.ChatRole.Model
import io.cequence.openaiscala.gemini.domain.response.{
  Candidate,
  GenerateContentResponse,
  UsageMetadata,
  FinishReason => GeminiFinishReason
}
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.domain.{
  Content,
  McpServer,
  Part,
  StreamableHttpTransport,
  Tool
}
import io.cequence.openaiscala.gemini.service.GeminiService
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/** The provider-neutral MCPServerTool / SkillTool on the Gemini adapter. */
class GeminiNeutralToolsSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(50, Millis))

  private val deepwiki = MCPServerTool(
    "deepwiki",
    "https://mcp.deepwiki.com/mcp",
    authorizationToken = Some("tok"),
    headers = Map("x-api-key" -> "k"),
    timeout = Some(2.minutes)
  )

  "toGeminiMcpServersTool" should {

    "fold the servers into one mcpServers tool, the bearer as an Authorization header" in {
      OpenAIGeminiChatCompletionService.toGeminiMcpServersTool(
        Seq(deepwiki, MCPServerTool("exa", "https://mcp.exa.ai/mcp"))
      ) shouldBe Some(
        Tool.McpServers(
          Seq(
            McpServer(
              "deepwiki",
              StreamableHttpTransport(
                "https://mcp.deepwiki.com/mcp",
                headers = Some(Map("x-api-key" -> "k", "Authorization" -> "Bearer tok")),
                timeout = Some("120s")
              )
            ),
            McpServer("exa", StreamableHttpTransport("https://mcp.exa.ai/mcp"))
          )
        )
      )
    }

    "send nothing without MCP servers" in {
      OpenAIGeminiChatCompletionService.toGeminiMcpServersTool(
        Seq(FunctionTool("f", parameters = JsonSchema.Object(Nil)))
      ) shouldBe None
    }

    "refuse a SkillTool - Gemini has no skills" in {
      (the[OpenAIScalaClientException] thrownBy OpenAIGeminiChatCompletionService
        .toGeminiMcpServersTool(Seq(SkillTool("pptx")))).getMessage should include(
        "no agent skills"
      )
    }
  }

  "mcpCallRule" should {

    "count the neutral tools' servers as MCP servers" in {
      val rule = OpenAIGeminiChatCompletionService.mcpCallRule(
        CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash),
        Seq(deepwiki, FunctionTool("get_weather", parameters = JsonSchema.Object(Nil)))
      )

      rule.isMcpCall("deepwiki_ask_question") shouldBe true
      rule.isMcpCall("ask_question") shouldBe true
      rule.isMcpCall("get_weather") shouldBe false
    }
  }

  "createChatToolCompletion / createChatToolCompletionStreamed" should {

    "send the mcpServers tool alongside the function declarations" in {
      val underlying = mock(classOf[GeminiService])
      val captor = ArgumentCaptor.forClass(classOf[GenerateContentSettings])
      when(underlying.generateContent(any(), any())).thenReturn(
        Future.successful(
          GenerateContentResponse(
            candidates = Seq(
              Candidate(
                Content(Seq(Part.Text("ok")), Some(Model)),
                finishReason = Some(GeminiFinishReason.STOP),
                index = Some(0)
              )
            ),
            usageMetadata = UsageMetadata(promptTokenCount = 1, totalTokenCount = 2),
            modelVersion = "gemini-2.5-flash"
          )
        )
      )

      new OpenAIGeminiChatCompletionService(underlying)
        .createChatToolCompletion(
          Seq(UserMessage("hi")),
          Seq(deepwiki, FunctionTool("get_weather", parameters = JsonSchema.Object(Nil))),
          None,
          CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash)
        )
        .futureValue

      verify(underlying).generateContent(any(), captor.capture())
      val tools = captor.getValue.tools.getOrElse(Nil)
      tools.collect { case t: Tool.FunctionDeclarations =>
        t.functionDeclarations.map(_.name)
      }.flatten shouldBe Seq("get_weather")
      tools.collect { case t: Tool.McpServers => t.mcpServers.map(_.name) }.flatten shouldBe
        Seq("deepwiki")
    }
  }
}
