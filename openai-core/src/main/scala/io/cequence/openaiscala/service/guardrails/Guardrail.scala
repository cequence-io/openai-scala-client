package io.cequence.openaiscala.service.guardrails

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.guardrails.GuardrailVerdict

import scala.concurrent.Future

/**
 * Checks a chat call's messages before the call is made - e.g. [[ModelGuardrail]], or your own
 * (a deny list, a PII detector, a moderation endpoint). Plugged into a chat service by
 * `OpenAIServiceAdapters.guardrails`, or called directly.
 *
 * A failed future fails the guarded call (fail closed); a guardrail that should fail open
 * returns a passing verdict instead (see [[GuardrailVerdict.failedOpen]]).
 */
trait InputGuardrail {

  /** The name in verdicts and logs. */
  def name: String

  def checkInput(messages: Seq[BaseMessage]): Future[GuardrailVerdict]
}

/**
 * Checks a reply of a chat call before it is returned - see [[InputGuardrail]].
 */
trait OutputGuardrail {

  /** The name in verdicts and logs. */
  def name: String

  /**
   * @param messages
   *   the call's messages - the context of the reply
   * @param reply
   *   the text of one reply (a choice's content)
   */
  def checkOutput(
    messages: Seq[BaseMessage],
    reply: String
  ): Future[GuardrailVerdict]
}
