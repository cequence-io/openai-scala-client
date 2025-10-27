package io.cequence.openaiscala.domain.graders

import io.cequence.openaiscala.domain.responsesapi.InputMessageContent

trait Grader {
  val `type`: String
}

/**
 * A grader that checks if the input text matches the reference text.
 *
 * @param input
 *   The input text. This may include template strings.
 * @param name
 *   The name of the grader.
 * @param operation
 *   The string check operation to perform. One of eq, ne, like, or ilike.
 * @param reference
 *   The reference text. This may include template strings.
 */
case class StringGrader(
  input: String,
  name: String,
  operation: String,
  reference: String
) extends Grader {
  override val `type`: String = "string_check"
}

/**
 * A grader that checks text similarity using a specified evaluation metric.
 *
 * @param input
 *   The text being graded.
 * @param name
 *   The name of the grader.
 * @param reference
 *   The text being graded against.
 * @param evaluationMetric
 *   The evaluation metric to use. One of cosine, fuzzy_match, bleu, gleu, meteor, rouge_1,
 *   rouge_2, rouge_3, rouge_4, rouge_5, or rouge_l.
 */
case class TextSimilarityGrader(
  input: String,
  name: String,
  reference: String,
  evaluationMetric: String
) extends Grader {
  override val `type`: String = "text_similarity"
}

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
  input: Seq[Map[String, Any]],
  model: String,
  name: String,
  range: Option[Seq[Double]] = Some(Seq(0.0, 1.0)),
  samplingParams: Option[Map[String, Any]] = None
) extends Grader {
  override val `type`: String = "score_model"
}

/**
 * A grader that uses a model to assign labels to each item in the evaluation.
 *
 * @param input
 *   The input messages.
 * @param model
 *   The model to use for the evaluation. Must support structured outputs.
 * @param name
 *   The name of the grader.
 * @param labels
 *   The labels to assign to each item in the evaluation.
 * @param passingLabels
 *   The labels that indicate a passing result. Must be a subset of labels.
 */
case class LabelModelGrader(
  input: Seq[InputMessageContent],
  model: String,
  name: String,
  labels: Seq[String],
  passingLabels: Seq[String]
) extends Grader {
  override val `type`: String = "label_model"
}

/**
 * A grader that runs a python script on the input.
 *
 * @param imageTag
 *   The image tag to use for the python script.
 * @param name
 *   The name of the grader.
 * @param source
 *   The source code of the python script.
 */
case class PythonGrader(
  imageTag: String,
  name: String,
  source: String
) extends Grader {
  override val `type`: String = "python"
}

/**
 * A grader that combines the output of multiple graders to produce a single score.
 *
 * @param name
 *   The name of the grader.
 * @param graders
 *   The graders to combine.
 * @param calculateOutput
 *   A formula to calculate the output based on grader results.
 */
case class MultiGrader(
  name: String,
  graders: Seq[Grader],
  calculateOutput: String
) extends Grader {
  override val `type`: String = "multi"
}
