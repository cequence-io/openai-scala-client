package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  Environment,
  EnvironmentDeleteResponse,
  PagedResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateEnvironmentSettings,
  AnthropicUpdateEnvironmentSettings
}

import scala.concurrent.Future

/**
 * Anthropic Managed Agents API — environment management. An environment is a reusable
 * container-provisioning template (cloud or self-hosted) that sessions reference. Requires the
 * `managed-agents-2026-04-01` beta; not available on Bedrock.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/environments">Anthropic Environments
 *   API</a>
 */
trait AnthropicEnvironmentService {

  /** Creates an environment (`POST /v1/environments`). */
  def createEnvironment(
    settings: AnthropicCreateEnvironmentSettings
  ): Future[Environment]

  /**
   * Lists environments (`GET /v1/environments`).
   *
   * @param includeArchived
   *   Whether to include archived environments.
   * @param limit
   *   Max results per page.
   * @param page
   *   Pagination cursor from a previous response's `nextPage`.
   */
  def listEnvironments(
    includeArchived: Option[Boolean] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[Environment]]

  /** Retrieves an environment (`GET /v1/environments/{id}`). */
  def getEnvironment(environmentId: String): Future[Environment]

  /** Updates an environment (`POST /v1/environments/{id}`). */
  def updateEnvironment(
    environmentId: String,
    settings: AnthropicUpdateEnvironmentSettings
  ): Future[Environment]

  /** Deletes an environment (`DELETE /v1/environments/{id}`). */
  def deleteEnvironment(environmentId: String): Future[EnvironmentDeleteResponse]

  /**
   * Archives an environment (`POST /v1/environments/{id}/archive`). Archived environments
   * cannot back new sessions. Permanent — there is no unarchive.
   */
  def archiveEnvironment(environmentId: String): Future[Environment]
}
