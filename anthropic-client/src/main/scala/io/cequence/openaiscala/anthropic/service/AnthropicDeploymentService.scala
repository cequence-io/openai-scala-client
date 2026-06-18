package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  Deployment,
  DeploymentRun,
  PagedResponse
}
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

  /**
   * Creates a deployment (`POST /v1/deployments`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def createDeployment(settings: AnthropicCreateDeploymentSettings): Future[Deployment]

  /**
   * Lists deployments (`GET /v1/deployments`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def listDeployments(
    agentId: Option[String] = None,
    status: Option[String] = None,
    createdAtGte: Option[String] = None,
    createdAtLte: Option[String] = None,
    includeArchived: Option[Boolean] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[Deployment]]

  /**
   * Retrieves a deployment (`GET /v1/deployments/{id}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def getDeployment(deploymentId: String): Future[Deployment]

  /**
   * Updates a deployment (`POST /v1/deployments/{id}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def updateDeployment(
    deploymentId: String,
    settings: AnthropicUpdateDeploymentSettings
  ): Future[Deployment]

  /**
   * Archives a deployment (`POST /v1/deployments/{id}/archive`); its status becomes `paused`.
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def archiveDeployment(deploymentId: String): Future[Deployment]

  /**
   * Pauses a deployment (`POST /v1/deployments/{id}/pause`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def pauseDeployment(deploymentId: String): Future[Deployment]

  /**
   * Resumes a paused deployment (`POST /v1/deployments/{id}/unpause`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def unpauseDeployment(deploymentId: String): Future[Deployment]

  /**
   * Triggers an immediate run of a deployment (`POST /v1/deployments/{id}/run`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def runDeployment(deploymentId: String): Future[DeploymentRun]

  /**
   * Lists a deployment's runs (`GET /v1/deployment_runs?deployment_id=...`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/deployments">Anthropic Deployments
   *   API</a>
   */
  def listDeploymentRuns(
    deploymentId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[DeploymentRun]]
}
