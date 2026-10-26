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
    private val GeminiIncludeThoughts = "gemini_include_thoughts"

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

    /**
     * Whether thought summaries (`Part.Text(thought = true)`) should be requested. The typed
     * stream (`createChatToolCompletionStreamed`) defaults to `true` whenever a thinking
     * config is derived from `reasoning_effort`; the sync calls ask for them only when set to
     * `true` - they come back as the reply's reasoning (`response.reasoningMessage`), which
     * the next round sends back. Set to `true`, every call asks a model that thinks (Gemini
     * 2.5, 3.x and 4 do by default) with or without a `reasoning_effort`; a model that takes
     * no thinking configuration (Gemini 2.0, Gemma, `gemini-2.5-flash-image`) is asked
     * nothing.
     */
    def setGeminiIncludeThoughts(flag: Boolean = true): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (GeminiIncludeThoughts -> flag)
      )

    def geminiIncludeThoughts: Option[Boolean] =
      settings.extra_params.get(GeminiIncludeThoughts).map(_.toString == "true")

    def getGeminiToolConfig: Option[ToolConfig] =
      settings.extra_params.get(GeminiToolConfigParam).collect { case toolConfig: ToolConfig =>
        toolConfig
      }
  }
}
