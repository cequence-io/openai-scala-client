package io.cequence.openaiscala.anthropic.service

import io.cequence.wsclient.stream.AsyncStream
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

  /**
   * Creates a session (`POST /v1/sessions`). Blocks until resources are mounted.
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions
   *   API</a>
   */
  def createSession(settings: AnthropicCreateSessionSettings): Future[Session]

  /**
   * Lists sessions (`GET /v1/sessions`).
   *
   * @param statuses
   *   Filter by session status.
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions
   *   API</a>
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

  /**
   * Retrieves a session (`GET /v1/sessions/{id}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions
   *   API</a>
   */
  def getSession(sessionId: String): Future[Session]

  /**
   * Updates a session's title/metadata (`POST /v1/sessions/{id}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions
   *   API</a>
   */
  def updateSession(
    sessionId: String,
    settings: AnthropicUpdateSessionSettings
  ): Future[Session]

  /**
   * Deletes a session (`DELETE /v1/sessions/{id}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions
   *   API</a>
   */
  def deleteSession(sessionId: String): Future[SessionDeleteResponse]

  /**
   * Archives a session (`POST /v1/sessions/{id}/archive`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions">Anthropic Sessions
   *   API</a>
   */
  def archiveSession(sessionId: String): Future[Session]

  // -- Events --

  /**
   * Sends one or more events to a session (`POST /v1/sessions/{id}/events`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/events">Anthropic Session
   *   Events API</a>
   */
  def sendSessionEvents(
    sessionId: String,
    events: Seq[SessionEvent]
  ): Future[Unit]

  /**
   * Lists a session's events (`GET /v1/sessions/{id}/events`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/events">Anthropic Session
   *   Events API</a>
   */
  def listSessionEvents(
    sessionId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[SessionEventEnvelope]]

  /**
   * Streams a session's events via SSE (`GET /v1/sessions/{id}/events/stream`). The stream is
   * cold: it connects when it runs, and the session delivers only the events that happen while
   * a subscription is live - to send events (a kickoff, tool confirmations) without missing
   * what they cause, use [[subscribeSessionEvents]].
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/events">Anthropic Session
   *   Events API</a>
   */
  def streamSessionEvents(sessionId: String): AsyncStream[SessionEventEnvelope]

  /**
   * [[streamSessionEvents]], subscribed: the future completes once the server confirmed the
   * subscription (its first frame, which comes with the response), with the session's events
   * from then on as a SINGLE-RUN stream - the shape of the official SDKs'
   * `events.stream(...)`. Send the events that start or resume a turn after it completed, then
   * run the stream; run it once, or cancel it (`run(_ => ()).cancel()`) - an abandoned one
   * holds its connection (the server keeps it open with a keepalive every 30 s). A second run
   * fails: it could not be the same subscription. To reconnect without missing events,
   * subscribe, list the history ([[listSessionEvents]]) and skip the events it had, by id. On
   * an Akka, Pekko or ZIO engine the stream stays the engine's own.
   */
  def subscribeSessionEvents(sessionId: String): Future[AsyncStream[SessionEventEnvelope]]

  // -- Resources --

  /**
   * Attaches a resource to a session (`POST /v1/sessions/{id}/resources`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/resources">Anthropic
   *   Session Resources API</a>
   */
  def addSessionResource(
    sessionId: String,
    resource: SessionResource
  ): Future[SessionResource]

  /**
   * Lists a session's resources (`GET /v1/sessions/{id}/resources`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/resources">Anthropic
   *   Session Resources API</a>
   */
  def listSessionResources(
    sessionId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[SessionResource]]

  /**
   * Retrieves a session resource (`GET /v1/sessions/{id}/resources/{resourceId}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/resources">Anthropic
   *   Session Resources API</a>
   */
  def getSessionResource(
    sessionId: String,
    resourceId: String
  ): Future[SessionResource]

  /**
   * Updates a session resource (`POST /v1/sessions/{id}/resources/{resourceId}`). Currently
   * the only mutable field is the refreshed GitHub `authorization_token`.
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/resources">Anthropic
   *   Session Resources API</a>
   */
  def updateSessionResource(
    sessionId: String,
    resourceId: String,
    authorizationToken: String
  ): Future[SessionResource]

  /**
   * Removes a session resource (`DELETE /v1/sessions/{id}/resources/{resourceId}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/resources">Anthropic
   *   Session Resources API</a>
   */
  def deleteSessionResource(
    sessionId: String,
    resourceId: String
  ): Future[Unit]

  // -- Threads (multiagent) --

  /**
   * Lists a session's subagent threads (`GET /v1/sessions/{id}/threads`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/threads">Anthropic
   *   Session Threads API</a>
   */
  def listSessionThreads(
    sessionId: String,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[SessionThread]]

  /**
   * Retrieves a thread (`GET /v1/sessions/{id}/threads/{threadId}`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/threads">Anthropic
   *   Session Threads API</a>
   */
  def getSessionThread(
    sessionId: String,
    threadId: String
  ): Future[SessionThread]

  /**
   * Archives a thread (`POST /v1/sessions/{id}/threads/{threadId}/archive`).
   *
   * @see
   *   <a href="https://platform.claude.com/docs/en/api/beta/sessions/threads">Anthropic
   *   Session Threads API</a>
   */
  def archiveSessionThread(
    sessionId: String,
    threadId: String
  ): Future[SessionThread]
}
