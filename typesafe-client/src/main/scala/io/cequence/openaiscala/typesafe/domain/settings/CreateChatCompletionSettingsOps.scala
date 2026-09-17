package io.cequence.openaiscala.typesafe.domain.settings

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

/**
 * TypeSafe-specific knobs for the OpenAI chat-completion adapter, carried in `extra_params`.
 */
object CreateChatCompletionSettingsOps {

  /** The noul probability at or above which a boolean property is `true` (default 0.5). */
  val NoulThresholdParam = "typesafe_noul_threshold"

  val DefaultNoulThreshold = 0.5

  implicit class TypeSafeSettingsOps(settings: CreateChatCompletionSettings) {

    def setTypeSafeNoulThreshold(threshold: Double): CreateChatCompletionSettings = {
      require(threshold >= 0 && threshold <= 1, "The noul threshold must be within 0..1.")
      settings.copy(extra_params = settings.extra_params + (NoulThresholdParam -> threshold))
    }

    def typeSafeNoulThreshold: Double =
      settings.extra_params.get(NoulThresholdParam) match {
        case Some(d: Double) => d
        case Some(other)     => other.toString.toDouble
        case None            => DefaultNoulThreshold
      }
  }
}
