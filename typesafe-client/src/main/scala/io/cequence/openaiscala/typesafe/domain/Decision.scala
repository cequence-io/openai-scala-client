package io.cequence.openaiscala.typesafe.domain

/**
 * A typed decision (`DecisionServiceExtra.decide[T]`): the value read from the answers, and
 * the answers themselves - the probabilities behind each field, under the field's path:
 * `refund`, `customer.vip` for a nested case class, `topics.[billing]` for the option
 * `billing` of a multi-select field (a `.` or `\` inside a name is escaped with a backslash -
 * see `TypeSafeChatMapping.toQuestions`).
 *
 * @param value
 *   the answers read as `T`
 * @param response
 *   the decision model's response - every answer, the model and the usage
 */
final case class Decision[T](
  value: T,
  response: SystemOneResponse
) {

  /** Every answer, by question name (the field's path). */
  def answers: Map[String, Answer] = response.answers

  /** The answer to a yes/no field - its probability of yes. */
  def noul(path: String): NoulAnswer = response.noul(path)

  /** The answer to an enum field - the probability of each option. */
  def choice(path: String): ChoiceAnswer = response.choice(path)

  /**
   * The answer to a field on a scale (a small numeric range) - the probability of each level.
   */
  def score(path: String): ScoreAnswer = response.score(path)

  /** The model that decided, e.g. `jev-1.13.0` for `jev-latest`. */
  def model: String = response.model

  def usage: Usage = response.usage
}
