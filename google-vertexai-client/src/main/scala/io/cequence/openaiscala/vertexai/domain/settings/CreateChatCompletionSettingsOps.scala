package io.cequence.openaiscala.vertexai.domain.settings

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.vertexai.domain.Tool

object CreateChatCompletionSettingsOps {
  implicit class RichVertexAICreateChatCompletionSettings(
    settings: CreateChatCompletionSettings
  ) {
    private val VertexAIToolsParam = "vertexai_tools"
    private val VertexAIToolConfigParam = "vertexai_tool_config"

    def setVertexAITools(tools: Seq[Tool]): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (VertexAIToolsParam -> tools)
      )

    def setVertexAIToolConfig(toolConfig: ToolConfig): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (VertexAIToolConfigParam -> toolConfig)
      )

    def getVertexAITools: Option[Seq[Tool]] =
      settings.extra_params.get(VertexAIToolsParam).collect {
        case tools: Seq[_] if tools.forall(_.isInstanceOf[Tool]) =>
          tools.asInstanceOf[Seq[Tool]]
      }

    def getVertexAIToolConfig: Option[ToolConfig] =
      settings.extra_params.get(VertexAIToolConfigParam).collect {
        case toolConfig: ToolConfig =>
          toolConfig
      }
  }
}
