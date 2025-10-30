package io.cequence.openaiscala.domain.graders

import io.cequence.openaiscala.domain.ChatRole

/**
 * A grader that uses a model to assign a score to the input.
 *
 * @param input
 *   The input text(s). This may include template strings.
 * @param model
 *   The model to use for the evaluation.
 * @param name
 *   The name of the grader.
 * @param range
 *   The range of the score. Defaults to [0, 1].
 * @param samplingParams
 *   The sampling parameters for the model.
 */
case class ScoreModelGrader(
  input: Seq[GraderModelInput],
  model: String,
  name: String,
  range: Seq[Double] = Nil,
  samplingParams: Option[SamplingParams] = None
) extends Grader {
  override val `type`: String = "score_model"
}

case class GraderModelInput(
  content: GraderInputContent,
  role: ChatRole
) {
  val `type`: String = "message"
}
