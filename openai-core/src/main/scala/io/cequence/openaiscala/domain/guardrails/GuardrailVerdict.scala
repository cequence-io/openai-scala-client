package io.cequence.openaiscala.domain.guardrails

import io.cequence.openaiscala.domain.response.UsageInfo
import io.cequence.wsclient.domain.NamedEnumValue

/** Whether a guardrail checked a chat call's input (before the call) or its reply (after). */
sealed abstract class GuardrailStage(value: String) extends NamedEnumValue(value)

object GuardrailStage {
  case object Input extends GuardrailStage("input")
  case object Output extends GuardrailStage("output")
}

/**
 * The answer to one [[GuardrailCheck]].
 *
 * @param flagged
 *   whether the message violates the check
 * @param probability
 *   the probability of a violation, when the guard reports one (a check with a `threshold`)
 */
final case class GuardrailCheckResult(
  name: String,
  flagged: Boolean,
  probability: Option[Double] = None
)

/**
 * What one guardrail decided about one message.
 *
 * @param guardrail
 *   the guardrail's name
 * @param results
 *   every check's answer, in the guardrail's check order - empty when there was nothing to
 *   check (e.g. a call ending with a tool result) or when the guard failed and the guardrail
 *   fails open
 * @param model
 *   the guard model that answered (a model-backed guardrail)
 * @param usage
 *   the guard call's token usage, when it reports one
 * @param failure
 *   why the guard could not answer: a guardrail that fails open passes the message anyway (no
 *   results), one that fails closed flags it as [[GuardrailVerdict.UnavailableCheck]]
 */
final case class GuardrailVerdict(
  guardrail: String,
  stage: GuardrailStage,
  results: Seq[GuardrailCheckResult] = Nil,
  model: Option[String] = None,
  usage: Option[UsageInfo] = None,
  failure: Option[Throwable] = None
) {

  /** The violated checks. */
  def flagged: Seq[GuardrailCheckResult] = results.filter(_.flagged)

  /** Whether the message is blocked. */
  def violation: Boolean = results.exists(_.flagged)

  /** The first violated check (by the guardrail's check order). */
  def category: Option[String] = flagged.headOption.map(_.name)

  /** Whether the guard could not answer and the guardrail failed closed. */
  def unavailable: Boolean = failure.isDefined && violation
}

object GuardrailVerdict {

  /** The check a guardrail that fails closed flags when its guard could not answer. */
  val UnavailableCheck = "guardrail_unavailable"

  /** A guard that could not answer, for a guardrail that fails closed - blocks the message. */
  def failedClosed(
    guardrail: String,
    stage: GuardrailStage,
    failure: Throwable,
    model: Option[String] = None
  ): GuardrailVerdict =
    GuardrailVerdict(
      guardrail,
      stage,
      Seq(GuardrailCheckResult(UnavailableCheck, flagged = true)),
      model,
      failure = Some(failure)
    )

  /** A guard that could not answer, for a guardrail that fails open - passes the message. */
  def failedOpen(
    guardrail: String,
    stage: GuardrailStage,
    failure: Throwable,
    model: Option[String] = None
  ): GuardrailVerdict =
    GuardrailVerdict(guardrail, stage, Nil, model, failure = Some(failure))

  /** One line about blocking verdicts, e.g. for an exception or a log. */
  def describe(verdicts: Seq[GuardrailVerdict]): String =
    verdicts.headOption.map { first =>
      val reasons = verdicts.map { verdict =>
        verdict.failure match {
          case Some(e) if verdict.unavailable =>
            s"the guardrail '${verdict.guardrail}' could not check it (fail closed): ${e.getMessage}"
          case _ =>
            s"the guardrail '${verdict.guardrail}' flagged ${verdict.flagged.map(_.name).mkString(", ")}"
        }
      }
      s"The ${first.stage} was blocked - ${reasons.mkString("; ")}."
    }.getOrElse("No guardrail blocked the call.")
}

/**
 * The `originalResponse` of a reply the guardrails adapter answers in place of a blocked call
 * (`GuardrailAction.Respond`, finish reason `content_filter`).
 *
 * @param verdicts
 *   the blocking verdicts
 * @param originalResponse
 *   the blocked reply's own `originalResponse` (an output block)
 */
final case class GuardrailBlock(
  verdicts: Seq[GuardrailVerdict],
  originalResponse: Option[Any] = None
)
