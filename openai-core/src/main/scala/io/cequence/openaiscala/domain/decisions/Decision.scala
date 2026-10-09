package io.cequence.openaiscala.domain.decisions

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.UsageInfo
import io.cequence.wsclient.domain.NamedEnumValue
import play.api.libs.json.JsObject

/**
 * The evidence a decision is about (`input`): text, or user messages of text and inline
 * images.
 */
sealed trait DecisionInput

object DecisionInput {

  final case class Text(text: String) extends DecisionInput

  final case class Messages(messages: Seq[DecisionMessage]) extends DecisionInput

  /** One user message of these parts. */
  def of(parts: DecisionContent*): DecisionInput = Messages(Seq(DecisionMessage(parts)))
}

/** A user message - the only role the API takes - with its parts in order. */
final case class DecisionMessage(content: Seq[DecisionContent])

sealed trait DecisionContent

object DecisionContent {

  final case class InputText(text: String) extends DecisionContent

  /**
   * An inline image: a base64 data URL (`data:image/png;base64,...`) - hosted URLs and file
   * ids are refused (400), at most 128 images per request.
   */
  final case class InputImage(
    imageUrl: String,
    detail: Option[DecisionImageDetail] = None
  ) extends DecisionContent
}

sealed abstract class DecisionImageDetail(value: String) extends NamedEnumValue(value)

object DecisionImageDetail {
  case object low extends DecisionImageDetail("low")
  case object high extends DecisionImageDetail("high")
  case object auto extends DecisionImageDetail("auto")
  case object original extends DecisionImageDetail("original")

  def values: Seq[DecisionImageDetail] = Seq(low, high, auto, original)
}

/**
 * A question about the input. Give each a unique `name` to find its answer (duplicates are a
 * 400); unnamed ones are answered in order.
 */
sealed trait DecisionQuestion {
  def instructions: String
  def name: Option[String]
}

object DecisionQuestion {

  /** Is a condition true? Answered with its probability. */
  final case class Predicate(
    instructions: String,
    name: Option[String] = None
  ) extends DecisionQuestion

  /** One of fixed options (at most 255), each with a description of when it applies. */
  final case class Choice(
    instructions: String,
    choices: Seq[DecisionChoice],
    name: Option[String] = None
  ) extends DecisionQuestion

  /**
   * A rating against ordered levels (at most 10, lowest first): answered with the
   * probability-weighted average of the level indices.
   */
  final case class Score(
    instructions: String,
    levels: Seq[DecisionLevel],
    name: Option[String] = None
  ) extends DecisionQuestion
}

/** A choice value: text or a boolean - `"true"` and `true` are distinct options. */
sealed trait DecisionValue

object DecisionValue {
  final case class Text(value: String) extends DecisionValue
  final case class Bool(value: Boolean) extends DecisionValue
}

final case class DecisionChoice(
  value: DecisionValue,
  description: Option[String] = None
)

object DecisionChoice {

  def apply(value: String): DecisionChoice = DecisionChoice(DecisionValue.Text(value))

  def apply(
    value: String,
    description: String
  ): DecisionChoice = DecisionChoice(DecisionValue.Text(value), Some(description))

  def apply(value: Boolean): DecisionChoice = DecisionChoice(DecisionValue.Bool(value))
}

final case class DecisionLevel(
  label: String,
  description: Option[String] = None
)

/** The answer to a question, carrying its `name` (None for an unnamed question). */
sealed trait DecisionAnswer {
  def name: Option[String]
}

object DecisionAnswer {

  /** The probability that the condition is true. */
  final case class Predicate(
    name: Option[String],
    probability: Double
  ) extends DecisionAnswer

  /** The most likely option, the distribution over all of them and how peaked it is. */
  final case class Choice(
    name: Option[String],
    choice: DecisionValue,
    probabilities: Seq[ChoiceProbability],
    confidence: Double
  ) extends DecisionAnswer

  /** The probability-weighted average of the level indices (from 0), and the distribution. */
  final case class Score(
    name: Option[String],
    score: Double,
    probabilities: Seq[LevelProbability],
    confidence: Double
  ) extends DecisionAnswer

  /** The host declined this question, without saying why; the others are answered. */
  final case class Refusal(name: Option[String]) extends DecisionAnswer

  /** An answer of a type this client does not know, as received. */
  final case class Unknown(
    name: Option[String],
    kind: String,
    raw: JsObject
  ) extends DecisionAnswer
}

final case class ChoiceProbability(
  value: DecisionValue,
  probability: Double
)

/** A level's probability - `value` is its index (from 0). */
final case class LevelProbability(
  value: Int,
  label: String,
  probability: Double
)

/**
 * The answers, in the questions' order.
 *
 * @param requestId
 *   the host's request id header, when the service reads it: `x-request-id` on OpenAI (the
 *   body carries no id); on a System One host the provider's `requestIdHeaders`
 *   (`x-typesafe-request-id`, OpenRouter's `x-generation-id`; llama.cpp sends none)
 */
final case class Decision(
  model: String,
  answers: Seq[DecisionAnswer],
  usage: Option[UsageInfo] = None,
  requestId: Option[String] = None
) {

  /** The answer to the question of this name. */
  def answer(name: String): Option[DecisionAnswer] = answers.find(_.name.contains(name))
}

/**
 * @param model
 *   the deciding model - none for the service's default: `gpt-6-luna` on OpenAI (the only one
 *   at launch, 2026-10-06), the host's default model on another host (a decision service of
 *   the typesafe-client module), so the same call switches hosts unchanged
 * @param safetyIdentifier
 *   an opaque identifier of the end user, for abuse detection (OpenAI's only)
 */
final case class CreateDecisionSettings(
  model: Option[String] = None,
  safetyIdentifier: Option[String] = None
)

object CreateDecisionSettings {

  /** OpenAI's decision model, sent when the settings name none. */
  val DefaultModel: String = ModelId.gpt_6_luna
}
