package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.settings.ReasoningEffort
import io.cequence.wsclient.domain.EnumValue

/**
 * Configuration options for reasoning models.
 *
 * @param effort
 *   Constrains effort on reasoning for reasoning models. Reducing reasoning effort can result
 *   in faster responses and fewer tokens used on reasoning in a response. Optional, defaults
 *   to Medium.
 * @param generateSummary
 *   A summary of the reasoning performed by the model. This can be useful for debugging and
 *   understanding the model's reasoning process. One of "concise" or "detailed". Optional.
 * @param summary
 *   A summary of the reasoning performed by the model. This can be useful for debugging and
 *   understanding the model's reasoning process. One of auto, concise, or detailed. Optional.
 * @param mode
 *   `standard` (the default) or `pro` - see [[ReasoningMode]]. Optional.
 * @param context
 *   Which earlier reasoning the model sees - see [[ReasoningContext]]. Optional.
 */
case class ReasoningConfig(
  effort: Option[ReasoningEffort] = None,
  @deprecated("Use summary instead", "1.3.0")
  generateSummary: Option[String] = None,
  summary: Option[String] = None,
  mode: Option[ReasoningMode] = None,
  context: Option[ReasoningContext] = None
)

/**
 * `reasoning.mode`: `standard` (the default) or `pro` - more model work for difficult tasks,
 * at a higher latency and token cost; independent of the effort. Live-verified 2026-09-29:
 * accepted by the GPT-6 models (Sol, 6.1 Sol) on OpenAI's Responses API, while GPT-5.5 and
 * Bedrock answer 400 "`reasoning.mode` is not supported with this model".
 */
sealed trait ReasoningMode extends EnumValue

object ReasoningMode {
  case object standard extends ReasoningMode
  case object pro extends ReasoningMode

  val values: Seq[ReasoningMode] = Seq(standard, pro)
}

/**
 * `reasoning.context`: which reasoning is rendered into the next sample - `auto` (the model's
 * default), `current_turn` (only the active turn's reasoning - the default before GPT-5.6) or
 * `all_turns` (compatible reasoning items from earlier turns too - the GPT-5.6 / GPT-6
 * default). GPT-5.4 and newer support it.
 */
sealed trait ReasoningContext extends EnumValue

object ReasoningContext {
  case object auto extends ReasoningContext
  case object current_turn extends ReasoningContext
  case object all_turns extends ReasoningContext

  val values: Seq[ReasoningContext] = Seq(auto, current_turn, all_turns)
}
