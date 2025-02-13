package io.cequence.openaiscala.gemini.domain.settings

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

// TODO
object CreateChatCompletionSettingsOps {
  implicit class RichGeminiCreateChatCompletionSettings(
    settings: CreateChatCompletionSettings
  ) {
    private val CacheSystemMessage = "cache_system_message"
    private val UseCache = "use_system_cache"

    def setCacheSystemMessage(flag: Boolean): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (CacheSystemMessage -> flag)
      )

    def setUseCache(name: String): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (UseCache -> name)
      )

    def geminiCacheSystemMessage: Boolean =
      settings.extra_params.get(CacheSystemMessage).map(_.toString).contains("true")

    def heminiSystemMessageCache: Option[String] =
      settings.extra_params.get(UseCache).map(_.toString)
  }
}
