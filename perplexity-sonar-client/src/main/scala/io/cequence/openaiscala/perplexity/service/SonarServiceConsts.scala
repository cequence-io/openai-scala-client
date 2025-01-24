package io.cequence.openaiscala.perplexity.service

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.perplexity.domain.settings.SonarCreateChatCompletionSettings
import io.cequence.openaiscala.service.ChatProviderSettings

/**
 * Constants of [[SonarService]], mostly defaults
 */
trait SonarServiceConsts {

  protected val coreUrl = ChatProviderSettings.sonar.coreUrl

  object DefaultSettings {

    val CreateChatCompletion = SonarCreateChatCompletionSettings(
      model = NonOpenAIModelId.sonar
    )
  }
}
