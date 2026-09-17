package io.cequence.openaiscala.typesafe.domain

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}

import scala.collection.immutable.ListMap

/**
 * A typed question for the System One API, evaluated against the request's `state`. There are
 * three kinds - [[NoulQuestion]] (yes/no), [[ChoiceQuestion]] (one of a fixed set) and
 * [[ScoreQuestion]] (a level on an ordered rubric) - and each gets an [[Answer]] of the
 * matching kind back, under the name the caller gave the question.
 *
 * Wherever the API accepts content - `instructions`, an option or level description, the noul
 * criteria - it takes text, a JSON object or a JSON array alike, hence `JsValue`; the `String`
 * overloads on the companions cover the common text case.
 *
 * @see
 *   <a href="https://docs.typesafe.ai/primitives">TypeSafe primitives</a>
 */
sealed trait Question {
  def instructions: Option[JsValue]
}

/**
 * A yes/no question (or a statement to evaluate); answered with the probability of yes.
 *
 * @param instructions
 *   the question or statement, e.g. "Is this message spam?"
 * @param criteria
 *   optional descriptions of what counts as yes and as no
 */
final case class NoulQuestion(
  instructions: Option[JsValue] = None,
  criteria: Option[NoulCriteria] = None
) extends Question {
  // the API answers 400 "Noul question must have criteria or instructions" otherwise
  require(
    instructions.isDefined || criteria.exists(c => c.yes.isDefined || c.no.isDefined),
    "A noul question needs instructions or criteria (what counts as yes / no)."
  )
}

object NoulQuestion {

  def apply(instructions: String): NoulQuestion =
    NoulQuestion(Some(JsString(instructions)))

  def apply(
    instructions: String,
    yes: String,
    no: String
  ): NoulQuestion =
    NoulQuestion(Some(JsString(instructions)), Some(NoulCriteria(yes, no)))
}

/** What counts as a yes (`true` on the wire) and as a no (`false`) for a [[NoulQuestion]]. */
final case class NoulCriteria(
  yes: Option[JsValue] = None,
  no: Option[JsValue] = None
)

object NoulCriteria {

  def apply(
    yes: String,
    no: String
  ): NoulCriteria =
    NoulCriteria(Some(JsString(yes)), Some(JsString(no)))
}

/**
 * Selects one option from the set you define; answered with the winning option, a probability
 * for every option and a confidence.
 *
 * @param criteria
 *   option name -> description of when it applies (`None` = interpreted by its name alone), in
 *   declaration order
 * @param instructions
 *   what to decide, e.g. "Which team should handle this?"
 */
final case class ChoiceQuestion(
  criteria: ListMap[String, Option[JsValue]],
  instructions: Option[JsValue] = None
) extends Question {
  require(criteria.nonEmpty, "A choice question needs at least one option.")
  // the API answers 400 "Too many choices. Must have at most 255 choices." (2026-09-17)
  require(
    criteria.size <= ChoiceQuestion.MaxOptions,
    s"A choice question allows at most ${ChoiceQuestion.MaxOptions} options (got ${criteria.size}); " +
      "narrow in two stages - pick the section first, then the option inside it."
  )

  def labels: Seq[String] = criteria.keys.toSeq
}

object ChoiceQuestion {

  /** The API's cap on options per choice question. */
  val MaxOptions = 255

  /**
   * Described options: `ChoiceQuestion("Which team?", "billing" -> "Payments, refunds", ...)`.
   */
  def apply(
    instructions: String,
    options: (String, String)*
  ): ChoiceQuestion =
    ChoiceQuestion(
      ListMap(options.map { case (label, description) =>
        label -> Option[JsValue](JsString(description))
      }: _*),
      Some(JsString(instructions))
    )

  /**
   * Options interpreted by their names alone: `ChoiceQuestion.ofLabels("Tone?", "calm",
   * "angry")`.
   */
  def ofLabels(
    instructions: String,
    labels: String*
  ): ChoiceQuestion =
    ChoiceQuestion(
      ListMap(labels.map(_ -> Option.empty[JsValue]): _*),
      Some(JsString(instructions))
    )
}

/**
 * Rates the state on an ordered rubric; answered with the expected level (a
 * probability-weighted average, so it may fall between levels), a probability per level and a
 * confidence.
 *
 * @param criteria
 *   level descriptions in order - the position is the score, starting at 0
 * @param instructions
 *   what to rate, e.g. "How frustrated is the customer?"
 */
final case class ScoreQuestion(
  criteria: Seq[JsValue],
  instructions: Option[JsValue] = None
) extends Question {
  require(criteria.nonEmpty, "A score question needs at least one level.")
  // the API answers 422 for a level that is not text, an object or an array
  require(
    criteria.forall {
      case _: JsString | _: JsObject | _: JsArray => true
      case _                                      => false
    },
    "Every score level must be text, a JSON object or a JSON array."
  )
}

object ScoreQuestion {

  /** `ScoreQuestion("How urgent?", "Can wait", "This week", "Today")` - levels 0, 1, 2. */
  def apply(
    instructions: String,
    levels: String*
  ): ScoreQuestion =
    ScoreQuestion(levels.map(JsString(_)), Some(JsString(instructions)))
}
