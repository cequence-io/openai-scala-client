package io.cequence.openaiscala.vertexai.domain.settings

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.vertexai.domain.Tool

object CreateChatCompletionSettingsOps {
  implicit class RichVertexAICreateChatCompletionSettings(
    settings: CreateChatCompletionSettings
  ) {
    private val VertexAIToolsParam = "vertexai_tools"
    private val VertexAIToolConfigParam = "vertexai_tool_config"
    private val VertexAIIncludeThoughtsParam = "vertexai_include_thoughts"

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

    /**
     * Whether the model's thoughts are requested (`thinkingConfig.includeThoughts`) on the
     * typed stream (`createChatToolCompletionStreamed`), so that `ChatChunk.Thinking` chunks
     * arrive. Defaults to `true` there; the legacy chunk stream never requests thoughts.
     */
    def setVertexAIIncludeThoughts(flag: Boolean = true): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (VertexAIIncludeThoughtsParam -> flag)
      )

    def vertexAIIncludeThoughts: Option[Boolean] =
      settings.extra_params.get(VertexAIIncludeThoughtsParam).map(_.toString == "true")
  }
}
