package io.cequence.openaiscala.typesafe.domain

/**
 * The answers of one `POST /v1/systemone` call, keyed by the question names of the request.
 *
 * @param model
 *   the model that answered - may differ from the alias requested (e.g. `jev-latest` resolves
 *   to a dated build)
 * @param requestId
 *   the `x-typesafe-request-id` response header, to quote when reporting an issue to TypeSafe
 */
final case class SystemOneResponse(
  model: String,
  answers: Map[String, Answer],
  usage: Usage,
  requestId: Option[String] = None
) {

  def nouls: Map[String, NoulAnswer] = answers.collect { case (name, a: NoulAnswer) =>
    name -> a
  }

  def choices: Map[String, ChoiceAnswer] =
    answers.collect { case (name, a: ChoiceAnswer) => name -> a }

  def scores: Map[String, ScoreAnswer] =
    answers.collect { case (name, a: ScoreAnswer) => name -> a }

  def unknown: Map[String, UnknownAnswer] =
    answers.collect { case (name, a: UnknownAnswer) => name -> a }

  /** The answer to the named [[NoulQuestion]]; throws if absent or of another kind. */
  def noul(name: String): NoulAnswer = typed(name, "noul") { case a: NoulAnswer => a }

  /** The answer to the named [[ChoiceQuestion]]; throws if absent or of another kind. */
  def choice(name: String): ChoiceAnswer = typed(name, "choice") { case a: ChoiceAnswer => a }

  /** The answer to the named [[ScoreQuestion]]; throws if absent or of another kind. */
  def score(name: String): ScoreAnswer = typed(name, "score") { case a: ScoreAnswer => a }

  private def typed[A](
    name: String,
    kind: String
  )(
    pf: PartialFunction[Answer, A]
  ): A =
    answers.get(name) match {
      case Some(answer) if pf.isDefinedAt(answer) => pf(answer)
      case Some(answer) =>
        throw new NoSuchElementException(
          s"The answer '$name' is a ${kindOf(answer)} answer, not a $kind one."
        )
      case None =>
        throw new NoSuchElementException(
          s"No answer named '$name'; the answers are: ${answers.keys.mkString(", ")}."
        )
    }

  private def kindOf(answer: Answer): String =
    answer match {
      case _: NoulAnswer    => "noul"
      case _: ChoiceAnswer  => "choice"
      case _: ScoreAnswer   => "score"
      case u: UnknownAnswer => u.kind
    }
}

/**
 * Token usage of one call. Output tokens are free of charge; both counts are optional because
 * the official SDKs treat them so (the API reserves the right not to report them).
 */
final case class Usage(
  input_tokens: Option[Int] = None,
  output_tokens: Option[Int] = None
)
