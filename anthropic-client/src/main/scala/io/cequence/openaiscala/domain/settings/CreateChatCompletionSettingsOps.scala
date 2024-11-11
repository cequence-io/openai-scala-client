package io.cequence.openaiscala.domain.settings

import scala.util.Try

object CreateChatCompletionSettingsOps {
  implicit class RichCreateChatCompletionSettings(settings: CreateChatCompletionSettings) {

    def anthropicCachedUserMessagesCount: Int =
      settings.extra_params
        .get(CreateChatCompletionSettings.AnthropicCachedUserMessagesCount)
        .flatMap(numberAsString => Try(numberAsString.toString.toInt).toOption)
        .getOrElse(0)

    def useAnthropicSystemMessagesCache: Boolean =
      settings.extra_params
        .get(CreateChatCompletionSettings.AnthropicUseSystemMessagesCache)
        .map(_.toString)
        .contains("true")
  }
}
