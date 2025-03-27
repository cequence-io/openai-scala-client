package io.cequence.openaiscala.domain.responsesapi

/**
 * Represents token usage details including input tokens, output tokens, a breakdown of output
 * tokens, and the total tokens used.
 *
 * @param inputTokens
 *   The number of input tokens.
 * @param inputTokensDetails
 *   A detailed breakdown of the input tokens.
 * @param outputTokens
 *   The number of output tokens.
 * @param outputTokensDetails
 *   A detailed breakdown of the output tokens.
 * @param totalTokens
 *   The total number of tokens used.
 */
case class UsageInfo(
  inputTokens: Int,
  inputTokensDetails: Option[InputTokensDetails] = None,
  outputTokens: Int,
  outputTokensDetails: Option[OutputTokensDetails] = None,
  totalTokens: Int
)

/**
 * A detailed breakdown of the input tokens.
 */
case class InputTokensDetails(
  cachedTokens: Option[Int] = None
  // audioTokens: Option[Int] = None
)

/**
 * A detailed breakdown of the output tokens.
 *
 * @param reasoningTokens
 *   The number of reasoning tokens.
 */
case class OutputTokensDetails(
  reasoningTokens: Int
  // acceptedPredictionTokens: Option[Int] = None,
  // rejectedPredictionTokens: Option[Int] = None
)
