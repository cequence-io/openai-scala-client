package io.cequence.openaiscala.gemini.service

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.service.ChatProviderSettings

/**
 * Constants of [[GeminiService]], mostly defaults
 */
trait GeminiServiceConsts {

  protected val coreUrl = ChatProviderSettings.geminiCoreURL

  object DefaultSettings {

    val GenerateContent = GenerateContentSettings(
      model = NonOpenAIModelId.gemini_2_0_flash
    )
  }
}
