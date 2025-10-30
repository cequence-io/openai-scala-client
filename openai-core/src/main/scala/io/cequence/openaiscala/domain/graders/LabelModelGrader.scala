package io.cequence.openaiscala.domain.graders

/**
 * A grader that uses a model to assign labels to each item in the evaluation.
 *
 * @param input
 *   The input messages.
 * @param labels
 *   The labels to assign to each item in the evaluation.
 * @param model
 *   The model to use for the evaluation. Must support structured outputs.
 * @param name
 *   The name of the grader.
 * @param passingLabels
 *   The labels that indicate a passing result. Must be a subset of labels.
 */
case class LabelModelGrader(
  input: Seq[GraderModelInput],
  labels: Seq[String] = Nil,
  model: String,
  name: String,
  passingLabels: Seq[String] = Nil
) extends Grader {
  override val `type`: String = "label_model"
}
