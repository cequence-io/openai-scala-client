package io.cequence.openaiscala.domain.responsesapi

/**
 * A description of the chain of thought used by a reasoning model while generating a response.
 *
 * @param id
 *   The unique identifier of the reasoning content
 * @param summary
 *   Reasoning summary content
 * @param content
 *   Reasoning text content
 * @param encryptedContent
 *   The encrypted content of the reasoning item - populated when a response is generated with
 *   reasoning.encrypted_content in the include parameter
 * @param status
 *   The status of the item
 */
final case class Reasoning(
  id: String,
  summary: Seq[SummaryText] = Nil,
  content: Seq[ReasoningText] = Nil,
  encryptedContent: Option[String] = None,
  status: Option[ModelStatus] = None // in_progress, completed, or incomplete.
) extends Input
    with Output {
  val `type`: String = "reasoning"
}

/**
 * A summary text component of reasoning.
 *
 * @param text
 *   A summary of the reasoning output from the model so far
 */
case class SummaryText(
  text: String
) {
  val `type`: String = "summary_text"
}

/**
 * A text component of reasoning.
 *
 * @param text
 *   The reasoning text from the model
 */
case class ReasoningText(
  text: String
) {
  val `type`: String = "reasoning_text"
}
