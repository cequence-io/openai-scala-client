package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.managedagents.{Agent, PagedResponse}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateAgentSettings,
  AnthropicUpdateAgentSettings
}

import scala.concurrent.Future

/**
 * Anthropic Managed Agents API — agent management.
 *
 * A Managed Agent is a persisted, versioned configuration (model, system prompt, tools, MCP
 * servers, skills) that sessions and deployments reference by id. All endpoints require the
 * `managed-agents-2026-04-01` beta and are not available on Bedrock.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/agents">Anthropic Agents API</a>
 */
trait AnthropicManagedAgentService {

  /**
   * Creates an agent (`POST /v1/agents`).
   *
   * @param settings
   *   Agent configuration (name and model are required).
   * @return
   *   the created agent (version 1)
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/agents">Anthropic Agents API</a>
   */
  def createAgent(
    settings: AnthropicCreateAgentSettings
  ): Future[Agent]

  /**
   * Lists agents (`GET /v1/agents`), most recent first.
   *
   * @param limit
   *   Max results per page (default 20, max 100).
   * @param page
   *   Pagination cursor from a previous response's `nextPage`.
   * @param createdAtGte
   *   Only agents created at or after this RFC 3339 timestamp.
   * @param createdAtLte
   *   Only agents created at or before this RFC 3339 timestamp.
   * @param includeArchived
   *   Whether to include archived agents (default false).
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/agents">Anthropic Agents API</a>
   */
  def listAgents(
    limit: Option[Int] = None,
    page: Option[String] = None,
    createdAtGte: Option[String] = None,
    createdAtLte: Option[String] = None,
    includeArchived: Option[Boolean] = None
  ): Future[PagedResponse[Agent]]

  /**
   * Retrieves an agent (`GET /v1/agents/{id}`).
   *
   * @param agentId
   *   Agent id.
   * @param version
   *   Specific version to fetch; latest if omitted.
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/agents">Anthropic Agents API</a>
   */
  def getAgent(
    agentId: String,
    version: Option[Int] = None
  ): Future[Agent]

  /**
   * Updates an agent (`POST /v1/agents/{id}`), creating a new version.
   *
   * @param agentId
   *   Agent id.
   * @param settings
   *   Fields to change; `version` is the current version (optimistic lock).
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/agents">Anthropic Agents API</a>
   */
  def updateAgent(
    agentId: String,
    settings: AnthropicUpdateAgentSettings
  ): Future[Agent]

  /**
   * Archives an agent (`POST /v1/agents/{id}/archive`). Existing sessions continue; new
   * sessions cannot reference it. Permanent — there is no unarchive.
   *
   * @param agentId
   *   Agent id.
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/agents">Anthropic Agents API</a>
   */
  def archiveAgent(agentId: String): Future[Agent]

  /**
   * Lists an agent's versions (`GET /v1/agents/{id}/versions`), most recent first.
   *
   * @param agentId
   *   Agent id.
   * @param limit
   *   Max results per page (default 20, max 100).
   * @param page
   *   Pagination cursor from a previous response's `nextPage`.
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/agents">Anthropic Agents API</a>
   */
  def listAgentVersions(
    agentId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[Agent]]
}
