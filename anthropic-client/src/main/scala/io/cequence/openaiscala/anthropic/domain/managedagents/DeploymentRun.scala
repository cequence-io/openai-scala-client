package io.cequence.openaiscala.anthropic.domain.managedagents

import play.api.libs.json.JsObject

/**
 * A record of a single deployment run (from `GET /v1/deployment_runs` or `POST
 * /v1/deployments/{id}/run`).
 *
 * The deployment-run schema is not published in the API reference, so the common fields are
 * typed and the full payload is retained in [[raw]] for any additional fields.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
 *   API</a>
 */
final case class DeploymentRun(
  id: Option[String],
  deploymentId: Option[String],
  sessionId: Option[String],
  status: Option[String],
  createdAt: Option[String],
  raw: JsObject
)
