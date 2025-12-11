package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  ReasoningEffort
}
import io.cequence.openaiscala.domain.settings.GroqCreateChatCompletionSettingsOps._
import org.slf4j.LoggerFactory
import io.cequence.openaiscala.domain.settings.Verbosity

object ChatCompletionSettingsConversions {

  private val logger = LoggerFactory.getLogger(getClass)

  type SettingsConversion = CreateChatCompletionSettings => CreateChatCompletionSettings

  case class FieldConversionDef(
    doConversion: CreateChatCompletionSettings => Boolean,
    convert: CreateChatCompletionSettings => CreateChatCompletionSettings,
    loggingMessage: Option[CreateChatCompletionSettings => String],
    warning: Boolean = false
  )

  def generic(
    fieldConversions: Seq[FieldConversionDef]
  ): SettingsConversion = (settings: CreateChatCompletionSettings) =>
    fieldConversions.foldLeft(settings) {
      case (acc, FieldConversionDef(isDefined, convert, maybeLoggingMessage, warning)) =>
        if (isDefined(acc)) {
          maybeLoggingMessage.foreach { messageFun =>
            val message = messageFun(acc)
            if (warning) logger.warn(message) else logger.debug(message)
          }
          convert(acc)
        } else acc
    }

  object FieldConversions {
    val maxTokensToMaxCompletionTokens: FieldConversionDef = FieldConversionDef(
      _.max_tokens.isDefined,
      settings =>
        settings.copy(
          max_tokens = None,
          extra_params =
            settings.extra_params + ("max_completion_tokens" -> settings.max_tokens.get)
        ),
      Some(settings =>
        s"${settings.model} model doesn't support max_tokens, converting to max_completion_tokens"
      )
    )

    val temperatureOneOnly: FieldConversionDef = FieldConversionDef(
      settings => settings.temperature.isDefined && settings.temperature.get != 1,
      _.copy(temperature = Some(1d)),
      Some(settings =>
        s"${settings.model} model doesn't support temperature values other than the default of 1, converting to 1."
      ),
      warning = true
    )

    val topPOneOnly: FieldConversionDef = FieldConversionDef(
      settings => settings.top_p.isDefined && settings.top_p.get != 1,
      _.copy(top_p = Some(1d)),
      Some(settings =>
        s"${settings.model} model doesn't support top p values other than the default of 1, converting to 1."
      ),
      warning = true
    )

    val logProbsUnsupported: FieldConversionDef = FieldConversionDef(
      settings => settings.logprobs.isDefined && settings.logprobs.get,
      _.copy(logprobs = None),
      Some(settings =>
        s"${settings.model} model doesn't support logprobs, converting to None."
      ),
      warning = true
    )

    // Versions that only apply when reasoning_effort is not None
    val temperatureOneOnlyWithReasoning: FieldConversionDef = FieldConversionDef(
      settings =>
        settings.temperature.isDefined && settings.temperature.get != 1 &&
          settings.reasoning_effort.exists(_ != ReasoningEffort.none),
      _.copy(temperature = Some(1d)),
      Some(settings =>
        s"${settings.model} model doesn't support temperature values other than the default of 1 when reasoning_effort is set, converting to 1."
      ),
      warning = true
    )

    val topPOneOnlyWithReasoning: FieldConversionDef = FieldConversionDef(
      settings =>
        settings.top_p.isDefined && settings.top_p.get != 1 &&
          settings.reasoning_effort.exists(_ != ReasoningEffort.none),
      _.copy(top_p = Some(1d)),
      Some(settings =>
        s"${settings.model} model doesn't support top p values other than the default of 1 when reasoning_effort is set, converting to 1."
      ),
      warning = true
    )

    val logProbsUnsupportedWithReasoning: FieldConversionDef = FieldConversionDef(
      settings =>
        settings.logprobs.isDefined && settings.logprobs.get &&
          settings.reasoning_effort.exists(_ != ReasoningEffort.none),
      _.copy(logprobs = None),
      Some(settings =>
        s"${settings.model} model doesn't support logprobs when reasoning_effort is set, converting to None."
      ),
      warning = true
    )

