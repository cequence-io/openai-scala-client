package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import org.slf4j.LoggerFactory

object ChatCompletionSettingsConversions {

  private val logger = LoggerFactory.getLogger(getClass)

  type SettingsConversion = CreateChatCompletionSettings => CreateChatCompletionSettings

  case class FieldConversionDef(
    doConversion: CreateChatCompletionSettings => Boolean,
    convert: CreateChatCompletionSettings => CreateChatCompletionSettings,
    loggingMessage: Option[String],
    warning: Boolean = false
  )

  def generic(
    fieldConversions: Seq[FieldConversionDef]
  ): SettingsConversion = (settings: CreateChatCompletionSettings) =>
    fieldConversions.foldLeft(settings) {
      case (acc, FieldConversionDef(isDefined, convert, loggingMessage, warning)) =>
        if (isDefined(acc)) {
          loggingMessage.foreach(message =>
            if (warning) logger.warn(message) else logger.debug(message)
          )
          convert(acc)
        } else acc
    }

  private val o1Conversions = Seq(
    // max tokens
    FieldConversionDef(
      _.max_tokens.isDefined,
      settings =>
        settings.copy(
          max_tokens = None,
          extra_params =
            settings.extra_params + ("max_completion_tokens" -> settings.max_tokens.get)
        ),
      Some("O1 models don't support max_tokens, converting to max_completion_tokens")
    ),
    // temperature
    FieldConversionDef(
      settings => settings.temperature.isDefined && settings.temperature.get != 1,
      _.copy(temperature = Some(1d)),
      Some(
        "O1 models don't support temperature values other than the default of 1, converting to 1."
      ),
      warning = true
    ),
    // top_p
    FieldConversionDef(
      settings => settings.top_p.isDefined && settings.top_p.get != 1,
      _.copy(top_p = Some(1d)),
      Some(
        "O1 models don't support top p values other than the default of 1, converting to 1."
      ),
      warning = true
    ),
    // presence_penalty
    FieldConversionDef(
      settings => settings.presence_penalty.isDefined && settings.presence_penalty.get != 0,
      _.copy(presence_penalty = Some(0d)),
      Some(
        "O1 models don't support presence penalty values other than the default of 0, converting to 0."
      ),
      warning = true
    ),
    // frequency_penalty
    FieldConversionDef(
      settings => settings.frequency_penalty.isDefined && settings.frequency_penalty.get != 0,
      _.copy(frequency_penalty = Some(0d)),
      Some(
        "O1 models don't support frequency penalty values other than the default of 0, converting to 0."
      ),
      warning = true
    ),
    // frequency_penalty
    FieldConversionDef(
      settings =>
        settings.response_format_type.isDefined && settings.response_format_type.get != ChatCompletionResponseFormatType.text,
      _.copy(response_format_type = None),
      Some(
        "O1 models don't support json object/schema response format, converting to None."
      ),
      warning = true
    )
  )

  val o1Specific: SettingsConversion = generic(o1Conversions)
}
