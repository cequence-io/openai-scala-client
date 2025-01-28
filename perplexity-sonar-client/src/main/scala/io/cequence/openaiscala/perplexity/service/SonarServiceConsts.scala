package io.cequence.openaiscala.perplexity.service

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.perplexity.domain.settings.SonarCreateChatCompletionSettings
import io.cequence.openaiscala.service.ChatProviderSettings

/**
 * Constants of [[SonarService]], mostly defaults
 */
trait SonarServiceConsts extends SonarConsts {

  object DefaultSettings {

    val CreateChatCompletion = SonarCreateChatCompletionSettings(
      model = NonOpenAIModelId.sonar
    )
  }
}

trait SonarConsts {
  protected val coreUrl = ChatProviderSettings.sonar.coreUrl

  protected val aHrefForCitationsParam = "a_href_for_citations"
}
