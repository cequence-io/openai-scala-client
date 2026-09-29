package io.cequence.openaiscala.domain.response

/**
 * The answer to a [[ChatChunk.ToolApprovalRequest]] - pass the decisions of a paused run to
 * the next typed-stream call via `ToolApprovalSettingsOps.setToolApprovalDecisions` to resume
 * it. Answer every pending request of the run: OpenAI rejects a (stateful) resume that leaves
 * one out, an Anthropic managed-agent session stays paused on the unanswered ones.
 *
 * @param reason
 *   why the call is denied, passed to the model - only allowed with `approve = false` (OpenAI
 *   rejects a reason on an approval, Anthropic only takes a `deny_message`)
 */
final case class ToolApprovalDecision(
  request: ChatChunk.ToolApprovalRequest,
  approve: Boolean,
  reason: Option[String] = None
) {
  require(
    !(approve && reason.isDefined),
    s"A reason can only accompany a denial (tool approval request '${request.requestId}')."
  )
}
