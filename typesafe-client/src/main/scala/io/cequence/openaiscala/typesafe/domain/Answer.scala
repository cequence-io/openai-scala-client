package io.cequence.openaiscala.typesafe.domain

import io.cequence.openaiscala.domain.JsonSchemaRange
import play.api.libs.json.{Format, JsObject, JsString, JsValue, Json}

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

  /**
   * The probability of an option.
   *
   * @throws IllegalArgumentException
   *   for an option that was not offered (a typo, most likely)
   */
  def probabilityOf(option: String): Double =
    probabilities.getOrElse(
      option,
      throw new IllegalArgumentException(
        s"'$option' was not an option - the options were ${probabilities.keys.mkString(", ")}."
      )
    )

  /**
   * How far the most likely option leads the runner-up - a small margin is a close call, e.g.
   * worth a human's look.
   */
  def margin: Double =
    ranked.map(_._2) match {
      case Seq(first, second, _*) => first - second
      case Seq(only)              => only
      case _                      => 0d
    }
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

  /**
   * The probability of this level or any above it (levels 0 .. n-1) - e.g. to act on "high or
   * worse".
   */
  def probabilityAtLeast(level: Int): Double =
    probabilities.collect { case (l, p) if l >= level => p }.sum

  /** The text description of a level, when the rubric was given as text. */
  def describe(level: Int): Option[String] =
    legend.get(level).collect { case JsString(text) => text }
}

/**
 * Where a [[BoxQuestion]]'s target is in the image.
 *
 * @param box
 *   its edges as fractions of the image's size
 * @param confidence
 *   0 to 1 - low when the target is not in the image; none when the model gives none
 */
final case class BoxAnswer(
  box: BoundingBox,
  confidence: Option[Double]
) extends Answer

/**
 * A rectangle in an image, its edges as fractions (0 to 1) of the image's height (`top`,
 * `bottom`) and width (`left`, `right`), measured from the top-left corner - each the expected
 * value of its edge's predicted position.
 *
 * Its derived JSON schema declares each edge a fraction (`minimum` 0, `maximum` 1), which is
 * what the JSON schema adapter and `decide[T]` ask a box question for.
 */
final case class BoundingBox(
  @JsonSchemaRange(0, 1) top: Double,
  @JsonSchemaRange(0, 1) bottom: Double,
  @JsonSchemaRange(0, 1) left: Double,
  @JsonSchemaRange(0, 1) right: Double
) {

  /** The box's width as a fraction of the image's. */
  def width: Double = right - left

  /** The box's height as a fraction of the image's. */
  def height: Double = bottom - top

  /** The box in pixels of an image this size - (left, top, right, bottom), rounded. */
  def inPixels(
    imageWidth: Int,
    imageHeight: Int
  ): (Int, Int, Int, Int) =
    (
      math.round(left * imageWidth).toInt,
      math.round(top * imageHeight).toInt,
      math.round(right * imageWidth).toInt,
      math.round(bottom * imageHeight).toInt
    )

  /**
   * The box moved out by `margin` (a fraction of the image, not negative - a negative one
   * would turn a small box inside out) on every side, kept in the image.
   */
  def padded(margin: Double): BoundingBox = {
    require(margin >= 0, s"A box's margin cannot be negative: $margin")
    BoundingBox(
      math.max(0d, top - margin),
      math.min(1d, bottom + margin),
      math.max(0d, left - margin),
      math.min(1d, right + margin)
    )
  }
}

object BoundingBox {

  /**
   * `{"top", "bottom", "left", "right"}` - so a case class with a `BoundingBox` field reads it
   * back from a typed decision or the OpenAI adapter's JSON (whose planner answers such an
   * object with a box question).
   */
  implicit val format: Format[BoundingBox] = Json.format[BoundingBox]
}

/** An answer whose `type` this library does not know; `raw` is the full JSON as received. */
final case class UnknownAnswer(
  kind: String,
  raw: JsObject
) extends Answer
