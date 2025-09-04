package io.cequence.openaiscala.domain.responsesapi.tools.mcp

import io.cequence.openaiscala.domain.responsesapi.Input

/**
 * A response to an MCP approval request.
 *
 * @param approvalRequestId
 *   The ID of the approval request being answered.
 * @param approve
 *   Whether the request was approved.
 * @param id
 *   The unique ID of the approval response (optional).
 * @param reason
 *   Optional reason for the decision.
 */
final case class MCPApprovalResponse(
  approvalRequestId: String,
  approve: Boolean,
  id: Option[String] = None,
  reason: Option[String] = None
) extends Input {
  val `type`: String = "mcp_approval_response"
}