package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.domain.EnumValue

object GroqCreateChatCompletionSettingsOps {
  implicit class RichCreateChatCompletionSettings(settings: CreateChatCompletionSettings) {
    private object ExtraParams {
      val reasoningFormat = "reasoning_format"
      val jsonMode = "json_mode"
      val maxCompletionTokens = "max_completion_tokens"
    }

    def setReasoningFormat(value: ReasoningFormat): CreateChatCompletionSettings =
      settings.copy(
        extra_params =
          settings.extra_params + (ExtraParams.reasoningFormat -> value.toString())
      )

    def reasoningFormat: Option[ReasoningFormat] =
      settings.extra_params.get(ExtraParams.reasoningFormat).map {
        case value: ReasoningFormat => value
        case value: String =>
          ReasoningFormat.values
            .find(_.toString() == value)
            .getOrElse(
              throw new OpenAIScalaClientException(s"Invalid reasoning format: $value")
            )
        case value: Any =>
          throw new OpenAIScalaClientException(s"Invalid reasoning format: $value")
      }

    def setJsonMode(value: Boolean): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (ExtraParams.jsonMode -> value)
      )

    def jsonMode: Option[Boolean] =
      settings.extra_params.get(ExtraParams.jsonMode).map {
        case value: Boolean => value
        case value: Any =>
          throw new OpenAIScalaClientException(s"Invalid json mode flag: $value")
      }

    def setMaxCompletionTokens(value: Int): CreateChatCompletionSettings =
      settings.copy(
        extra_params = settings.extra_params + (ExtraParams.maxCompletionTokens -> value)
      )

    def maxCompletionTokens: Option[Int] =
      settings.extra_params.get(ExtraParams.maxCompletionTokens).map {
        case value: Int => value
        case value: Any =>
          throw new OpenAIScalaClientException(s"Invalid max. completion tokens: $value")
      }
  }

  sealed trait ReasoningFormat extends EnumValue

  object ReasoningFormat {
    case object parsed extends ReasoningFormat
    case object raw extends ReasoningFormat
    case object hidden extends ReasoningFormat

    def values: Seq[ReasoningFormat] = Seq(parsed, raw, hidden)
  }
}
