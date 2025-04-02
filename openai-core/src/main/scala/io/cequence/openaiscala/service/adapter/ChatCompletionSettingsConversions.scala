package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.domain.settings.GroqCreateChatCompletionSettingsOps._
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

  private val oBaseConversions = Seq(
    // max tokens
    FieldConversionDef(
      _.max_tokens.isDefined,
      settings =>
        settings.copy(
          max_tokens = None,
          extra_params =
            settings.extra_params + ("max_completion_tokens" -> settings.max_tokens.get)
        ),
      Some("O1/3 models don't support max_tokens, converting to max_completion_tokens")
    ),
    // temperature
    FieldConversionDef(
      settings => settings.temperature.isDefined && settings.temperature.get != 1,
      _.copy(temperature = Some(1d)),
      Some(
        "O1/3 models don't support temperature values other than the default of 1, converting to 1."
      ),
      warning = true
    ),
    // top_p
    FieldConversionDef(
      settings => settings.top_p.isDefined && settings.top_p.get != 1,
      _.copy(top_p = Some(1d)),
      Some(
        "O1/3 models don't support top p values other than the default of 1, converting to 1."
      ),
      warning = true
    ),
    // presence_penalty
    FieldConversionDef(
      settings => settings.presence_penalty.isDefined && settings.presence_penalty.get != 0,
      _.copy(presence_penalty = Some(0d)),
      Some(
        "O1/3 models don't support presence penalty values other than the default of 0, converting to 0."
      ),
      warning = true
    ),
    // frequency_penalty
    FieldConversionDef(
      settings => settings.frequency_penalty.isDefined && settings.frequency_penalty.get != 0,
      _.copy(frequency_penalty = Some(0d)),
      Some(
        "O1/3 models don't support frequency penalty values other than the default of 0, converting to 0."
      ),
      warning = true
    ),
    // parallel_tool_calls
    FieldConversionDef(
      settings => settings.parallel_tool_calls.isDefined,
      _.copy(parallel_tool_calls = None),
      Some(
        "O1/3 models don't support parallel tool calls, converting to None."
      ),
      warning = true
    )
  )

  private val o1PreviewConversions =
    oBaseConversions :+
      // response format type
      FieldConversionDef(
        settings =>
          settings.response_format_type.isDefined && settings.response_format_type.get != ChatCompletionResponseFormatType.text,
        _.copy(response_format_type = None),
        Some(
          "O1 (preview) models don't support json object/schema response format, converting to None."
        ),
        warning = true
      )

  val o1And3: SettingsConversion = generic(oBaseConversions)

  val o1Preview: SettingsConversion = generic(o1PreviewConversions)

  def groqConversions(
    reasoningFormat: Option[ReasoningFormat] = None
  ) = Seq(
    // max tokens
    FieldConversionDef(
      settings =>
        settings.model.endsWith(
          NonOpenAIModelId.deepseek_r1_distill_llama_70b
        ) && settings.max_tokens.isDefined,
      settings =>
        settings.copy(max_tokens = None).setMaxCompletionTokens(settings.max_tokens.get),
      Some(
        "Groq deepseek R1 model doesn't support max_tokens, converting to max_completion_tokens."
      )
    ),
    // reasoning format
    FieldConversionDef(
      settings => settings.model.endsWith(
        NonOpenAIModelId.deepseek_r1_distill_llama_70b
      ) && reasoningFormat.isDefined,
      _.setReasoningFormat(reasoningFormat.get),
      Some(
        s"Setting reasoning format '${reasoningFormat.get}' for Groq deepseek R1 model."
      )
    )
  )
}
