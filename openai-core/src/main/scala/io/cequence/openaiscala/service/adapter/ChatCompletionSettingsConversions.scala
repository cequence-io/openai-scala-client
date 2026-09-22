package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{AssistantTool, ChatCompletionTool}

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

  /**
   * Whether function tools for this model are accepted only on the Responses API (GPT-6 Astra
   * rejects them on the chat completions API outright), so tool completions must be routed
   * through the Responses API. GPT-6 Sol/Luna accept them there with reasoning_effort 'none'
   * (see [[gpt5_6ChatTools]]).
   */
  def chatToolsRequireResponsesAPI(model: String): Boolean =
    isGpt6Astra(model)

  /**
   * GPT-6 Astra - the only GPT-6 tier that rejects reasoning_effort 'none' and function tools
   * on the chat completions API (Sol/Luna follow the GPT-5.6 rules).
   */
  def isGpt6Astra(model: String): Boolean =
    canonicalOpenAIModel(model).startsWith("gpt-6-astra")

  /**
   * [[chatToolsRequireResponsesAPI]] for a concrete tool list: besides the model rule, the
   * provider-neutral [[io.cequence.openaiscala.domain.ChatCompletionTool.MCPServerTool]] and
   * [[io.cequence.openaiscala.domain.ChatCompletionTool.SkillTool]] exist on OpenAI only as
   * Responses API tools (`mcp`, the hosted `shell`), whatever the model.
   */
  def chatToolsRequireResponsesAPI(
    model: String,
    tools: Seq[ChatCompletionTool]
  ): Boolean =
    chatToolsRequireResponsesAPI(model) || tools.exists {
      case _: AssistantTool.FunctionTool => false
      case _                             => true
    }

  /**
   * Whether function tools for this model are accepted on the chat completions API only with
   * reasoning_effort 'none' (GPT-5.6 and GPT-6 Sol/Luna - see [[gpt5_6ChatTools]]), so any
   * reasoning is lost there.
   */
  def chatToolsForceNoReasoning(model: String): Boolean = {
    val bare = canonicalOpenAIModel(model)
    bare.startsWith("gpt-5.6") || (bare.startsWith("gpt-6") && !isGpt6Astra(model))
  }

  /**
   * Whether a tool completion SHOULD go through the Responses API when the service can reach
   * it: whenever it must ([[chatToolsRequireResponsesAPI]]), and also when the chat
   * completions API would force reasoning_effort to 'none' ([[chatToolsForceNoReasoning]])
   * while the caller did not ask for 'none' - the Responses API keeps the requested (or the
   * model's default) reasoning with tools. A chat-only service falls back to chat completions
   * with 'none'.
   */
  def chatToolsPreferResponsesAPI(
    settings: CreateChatCompletionSettings,
    tools: Seq[ChatCompletionTool]
  ): Boolean =
    tools.nonEmpty && (
      chatToolsRequireResponsesAPI(settings.model, tools) ||
        (chatToolsForceNoReasoning(settings.model) &&
          !settings.reasoning_effort.contains(ReasoningEffort.none))
    )

  // Amazon Bedrock serves OpenAI models under a provider prefix, optionally behind a
  // cross-region inference profile: `openai.gpt-5.6-luna`, `us.openai.gpt-6-astra`,
  // `global.openai.gpt-5.6-sol`. The per-model parameter rules key on the bare id.
  private val bedrockOpenAIPrefix = "^(?:[a-z0-9-]+\\.)*openai\\.".r

  /**
   * The bare OpenAI model id behind a Bedrock provider / inference-profile prefix (e.g.
   * `global.openai.gpt-5.6-luna` -> `gpt-5.6-luna`); any other id is returned unchanged. Use
   * it wherever a conversion or routing rule is keyed on the model id, so the rules apply on
   * Bedrock too.
   */
  def canonicalOpenAIModel(model: String): String =
    bedrockOpenAIPrefix.replaceFirstIn(model, "")

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

    val reasoningEffortMediumOnly: FieldConversionDef = FieldConversionDef(
      settings =>
        settings.reasoning_effort.isDefined && !settings.reasoning_effort
          .contains(ReasoningEffort.medium),
      _.copy(reasoning_effort = None),
      Some(settings =>
        s"${settings.model} model doesn't support reasoning_effort values other than 'medium', converting to None (model default)."
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

    val presencePenaltyZeroOnlyWithReasoning: FieldConversionDef = FieldConversionDef(
      settings =>
        settings.presence_penalty.isDefined && settings.presence_penalty.get != 0 &&
          settings.reasoning_effort.exists(_ != ReasoningEffort.none),
      _.copy(presence_penalty = Some(0d)),
      Some(settings =>
        s"${settings.model} model doesn't support presence penalty values other than the default of 0 when reasoning_effort is set, converting to 0."
      ),
      warning = true
    )

    val frequencyPenaltyZeroOnlyWithReasoning: FieldConversionDef = FieldConversionDef(
      settings =>
        settings.frequency_penalty.isDefined && settings.frequency_penalty.get != 0 &&
          settings.reasoning_effort.exists(_ != ReasoningEffort.none),
      _.copy(frequency_penalty = Some(0d)),
      Some(settings =>
        s"${settings.model} model doesn't support frequency penalty values other than the default of 0 when reasoning_effort is set, converting to 0."
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

    // 'max' is Responses-API-only on GPT-5.6; the chat completions API rejects it with a 400,
    // so downgrade to the highest chat-completions-supported effort.
    val reasoningEffortMaxToXHigh: FieldConversionDef = FieldConversionDef(
      settings => settings.reasoning_effort.contains(ReasoningEffort.max),
      _.copy(reasoning_effort = Some(ReasoningEffort.xhigh)),
      Some(settings =>
        s"${settings.model} model doesn't support reasoning_effort 'max' on the chat completions API (Responses API only), downgrading to 'xhigh'."
      ),
      warning = true
    )

    val reasoningEffortMinimalToLow: FieldConversionDef = FieldConversionDef(
      settings => settings.reasoning_effort.contains(ReasoningEffort.minimal),
      _.copy(reasoning_effort = Some(ReasoningEffort.low)),
      Some(settings =>
        s"${settings.model} model doesn't support reasoning_effort 'minimal', converting to 'low'."
      ),
      warning = true
    )

    val reasoningEffortNoneToLow: FieldConversionDef = FieldConversionDef(
      settings => settings.reasoning_effort.contains(ReasoningEffort.none),
      _.copy(reasoning_effort = Some(ReasoningEffort.low)),
      Some(settings =>
        s"${settings.model} model doesn't support reasoning_effort 'none', converting to 'low'."
      ),
      warning = true
    )

    // Function tools on the chat completions API reject any explicit reasoning_effort on
    // GPT-5.5 (the model default works) - see the chat-tool conversions below.
    val reasoningEffortUnsupportedWithTools: FieldConversionDef = FieldConversionDef(
      settings => settings.reasoning_effort.isDefined,
      _.copy(reasoning_effort = None),
      Some(settings =>
        s"${settings.model} model doesn't support an explicit reasoning_effort together with function tools on the chat completions API, converting to None (model default)."
      ),
      warning = true
    )

    // Function tools on the chat completions API require reasoning_effort = 'none' on GPT-5.6
    // (any other value, or omitting it, is rejected with a 400).
    val reasoningEffortNoneRequiredWithTools: FieldConversionDef = FieldConversionDef(
      settings => !settings.reasoning_effort.contains(ReasoningEffort.none),
      _.copy(reasoning_effort = Some(ReasoningEffort.none)),
      Some(settings =>
        s"${settings.model} model supports function tools on the chat completions API only with reasoning_effort 'none', converting to 'none' (use the Responses API to keep reasoning with tools)."
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

  val gpt5_1: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnlyWithReasoning,
      topPOneOnlyWithReasoning,
      logProbsUnsupportedWithReasoning,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly
    )
  )

  val gpt5_2: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnlyWithReasoning,
      topPOneOnlyWithReasoning,
      logProbsUnsupportedWithReasoning,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly
    )
  )

  val gpt5_3: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnly,
      topPOneOnly,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly,
      logProbsUnsupported
    )
  )

  val gpt5_4: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnlyWithReasoning,
      topPOneOnlyWithReasoning,
      presencePenaltyZeroOnlyWithReasoning,
      frequencyPenaltyZeroOnlyWithReasoning,
      logProbsUnsupported
    )
  )

  // GPT-5.5 restricts all sampling params unconditionally unlike 5.4 which only restricts them when reasoning_effort is set.
  val gpt5_5: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnly,
      topPOneOnly,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly,
      logProbsUnsupported
    )
  )

  // GPT-5.6 (Sol/Terra/Luna) is reasoning-first; restrict all sampling params unconditionally like 5.5.
  // Verified against the live API 2026-07-11: temperature/top_p/presence_penalty/frequency_penalty/logprobs
  // all return 400 for every tier, and max_tokens must be sent as max_completion_tokens.
  // reasoning_effort on chat completions supports none/low/medium/high/xhigh only - 'max' is
  // Responses-API-only and 'minimal' is rejected by both APIs, so downgrade both here.
  val gpt5_6: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnly,
      topPOneOnly,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly,
      logProbsUnsupported,
      reasoningEffortMaxToXHigh,
      reasoningEffortMinimalToLow
    )
  )

  // GPT-6 Astra is reasoning-first like GPT-5.6. Verified against the live API 2026-09-05 (and
  // unchanged on 2026-09-22):
  // temperature/top_p/presence_penalty/frequency_penalty/logprobs all return 400, max_tokens
  // must be sent as max_completion_tokens, and reasoning_effort on chat completions accepts
  // only low/medium/high/xhigh - 'max' is Responses-API-only, while 'minimal' AND 'none' are
  // rejected by both APIs (unlike GPT-5.6, which still accepts 'none' on chat completions).
  val gpt6: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnly,
      topPOneOnly,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly,
      logProbsUnsupported,
      reasoningEffortMaxToXHigh,
      reasoningEffortMinimalToLow,
      reasoningEffortNoneToLow
    )
  )

  // GPT-6 Sol/Luna keep the GPT-5.6 rules exactly (live-verified 2026-09-22): sampling params
  // and logprobs are 400s, max_tokens must be max_completion_tokens, reasoning_effort accepts
  // none/low/medium/high/xhigh on chat completions ('max' is Responses-API-only, 'minimal' is
  // rejected by both APIs).
  val gpt6SolLuna: SettingsConversion = gpt5_6

  // Function tools on the CHAT COMPLETIONS API (createChatToolCompletion) - verified live
  // 2026-09-05: GPT-5.4 and older accept tools with any reasoning_effort; GPT-5.5 rejects an
  // explicit reasoning_effort when tools are present; GPT-5.6 and GPT-6 Sol/Luna (2026-09-22)
  // require reasoning_effort 'none' with tools; GPT-6 Astra requires 'none' with tools but
  // rejects 'none' altogether, so on Astra function tools are Responses-API-only
  // (OpenAIChatCompletionServiceImpl routes them there).
  val gpt5_5ChatTools: SettingsConversion = generic(Seq(reasoningEffortUnsupportedWithTools))

  val gpt5_6ChatTools: SettingsConversion = generic(Seq(reasoningEffortNoneRequiredWithTools))

  /**
   * `reasoning_effort` for the RESPONSES API, where the chat-completions conversions above
   * don't apply. Live-verified 2026-09-22: GPT-5.6 and GPT-6 accept 'max' there (so it is
   * kept) but reject 'minimal' (-> 'low'); GPT-6 Astra also rejects 'none' (-> 'low'). Other
   * models are passed through unchanged.
   */
  def responsesReasoningEffort(
    model: String,
    effort: ReasoningEffort
  ): ReasoningEffort = {
    val bare = canonicalOpenAIModel(model)
    val gpt5_6OrGpt6 = bare.startsWith("gpt-5.6") || bare.startsWith("gpt-6")

    effort match {
      case ReasoningEffort.minimal if gpt5_6OrGpt6 =>
        logger.warn(
          s"$model model doesn't support reasoning_effort 'minimal' on the Responses API, converting to 'low'."
        )
        ReasoningEffort.low
      case ReasoningEffort.none if isGpt6Astra(model) =>
        logger.warn(
          s"$model model doesn't support reasoning_effort 'none', converting to 'low'."
        )
        ReasoningEffort.low
      case other => other
    }
  }

  /**
   * Whether the RESPONSES API rejects `temperature` / `top_p` other than the default and
   * `top_logprobs` for this model - live-verified 2026-09-22 on GPT-5.6 and GPT-6 (Astra, Sol,
   * Luna): "Unsupported parameter" / "logprobs are not supported with reasoning models".
   */
  def responsesSamplingUnsupported(model: String): Boolean = {
    val bare = canonicalOpenAIModel(model)
    bare.startsWith("gpt-5.6") || bare.startsWith("gpt-6")
  }

  // 'chat-latest' is a rolling ChatGPT-style alias. Verified against the live API 2026-09-02:
  // max_tokens must be sent as max_completion_tokens; temperature/top_p/presence_penalty/
  // frequency_penalty/logprobs all return 400; reasoning_effort accepts only 'medium'.
  val chatLatest: SettingsConversion = generic(
    Seq(
      maxTokensToMaxCompletionTokens,
      temperatureOneOnly,
      topPOneOnly,
      presencePenaltyZeroOnly,
      frequencyPenaltyZeroOnly,
      logProbsUnsupported,
      reasoningEffortMediumOnly
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
