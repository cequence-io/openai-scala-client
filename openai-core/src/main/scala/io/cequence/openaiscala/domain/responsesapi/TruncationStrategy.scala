package io.cequence.openaiscala.domain.responsesapi

import io.cequence.wsclient.domain.EnumValue

/**
 * Defines the truncation strategy to use for model responses.
 *
 *   - Auto: If the context exceeds the model's context window size, the model will truncate
 *     the response by dropping input items in the middle of the conversation.
 *   - Disabled: If a model response will exceed the context window size, the request will fail
 *     with a 400 error. This is the default behavior.
 */
sealed trait TruncationStrategy extends EnumValue {
  override def toString: String = super.toString.toLowerCase
}

object TruncationStrategy {
  case object Auto extends TruncationStrategy
  case object Disabled extends TruncationStrategy

  def values: Seq[TruncationStrategy] = Seq(Auto, Disabled)
}
