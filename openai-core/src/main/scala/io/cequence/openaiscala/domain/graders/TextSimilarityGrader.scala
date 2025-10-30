package io.cequence.openaiscala.domain.graders

import io.cequence.wsclient.domain.EnumValue

/**
 * Evaluation metric type for text similarity.
 */
sealed trait TextSimilarityEvaluationMetric extends EnumValue

object TextSimilarityEvaluationMetric {
  case object cosine extends TextSimilarityEvaluationMetric
  case object fuzzy_match extends TextSimilarityEvaluationMetric
  case object bleu extends TextSimilarityEvaluationMetric
  case object gleu extends TextSimilarityEvaluationMetric
  case object meteor extends TextSimilarityEvaluationMetric
  case object rouge_1 extends TextSimilarityEvaluationMetric
  case object rouge_2 extends TextSimilarityEvaluationMetric
  case object rouge_3 extends TextSimilarityEvaluationMetric
  case object rouge_4 extends TextSimilarityEvaluationMetric
  case object rouge_5 extends TextSimilarityEvaluationMetric
  case object rouge_l extends TextSimilarityEvaluationMetric

  def values = Seq(
    cosine,
    fuzzy_match,
    bleu,
    gleu,
    meteor,
    rouge_1,
    rouge_2,
    rouge_3,
    rouge_4,
    rouge_5,
    rouge_l
  )
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
  evaluationMetric: TextSimilarityEvaluationMetric
) extends Grader {
  override val `type`: String = "text_similarity"
}
