package io.cequence.openaiscala.anthropic.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.managedagents.{
  PagedResponse,
  Session,
  SessionDeleteResponse,
  SessionEvent,
  SessionEventEnvelope,
  SessionResource,
  SessionStatus,
  SessionThread
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateSessionSettings,
  AnthropicUpdateSessionSettings
}

import scala.concurrent.Future

/**
 * Anthropic Managed Agents API — session management, events, resources, and threads. A session
 * is a stateful run of an agent inside an environment. Requires the
 * `managed-agents-2026-04-01` beta; not available on Bedrock.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions API</a>
 */
trait AnthropicSessionService {

  /** Creates a session (`POST /v1/sessions`). Blocks until resources are mounted. */
  def createSession(settings: AnthropicCreateSessionSettings): Future[Session]

  /**
   * Lists sessions (`GET /v1/sessions`).
   *
   * @param statuses
   *   Filter by session status.
   */
  def listSessions(
    agentId: Option[String] = None,
    agentVersion: Option[Int] = None,
    deploymentId: Option[String] = None,
    memoryStoreId: Option[String] = None,
    statuses: Seq[SessionStatus] = Nil,
    createdAtGte: Option[String] = None,
    createdAtLte: Option[String] = None,
    order: Option[String] = None,
    includeArchived: Option[Boolean] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[Session]]

  /** Retrieves a session (`GET /v1/sessions/{id}`). */
  def getSession(sessionId: String): Future[Session]

  /** Updates a session's title/metadata (`POST /v1/sessions/{id}`). */
  def updateSession(
    sessionId: String,
    settings: AnthropicUpdateSessionSettings
  ): Future[Session]

  /** Deletes a session (`DELETE /v1/sessions/{id}`). */
  def deleteSession(sessionId: String): Future[SessionDeleteResponse]

  /** Archives a session (`POST /v1/sessions/{id}/archive`). */
  def archiveSession(sessionId: String): Future[Session]

  // -- Events --

  /** Sends one or more events to a session (`POST /v1/sessions/{id}/events`). */
  def sendSessionEvents(
    sessionId: String,
    events: Seq[SessionEvent]
  ): Future[Unit]

  /** Lists a session's events (`GET /v1/sessions/{id}/events`). */
  def listSessionEvents(
    sessionId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[SessionEventEnvelope]]

  /**
   * Streams a session's events via SSE (`GET /v1/sessions/{id}/events/stream`). Open the
   * stream before sending the kickoff event so no early events are missed.
   */
  def streamSessionEvents(sessionId: String): Source[SessionEventEnvelope, NotUsed]

  // -- Resources --

  /** Attaches a resource to a session (`POST /v1/sessions/{id}/resources`). */
  def addSessionResource(
    sessionId: String,
    resource: SessionResource
  ): Future[SessionResource]

  /** Lists a session's resources (`GET /v1/sessions/{id}/resources`). */
  def listSessionResources(sessionId: String): Future[PagedResponse[SessionResource]]

  /** Retrieves a session resource (`GET /v1/sessions/{id}/resources/{resourceId}`). */
  def getSessionResource(
    sessionId: String,
    resourceId: String
  ): Future[SessionResource]

  /** Removes a session resource (`DELETE /v1/sessions/{id}/resources/{resourceId}`). */
  def deleteSessionResource(
    sessionId: String,
    resourceId: String
  ): Future[Unit]

  // -- Threads (multiagent) --

  /** Lists a session's subagent threads (`GET /v1/sessions/{id}/threads`). */
  def listSessionThreads(
    sessionId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[SessionThread]]

  /** Retrieves a thread (`GET /v1/sessions/{id}/threads/{threadId}`). */
  def getSessionThread(
    sessionId: String,
    threadId: String
  ): Future[SessionThread]

  /** Lists a thread's events (`GET /v1/sessions/{id}/threads/{threadId}/events`). */
  def listSessionThreadEvents(
    sessionId: String,
    threadId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[SessionEventEnvelope]]
}
