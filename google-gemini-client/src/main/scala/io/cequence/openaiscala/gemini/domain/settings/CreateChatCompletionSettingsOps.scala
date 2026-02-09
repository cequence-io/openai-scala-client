package io.cequence.openaiscala.gemini.domain.settings

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.gemini.domain.Tool

object CreateChatCompletionSettingsOps {
  implicit class RichGeminiCreateChatCompletionSettings(
    settings: CreateChatCompletionSettings
  ) {
    private val SystemCacheEnabled = "system_cache_enabled"
    private val SystemCacheName = "system_cache_name"
    private val GeminiToolsParam = "gemini_tools"
    private val GeminiToolConfigParam = "gemini_tool_config"

    def enableCacheSystemMessage(flag: Boolean): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (SystemCacheEnabled -> flag)
      )

    def setSystemCacheName(name: String): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (SystemCacheName -> name)
      )

    def isCacheSystemMessageEnabled: Boolean =
      settings.extra_params.get(SystemCacheEnabled).map(_.toString).contains("true")

    def getSystemCacheName: Option[String] =
      settings.extra_params.get(SystemCacheName).map(_.toString)

    def setGeminiTools(tools: Seq[Tool]): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (GeminiToolsParam -> tools)
      )

    def setGeminiToolConfig(toolConfig: ToolConfig): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (GeminiToolConfigParam -> toolConfig)
      )

    def getGeminiTools: Option[Seq[Tool]] =
      settings.extra_params.get(GeminiToolsParam).collect {
        case tools: Seq[_] if tools.forall(_.isInstanceOf[Tool]) =>
          tools.asInstanceOf[Seq[Tool]]
      }

    def getGeminiToolConfig: Option[ToolConfig] =
      settings.extra_params.get(GeminiToolConfigParam).collect { case toolConfig: ToolConfig =>
        toolConfig
      }
  }
}
