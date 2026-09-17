package io.cequence.openaiscala.typesafe.domain.settings

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import scala.util.Try

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

    /**
     * The threshold, or [[DefaultNoulThreshold]]; a value that is not a number within 0..1
     * (only possible by writing `extra_params` directly) fails with an
     * `OpenAIScalaClientException`, like every other misuse of the adapter.
     */
    def typeSafeNoulThreshold: Double =
      settings.extra_params.get(NoulThresholdParam) match {
        case None => DefaultNoulThreshold
        case Some(value) =>
          val parsed = value match {
            case n: java.lang.Number => Some(n.doubleValue)
            case s: String           => Try(s.trim.toDouble).toOption
            case _                   => None
          }
          parsed
            .filter(d => d >= 0 && d <= 1)
            .getOrElse(
              throw new OpenAIScalaClientException(
                s"$NoulThresholdParam must be a number within 0..1, got '$value'."
              )
            )
      }
  }
}
