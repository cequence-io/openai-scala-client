package io.cequence.openaiscala.vertexai.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.ChatCompletionTool.{MCPServerTool, SkillTool}
import io.cequence.openaiscala.domain.JsonSchema
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Vertex AI has neither remote MCP servers nor skills - the neutral tools are refused. */
class VertexAINeutralToolsSpec extends AnyWordSpec with Matchers {

  "rejectUnsupportedTools" should {

    "let function tools through" in {
      OpenAIVertexAIChatCompletionService.rejectUnsupportedTools(
        Seq(FunctionTool("f", parameters = JsonSchema.Object(Nil)))
      )
    }

    "refuse an MCPServerTool and a SkillTool, naming them" in {
      (the[OpenAIScalaClientException] thrownBy OpenAIVertexAIChatCompletionService
        .rejectUnsupportedTools(
          Seq(MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp"))
        )).getMessage should include("MCPServerTool 'deepwiki'")

      (the[OpenAIScalaClientException] thrownBy OpenAIVertexAIChatCompletionService
        .rejectUnsupportedTools(Seq(SkillTool("pptx")))).getMessage should include(
        "SkillTool 'pptx'"
      )
    }
  }
}
