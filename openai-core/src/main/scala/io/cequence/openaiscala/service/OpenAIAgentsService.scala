package io.cequence.openaiscala.service

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.domain.SortOrder
import io.cequence.openaiscala.domain.agents._
import play.api.libs.json.JsObject

import scala.concurrent.Future

/**
 * The Agents API (public beta, `OpenAI-Beta: agents=v1` - sent for you): durable cloud agents
 * on a managed Codex harness. An agent (reusable, or configured inline per session) runs in a
 * session, optionally with an environment (an OpenAI-hosted sandbox, a self-hosted machine, or
 * none); the application sends input events and receives the session's events and items.
 *
 * Streaming lives on the streamed service (`OpenAIStreamedServiceExtra`):
 * `createAgentSessionStreamed` (the create call's own stream, closed once the session idles)
 * and `streamAgentSessionEvents` (a subscription, held open - see
 * [[io.cequence.openaiscala.service.AgentSessionEvents]] to end it at the turn's end).
 *
 * Live facts (2026-09-30): a client function call pauses the session (`requires_action`, the
 * arguments a JSON object) until its [[AgentSessionInput.ToolResult]] is posted - answer it
 * promptly (the platform eventually moves on); a subscription opened mid-turn replays the
 * turn's items without text deltas; a session paused mid-turn cannot be deleted (409) before
 * it is cancelled ([[AgentSessionInput.Cancel]]).
 *
 * @see
 *   <a href="https://developers.openai.com/api/docs/guides/agents-api/overview">OpenAI Doc</a>
 */
trait OpenAIAgentsService extends OpenAIServiceConsts {

  // ---- agents ----

  /** Creates a reusable agent. */
  def createAgent(settings: CreateAgentSettings): Future[Agent]

  def listAgents(
    limit: Option[Int] = None,
    order: Option[SortOrder] = None,
    after: Option[String] = None
  ): Future[AgentsPage[Agent]]

  def getAgent(agentId: String): Future[Agent]

  def updateAgent(
    agentId: String,
    settings: UpdateAgentSettings
  ): Future[Agent]

  def deleteAgent(agentId: String): Future[AgentsDeleted]

  // ---- sessions ----

  /**
   * Creates a session - the first `input`, if given, starts a turn that runs asynchronously
   * (follow it with `streamAgentSessionEvents`, or create the session with
   * `createAgentSessionStreamed` instead).
   */
  def createAgentSession(
    settings: CreateAgentSessionSettings,
    input: Option[AgentInput] = None
  ): Future[AgentSession]

  def listAgentSessions(
    limit: Option[Int] = None,
    order: Option[SortOrder] = None,
    after: Option[String] = None,
    agentId: Option[String] = None
  ): Future[AgentsPage[AgentSession]]

  def getAgentSession(sessionId: String): Future[AgentSession]

  /** Changes the session's agent configuration and / or metadata. */
  def updateAgentSession(
    sessionId: String,
    agent: Option[AgentConfig] = None,
    metadata: Option[Map[String, String]] = None
  ): Future[AgentSession]

  /** Deletes a session (and its environment); a turn in progress must be cancelled first. */
  def deleteAgentSession(sessionId: String): Future[AgentsDeleted]

  /**
   * Sends input events to a session - new messages, a function result, a cancellation, a
   * computer-use approval (accepted with 202; the effects arrive as session events).
   *
   * @param idempotencyKey
   *   makes a retried send safe (the `Idempotency-Key` header)
   */
  def sendAgentSessionEvents(
    sessionId: String,
    events: Seq[AgentSessionInput],
    idempotencyKey: Option[String] = None
  ): Future[Unit]

  /**
   * The items of a session, or of one of its subagents (`subagentId`), or of one subagent turn
   * (`subagentId` + `turnId`).
   */
  def listAgentSessionItems(
    sessionId: String,
    limit: Option[Int] = None,
    order: Option[SortOrder] = None,
    after: Option[String] = None,
    subagentId: Option[String] = None,
    turnId: Option[String] = None
  ): Future[AgentsPage[AgentSessionItem]]

  /** The turns of a session, or of one of its subagents. */
  def listAgentSessionTurns(
    sessionId: String,
    limit: Option[Int] = None,
    order: Option[SortOrder] = None,
    after: Option[String] = None,
    subagentId: Option[String] = None
  ): Future[AgentsPage[AgentTurn]]

  def getAgentSessionTurn(
    sessionId: String,
    turnId: String,
    subagentId: Option[String] = None
  ): Future[AgentTurn]

  def listAgentSessionSubagents(
    sessionId: String,
    limit: Option[Int] = None,
    order: Option[SortOrder] = None,
    after: Option[String] = None
  ): Future[AgentsPage[AgentSubagent]]

  def getAgentSessionSubagent(
    sessionId: String,
    subagentId: String
  ): Future[AgentSubagent]

  // ---- artifacts ----

  def listAgentSessionArtifacts(
    sessionId: String,
    limit: Option[Int] = None,
    order: Option[SortOrder] = None,
    after: Option[String] = None,
    environmentId: Option[String] = None
  ): Future[AgentsPage[AgentSessionArtifact]]

  def getAgentSessionArtifact(
    sessionId: String,
    artifactId: String
  ): Future[AgentSessionArtifact]

  /** The artifact's content as a byte stream. */
  def getAgentSessionArtifactContent(
    sessionId: String,
    artifactId: String
  ): Future[Source[ByteString, _]]

  def deleteAgentSessionArtifact(
    sessionId: String,
    artifactId: String
  ): Future[AgentsDeleted]

  // ---- environments ----

  /**
   * Creates a reusable environment template - `settings` as JSON (`name`, `packages`,
   * `setup_commands`, `network`, `desktop`, `env`, `capability_directories`, `skills`,
   * `plugins`, `files`).
   */
  def createAgentEnvironmentTemplate(settings: JsObject): Future[AgentEnvironmentTemplate]

  def listAgentEnvironmentTemplates(
    limit: Option[Int] = None,
    order: Option[SortOrder] = None,
    after: Option[String] = None
  ): Future[AgentsPage[AgentEnvironmentTemplate]]

  def getAgentEnvironmentTemplate(templateId: String): Future[AgentEnvironmentTemplate]

  def updateAgentEnvironmentTemplate(
    templateId: String,
    settings: JsObject
  ): Future[AgentEnvironmentTemplate]

  def deleteAgentEnvironmentTemplate(templateId: String): Future[AgentsDeleted]

  /** A session's (hosted) environment - `type`, `status`, `plugins`, `skills`, `files`. */
  def getAgentEnvironment(environmentId: String): Future[JsObject]

  /** The files of an environment, as returned (`data`, `next`, `has_more`). */
  def listAgentEnvironmentFiles(
    environmentId: String,
    path: Option[String] = None,
    limit: Option[Int] = None,
    order: Option[SortOrder] = None,
    page: Option[String] = None
  ): Future[JsObject]

  /**
   * Adds a file to an environment - `file` as JSON: an uploaded file's `file_id`, or its
   * inline content (see the API reference).
   */
  def addAgentEnvironmentFile(
    environmentId: String,
    file: JsObject
  ): Future[JsObject]
}
