package io.cequence.openaiscala.gemini.domain.settings

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

object CreateChatCompletionSettingsOps {
  implicit class RichGeminiCreateChatCompletionSettings(
    settings: CreateChatCompletionSettings
  ) {
    private val SystemCacheEnabled = "system_cache_enabled"
    private val SystemCacheName = "system_cache_name"

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
  }
}
