package io.cequence.openaiscala.domain.guardrails

/** What the guardrails adapter does with a call a guardrail blocked. */
sealed trait GuardrailAction

object GuardrailAction {

  /**
   * Fail the call with an `OpenAIScalaGuardrailException` carrying the blocking verdicts.
   */
  case object Reject extends GuardrailAction

  /**
   * Answer the call with this message instead, finish reason `content_filter` (OpenAI's own
   * for filtered content); the response's `originalResponse` is a [[GuardrailBlock]].
   *
   * @param message
   *   the reply to the first blocking verdict
   */
  final case class Respond(
    message: GuardrailVerdict => String = defaultMessage
  ) extends GuardrailAction

  /** [[Respond]] with a fixed message. */
  def respond(message: String): Respond = Respond(_ => message)

  /** Names the violated checks, or says the check is unavailable when the guard failed. */
  def defaultMessage(verdict: GuardrailVerdict): String =
    if (verdict.unavailable)
      "I can't answer right now - the content check is unavailable. Please try again later."
    else {
      val categories = verdict.flagged.map(_.name.replace('_', ' ')).mkString(", ")
      verdict.stage match {
        case GuardrailStage.Input =>
          s"I can't help with this request. It was flagged by the content policy ($categories)."
        case GuardrailStage.Output =>
          s"I can't share this answer. It was flagged by the content policy ($categories)."
      }
    }
}

/** Which messages of a chat call an input guardrail checks. */
sealed trait GuardrailScope

object GuardrailScope {

  /**
   * The user messages since the model last spoke - after the last assistant, tool or function
   * message - i.e. the call's new input (default). Every earlier turn was checked when it was
   * new, so a call that ends with a tool result (a tool loop's later turns) has nothing to
   * check; a system message after the user's does not hide it.
   */
  case object NewUserMessages extends GuardrailScope

  /**
   * Every user message of the call - catches instructions split across turns, at the cost of
   * checking the whole history on every call.
   */
  case object AllUserMessages extends GuardrailScope
}
