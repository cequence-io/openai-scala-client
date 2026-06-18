package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.managedagents.{Deployment, PagedResponse}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateDeploymentSettings,
  AnthropicUpdateDeploymentSettings
}

import scala.concurrent.Future

/**
 * Anthropic Managed Agents API — deployment management. A deployment launches sessions for an
 * agent in an environment, on a schedule or on demand. Requires the
 * `managed-agents-2026-04-01` beta; not available on Bedrock.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
 *   API</a>
 */
trait AnthropicDeploymentService {

  /** Creates a deployment (`POST /v1/deployments`). */
  def createDeployment(settings: AnthropicCreateDeploymentSettings): Future[Deployment]

  /** Lists deployments (`GET /v1/deployments`). */
  def listDeployments(
    agentId: Option[String] = None,
    status: Option[String] = None,
    createdAtGte: Option[String] = None,
    createdAtLte: Option[String] = None,
    includeArchived: Option[Boolean] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[Deployment]]

  /** Retrieves a deployment (`GET /v1/deployments/{id}`). */
  def getDeployment(deploymentId: String): Future[Deployment]

  /** Updates a deployment (`POST /v1/deployments/{id}`). */
  def updateDeployment(
    deploymentId: String,
    settings: AnthropicUpdateDeploymentSettings
  ): Future[Deployment]

  /**
   * Archives a deployment (`POST /v1/deployments/{id}/archive`); its status becomes `paused`.
   */
  def archiveDeployment(deploymentId: String): Future[Deployment]
}
