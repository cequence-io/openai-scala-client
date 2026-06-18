package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  Environment,
  EnvironmentDeleteResponse,
  PagedResponse,
  SelfHostedWork,
  WorkHeartbeatResponse,
  WorkQueueStats
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

  // -- Self-hosted work queue --
  // These endpoints are normally driven automatically by the SDK/CLI environment worker; they
  // are exposed here for completeness and advanced/self-hosted orchestration.

  /** Lists work items in an environment (`GET /v1/environments/{id}/work`). */
  def listWork(
    environmentId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[SelfHostedWork]]

  /** Retrieves a work item (`GET /v1/environments/{id}/work/{workId}`). */
  def getWork(
    environmentId: String,
    workId: String
  ): Future[SelfHostedWork]

  /**
   * Long-polls for a work item (`GET /v1/environments/{id}/work/poll`).
   *
   * @param blockMs
   *   How long (1-999 ms) to wait for work; non-blocking if omitted.
   * @param reclaimOlderThanMs
   *   Reclaim unacknowledged work older than this (default 5000ms).
   * @param workerId
   *   Worker identifier, sent as the `Anthropic-Worker-ID` header for queue metrics.
   */
  def pollWork(
    environmentId: String,
    blockMs: Option[Int] = None,
    reclaimOlderThanMs: Option[Int] = None,
    workerId: Option[String] = None
  ): Future[SelfHostedWork]

  /** Acknowledges a work item (`POST /v1/environments/{id}/work/{workId}/ack`). */
  def acknowledgeWork(
    environmentId: String,
    workId: String
  ): Future[SelfHostedWork]

  /** Records a heartbeat (`POST /v1/environments/{id}/work/{workId}/heartbeat`). */
  def recordWorkHeartbeat(
    environmentId: String,
    workId: String,
    desiredTtlSeconds: Option[Int] = None,
    expectedLastHeartbeat: Option[String] = None
  ): Future[WorkHeartbeatResponse]

  /**
   * Stops a work item (`POST /v1/environments/{id}/work/{workId}/stop`).
   *
   * @param force
   *   Stop even if the worker has not acknowledged; defaults to the server's behavior.
   */
  def stopWork(
    environmentId: String,
    workId: String,
    force: Option[Boolean] = None
  ): Future[SelfHostedWork]

  /**
   * Updates a work item's metadata with merge semantics (`POST
   * /v1/environments/{id}/work/{workId}`). A `None` value deletes the key.
   */
  def updateWork(
    environmentId: String,
    workId: String,
    metadata: Map[String, Option[String]]
  ): Future[SelfHostedWork]

  /** Work-queue statistics (`GET /v1/environments/{id}/work/stats`). */
  def getWorkQueueStats(environmentId: String): Future[WorkQueueStats]
}
