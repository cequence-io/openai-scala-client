package io.cequence.openaiscala.typesafe.domain

import play.api.libs.json.{JsObject, JsString, JsValue}

/**
 * The answer to a [[Question]], of the matching kind. Every answer carries the `type` of its
 * question on the wire; an answer type this library does not model yet arrives as
 * [[UnknownAnswer]] rather than being dropped.
 */
sealed trait Answer

/**
 * Probability (0 to 1) that the answer to a [[NoulQuestion]] is yes / the statement is true.
 */
final case class NoulAnswer(noul: Double) extends Answer {

  /** `noul` at or above the threshold reads as yes. */
  def isYes(threshold: Double = 0.5): Boolean = noul >= threshold
}

/**
 * @param choice
 *   the option with the highest probability
 * @param confidence
 *   0 to 1, derived from how peaked the distribution is - gate risky actions on it
 * @param probabilities
 *   every option -> probability (sums to ~1)
 */
final case class ChoiceAnswer(
  choice: String,
  confidence: Double,
  probabilities: Map[String, Double]
) extends Answer {

  /** Options ordered from most to least likely. */
  def ranked: Seq[(String, Double)] = probabilities.toSeq.sortBy(-_._2)
}

/**
 * @param score
 *   the expected level - a probability-weighted average, so e.g. 1.7 between levels 1 and 2
 * @param confidence
 *   0 to 1, derived from how peaked the distribution is
 * @param legend
 *   level -> the description that was sent for it, to interpret the score
 * @param probabilities
 *   level -> probability (sums to ~1)
 */
final case class ScoreAnswer(
  score: Double,
  confidence: Double,
  legend: Map[Int, JsValue],
  probabilities: Map[Int, Double]
) extends Answer {

  /** The rubric levels in order (0 .. n-1). */
  def levels: Seq[Int] = legend.keys.toSeq.sorted

  def maxLevel: Int = levels.lastOption.getOrElse(0)

  /** `score` rescaled to 0..1 (`score / maxLevel`) - the composite-scoring pattern's input. */
  def normalized: Double = if (maxLevel == 0) 0d else score / maxLevel

  /** The single most likely level. */
  def mostLikelyLevel: Int = probabilities.maxBy(_._2)._1

  /** The text description of a level, when the rubric was given as text. */
  def describe(level: Int): Option[String] =
    legend.get(level).collect { case JsString(text) => text }
}

/** An answer whose `type` this library does not know; `raw` is the full JSON as received. */
final case class UnknownAnswer(
  kind: String,
  raw: JsObject
) extends Answer
