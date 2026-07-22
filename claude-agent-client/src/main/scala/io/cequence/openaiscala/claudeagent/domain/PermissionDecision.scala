package io.cequence.openaiscala.claudeagent.domain

import play.api.libs.json.JsObject

/** The host's answer to a [[ClaudeAgentEvent.ToolPermissionRequest]]. */
sealed trait PermissionDecision

object PermissionDecision {

  /**
   * Allow the tool call to proceed.
   *
   * @param updatedInput
   *   Tool input to execute. Pass the original request input to approve unchanged.
   */
  case class Allow(updatedInput: JsObject) extends PermissionDecision

  /**
   * Deny the tool call.
   *
   * @param message
   *   Explanation surfaced back to the agent.
   * @param interrupt
   *   If `true`, also interrupts the current turn instead of merely denying this one tool
   *   call.
   */
  case class Deny(
    message: String,
    interrupt: Boolean = false
  ) extends PermissionDecision
}

/**
 * Result of `ClaudeAgentService.interrupt()`.
 *
 * @param stillQueued
 *   Uuids of async user messages that survive the interrupt (per the CLI's
 *   `interrupt_receipt_v1` capability; empty on older CLIs).
 */
case class InterruptResult(stillQueued: Seq[String])
