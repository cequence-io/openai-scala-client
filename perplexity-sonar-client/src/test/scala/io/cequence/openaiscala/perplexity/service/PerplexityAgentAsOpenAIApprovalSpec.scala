package io.cequence.openaiscala.perplexity.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.ChatCompletionTool.MCPServerTool
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.{MCPRequireApproval, MCPTool}
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

/**
 * Perplexity's `/v1/responses` runs every MCP call (`require_approval` is ignored), so its
 * OpenAI adapter refuses approval before any I/O rather than running calls unapproved or
 * starting a fresh run for a resume.
 */
class PerplexityAgentAsOpenAIApprovalSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(Span(10, Seconds))

  // an unroutable base URL: nothing may reach the network
  private val service = SonarServiceFactory.agentAsOpenAI("k", Some("http://127.0.0.1:1/"))
  private val settings = CreateChatCompletionSettings("openai/gpt-5.4-mini")
  private val messages = Seq(UserMessage("hi"))

  override protected def afterAll(): Unit = service.close()

  "agentAsOpenAI" should {

    "refuse an MCPServerTool that requires approval" in {
      service
        .createChatToolCompletion(
          messages,
          Seq(
            MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp", requireApproval = true)
          ),
          None,
          settings
        )
        .failed
        .futureValue
        .asInstanceOf[OpenAIScalaClientException]
        .getMessage should include("requireApproval")
    }

    "refuse a raw MCPTool that requires approval, but send one left at the default" in {
      def raw(requireApproval: Option[MCPRequireApproval]) =
        settings.setResponsesTools(
          Seq(
            MCPTool(
              "ops",
              serverUrl = Some("https://x/mcp"),
              requireApproval = requireApproval
            )
          )
        )

      service
        .createChatToolCompletion(
          messages,
          Nil,
          None,
          raw(Some(MCPRequireApproval.Setting.Always))
        )
        .failed
        .futureValue
        .getMessage should include("MCPTool 'ops' requires approval")

      // unset = the backend's default (Perplexity runs every call): not refused - the call
      // goes out (and fails on the unroutable URL)
      service
        .createChatToolCompletion(messages, Nil, None, raw(None))
        .failed
        .futureValue
        .getMessage should not include "cannot pause for tool approval"
    }

    "refuse approval decisions" in {
      val request =
        ChatChunk.ToolApprovalRequest("mcpr_1", "t", "{}", Some("s"), "resp_1", Json.obj())
      service
        .createChatToolCompletion(
          messages,
          Seq(MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp")),
          None,
          settings.setToolApprovalDecisions(Seq(request.approve))
        )
        .failed
        .futureValue
        .getMessage should include("cannot resume a run paused for tool approval")
    }
  }
}
