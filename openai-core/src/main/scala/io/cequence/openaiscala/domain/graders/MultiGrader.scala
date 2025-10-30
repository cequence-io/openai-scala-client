package io.cequence.openaiscala.domain.graders

/**
 * A grader that combines the output of multiple graders to produce a single score.
 *
 * @param calculateOutput
 *   A formula to calculate the output based on grader results.
 * @param graders
 *   The graders to combine.
 * @param name
 *   The name of the grader.
 */
case class MultiGrader(
  calculateOutput: String,
  graders: Seq[Grader],
  name: String
) extends Grader {
  override val `type`: String = "multi"
}
