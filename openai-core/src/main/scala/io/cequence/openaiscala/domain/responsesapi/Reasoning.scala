package io.cequence.openaiscala.domain.responsesapi

/**
 * A description of the chain of thought used by a reasoning model while generating a response.
 *
 * @param id
 *   The unique identifier of the reasoning content
 * @param summary
 *   Reasoning text contents
 * @param `type`
 *   The type of the object. Always reasoning
 * @param status
 *   The status of the item
 */
final case class Reasoning(
  id: String,
  summary: Seq[ReasoningText] = Nil,
  status: Option[ModelStatus] = None // in_progress, completed, or incomplete.
) extends Input
    with Output {
  val `type`: String = "reasoning"
}

/**
 * A summary text component of reasoning.
 *
 * @param text
 *   The text content of the reasoning summary
 */
case class ReasoningText(
  text: String
) {
  val `type`: String = "summary_text"
}