    val presencePenaltyZeroOnly: FieldConversionDef = FieldConversionDef(
      settings => settings.presence_penalty.isDefined && settings.presence_penalty.get != 0,
      _.copy(presence_penalty = Some(0d)),
      Some(settings =>
        s"${settings.model} model doesn't support presence penalty values other than the default of 0, converting to 0."
      ),
      warning = true
    )

    val frequencyPenaltyZeroOnly: FieldConversionDef = FieldConversionDef(
      settings => settings.frequency_penalty.isDefined && settings.frequency_penalty.get != 0,
      _.copy(frequency_penalty = Some(0d)),
      Some(settings =>
        s"${settings.model} model doesn't support frequency penalty values other than the default of 0, converting to 0."
      ),
      warning = true
    )

    val parallelToolCallsUnsupported: FieldConversionDef = FieldConversionDef(
      settings => settings.parallel_tool_calls.isDefined,
      _.copy(parallel_tool_calls = None),
      Some(settings =>
        s"${settings.model} model doesn't support parallel tool calls, converting to None."
      ),
      warning = true
    )

    val verbosityMediumOnly: FieldConversionDef = FieldConversionDef(
      settings => settings.verbosity.isDefined && settings.verbosity.get != Verbosity.medium,
      _.copy(verbosity = None),
      Some(settings =>
        s"${settings.model} model doesn't support verbosity values other than 'medium', converting to None."
      ),
      warning = true
    )

    val responseFormatTypeMustBeText: FieldConversionDef = FieldConversionDef(
      settings =>
        settings.response_format_type.isDefined && settings.response_format_type.get != ChatCompletionResponseFormatType.text,
      _.copy(response_format_type = None),
      Some(settings =>
        s"${settings.model} model doesn't support json object/schema response format, converting to None."
      ),
      warning = true
    )
  }

  import FieldConversions._

  private lazy val oBaseConversions =
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnly,
      topPOneOnly,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly,
      parallelToolCallsUnsupported,
      verbosityMediumOnly
    )

  private val o1PreviewConversions =
    oBaseConversions :+ responseFormatTypeMustBeText

  val gpt5_1And2: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnlyWithReasoning,
      topPOneOnlyWithReasoning,
      logProbsUnsupportedWithReasoning,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly
    )
  )

  val gpt5: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnly,
      topPOneOnly,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly,
      logProbsUnsupported
    )
  )

  val o: SettingsConversion = generic(oBaseConversions)

  val o1Preview: SettingsConversion = generic(o1PreviewConversions)

  private def groqConversions(
    reasoningFormat: Option[ReasoningFormat] = None
  ) = Seq(
    // max tokens
    FieldConversionDef(
      settings =>
        (
          settings.model.endsWith(
            NonOpenAIModelId.deepseek_r1_distill_llama_70b
          ) || settings.model.endsWith(
            NonOpenAIModelId.deepseek_r1_distill_qwen_32b
          )
        ) && settings.max_tokens.isDefined,
      settings =>
        settings.copy(max_tokens = None).setMaxCompletionTokens(settings.max_tokens.get),
      Some(settings =>
        s"Groq deepseek R1 model ${settings.model} model doesn't support max_tokens, converting to max_completion_tokens."
      )
    ),
    // reasoning format
    FieldConversionDef(
      settings =>
        (
          settings.model.endsWith(
            NonOpenAIModelId.deepseek_r1_distill_llama_70b
          ) || settings.model.endsWith(
            NonOpenAIModelId.deepseek_r1_distill_qwen_32b
          )
        ) && reasoningFormat.isDefined,
      _.setReasoningFormat(reasoningFormat.get),
      Some(settings =>
        s"Setting reasoning format '${reasoningFormat.get}' for Groq deepseek R1 mode ${settings.model}."
      )
    )
  )

  def groq(reasoningFormat: Option[ReasoningFormat] = None): SettingsConversion =
    generic(groqConversions(reasoningFormat))
}
