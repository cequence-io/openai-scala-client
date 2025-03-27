package io.cequence.openaiscala.domain.responsesapi

import io.cequence.wsclient.domain.EnumValue

/**
 * Configuration options for reasoning models (o-series models only).
 *
 * @param effort
 *   Constrains effort on reasoning for reasoning models. Reducing reasoning effort can result
 *   in faster responses and fewer tokens used on reasoning in a response. Optional, defaults
 *   to Medium.
 * @param generateSummary
 *   A summary of the reasoning performed by the model. This can be useful for debugging and
 *   understanding the model's reasoning process. One of "concise" or "detailed". Optional.
 */
case class ReasoningConfig(
  effort: Option[ReasoningEffort] = None,
  generateSummary: Option[String] = None
)

/**
 * Represents the level of reasoning effort for reasoning models.
 */
sealed trait ReasoningEffort extends EnumValue {
  override def toString: String = super.toString.toLowerCase
}

object ReasoningEffort {
  case object Low extends ReasoningEffort
  case object Medium extends ReasoningEffort
  case object High extends ReasoningEffort

  def values: Seq[ReasoningEffort] = Seq(Low, Medium, High)
}
