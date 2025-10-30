package io.cequence.openaiscala.domain.graders

import io.cequence.openaiscala.domain.settings.ReasoningEffort

/**
 * The sampling parameters for the model.
 *
 * @param maxCompletionsTokens
 *   The maximum number of tokens the grader model may generate in its response.
 * @param reasoningEffort
 *   Constrains effort on reasoning for reasoning models. Currently supported values are
 *   minimal, low, medium, and high. Reducing reasoning effort can result in faster responses
 *   and fewer tokens used on reasoning in a response. Note: The gpt-5-pro model defaults to
 *   (and only supports) high reasoning effort.
 * @param seed
 *   A seed value to initialize the randomness, during sampling.
 * @param temperature
 *   A higher temperature increases randomness in the outputs.
 * @param topP
 *   An alternative to temperature for nucleus sampling; 1.0 includes all tokens.
 */
case class SamplingParams(
  maxCompletionsTokens: Option[Int] = None,
  reasoningEffort: Option[ReasoningEffort] = None,
  seed: Option[Int] = None,
  temperature: Option[Double] = None,
  topP: Option[Double] = None
)
