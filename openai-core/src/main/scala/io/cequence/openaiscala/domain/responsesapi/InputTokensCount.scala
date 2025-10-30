package io.cequence.openaiscala.domain.responsesapi

/**
 * Response from the input tokens count endpoint.
 *
 * @param `object`
 *   Always "response.input_tokens"
 * @param inputTokens
 *   The number of input tokens
 */
final case class InputTokensCount(
  `object`: String,
  inputTokens: Int
)
