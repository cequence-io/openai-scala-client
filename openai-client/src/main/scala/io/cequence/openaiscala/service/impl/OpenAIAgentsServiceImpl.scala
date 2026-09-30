package io.cequence.openaiscala.service.impl

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.domain.SortOrder
import io.cequence.openaiscala.domain.agents.JsonFormats._
import io.cequence.openaiscala.domain.agents._
import io.cequence.openaiscala.service.OpenAIAgentsService
import io.cequence.wsclient.ResponseImplicits._
import io.cequence.wsclient.StreamResponseImplicits._
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

/**
 * The Agents API (beta) - every call carries `OpenAI-Beta: agents=v1` (see
 * [[OpenAIAgentsServiceImpl.betaHeaders]]).
 */
trait OpenAIAgentsServiceImpl extends OpenAIAgentsService with OpenAIServiceWSBase {

  import OpenAIAgentsServiceImpl.betaHeaders

  private def listParams(
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String]
  ): Seq[(Param, Option[Any])] =
    Seq(
      Param.limit -> limit,
      Param.order -> order.map(_.toString),
      Param.after -> after
    )

  // ---- agents ----

  override def createAgent(settings: CreateAgentSettings): Future[Agent] =
    execPOSTBody(
      EndPoint.agents,
      body = Json.toJsObject(settings),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[Agent])

  override def listAgents(
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String]
  ): Future[AgentsPage[Agent]] =
    execGET(
      EndPoint.agents,
      params = listParams(limit, order, after),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsPage[Agent]])

  override def getAgent(agentId: String): Future[Agent] =
    execGET(EndPoint.agents, Some(agentId), extraHeaders = betaHeaders)
      .map(_.asSafeJson[Agent])

  override def updateAgent(
    agentId: String,
    settings: UpdateAgentSettings
  ): Future[Agent] =
    execPOSTBody(
      EndPoint.agents,
      Some(agentId),
      body = Json.toJsObject(settings),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[Agent])

  override def deleteAgent(agentId: String): Future[AgentsDeleted] =
    execDELETE(EndPoint.agents, Some(agentId), extraHeaders = betaHeaders)
      .map(_.asSafeJson[AgentsDeleted])

  // ---- sessions ----

  override def createAgentSession(
    settings: CreateAgentSessionSettings,
    input: Option[AgentInput]
  ): Future[AgentSession] =
    execPOSTBody(
      EndPoint.agent_sessions,
      body = createAgentSessionBody(settings, input, stream = false),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentSession])

  override def listAgentSessions(
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    agentId: Option[String]
  ): Future[AgentsPage[AgentSession]] =
    execGET(
      EndPoint.agent_sessions,
      params = listParams(limit, order, after) :+ (Param.agent_id -> agentId),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsPage[AgentSession]])

  override def getAgentSession(sessionId: String): Future[AgentSession] =
    execGET(EndPoint.agent_sessions, Some(sessionId), extraHeaders = betaHeaders)
      .map(_.asSafeJson[AgentSession])

  override def updateAgentSession(
    sessionId: String,
    agent: Option[AgentConfig],
    metadata: Option[Map[String, String]]
  ): Future[AgentSession] = {
    val body = JsObject(
      agent.map(a => "agent" -> Json.toJson(a)).toSeq ++
        metadata.map(m => "metadata" -> Json.toJson(m)).toSeq
    )
    execPOSTBody(
      EndPoint.agent_sessions,
      Some(sessionId),
      body = body,
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentSession])
  }

  override def deleteAgentSession(sessionId: String): Future[AgentsDeleted] =
    execDELETE(EndPoint.agent_sessions, Some(sessionId), extraHeaders = betaHeaders)
      .map(_.asSafeJson[AgentsDeleted])

  override def sendAgentSessionEvents(
    sessionId: String,
    events: Seq[AgentSessionInput],
    idempotencyKey: Option[String]
  ): Future[Unit] =
    execPOSTBody(
      EndPoint.agent_sessions,
      Some(s"$sessionId/events"),
      body = Json.obj("events" -> events),
      extraHeaders = betaHeaders ++ idempotencyKey.map("Idempotency-Key" -> _)
    ).map(_ => ())

  override def listAgentSessionItems(
    sessionId: String,
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    subagentId: Option[String],
    turnId: Option[String]
  ): Future[AgentsPage[AgentSessionItem]] = {
    val path = (subagentId, turnId) match {
      case (Some(subagent), Some(turn)) => s"$sessionId/subagents/$subagent/turns/$turn/items"
      case (Some(subagent), None)       => s"$sessionId/subagents/$subagent/items"
      case (None, Some(_)) =>
        throw new IllegalArgumentException(
          "The items of one turn are listed for a subagent only - pass subagentId too."
        )
      case (None, None) => s"$sessionId/items"
    }
    execGET(
      EndPoint.agent_sessions,
      Some(path),
      params = listParams(limit, order, after),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsPage[AgentSessionItem]])
  }

  override def listAgentSessionTurns(
    sessionId: String,
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    subagentId: Option[String]
  ): Future[AgentsPage[AgentTurn]] =
    execGET(
      EndPoint.agent_sessions,
      Some(subagentId.fold(s"$sessionId/turns")(id => s"$sessionId/subagents/$id/turns")),
      params = listParams(limit, order, after),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsPage[AgentTurn]])

  override def getAgentSessionTurn(
    sessionId: String,
    turnId: String,
    subagentId: Option[String]
  ): Future[AgentTurn] =
    execGET(
      EndPoint.agent_sessions,
      Some(
        subagentId.fold(s"$sessionId/turns/$turnId")(id =>
          s"$sessionId/subagents/$id/turns/$turnId"
        )
      ),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentTurn])

  override def listAgentSessionSubagents(
    sessionId: String,
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String]
  ): Future[AgentsPage[AgentSubagent]] =
    execGET(
      EndPoint.agent_sessions,
      Some(s"$sessionId/subagents"),
      params = listParams(limit, order, after),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsPage[AgentSubagent]])

  override def getAgentSessionSubagent(
    sessionId: String,
    subagentId: String
  ): Future[AgentSubagent] =
    execGET(
      EndPoint.agent_sessions,
      Some(s"$sessionId/subagents/$subagentId"),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentSubagent])

  // ---- artifacts ----

  override def listAgentSessionArtifacts(
    sessionId: String,
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    environmentId: Option[String]
  ): Future[AgentsPage[AgentSessionArtifact]] =
    execGET(
      EndPoint.agent_sessions,
      Some(s"$sessionId/artifacts"),
      params = listParams(limit, order, after) :+ (Param.environment_id -> environmentId),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsPage[AgentSessionArtifact]])

  override def getAgentSessionArtifact(
    sessionId: String,
    artifactId: String
  ): Future[AgentSessionArtifact] =
    execGET(
      EndPoint.agent_sessions,
      Some(s"$sessionId/artifacts/$artifactId"),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentSessionArtifact])

  override def getAgentSessionArtifactContent(
    sessionId: String,
    artifactId: String
  ): Future[Source[ByteString, _]] =
    execGET(
      EndPoint.agent_sessions,
      Some(s"$sessionId/artifacts/$artifactId/content"),
      extraHeaders = betaHeaders
    ).map(_.asSafeSource)

  override def deleteAgentSessionArtifact(
    sessionId: String,
    artifactId: String
  ): Future[AgentsDeleted] =
    execDELETE(
      EndPoint.agent_sessions,
      Some(s"$sessionId/artifacts/$artifactId"),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsDeleted])

  // ---- environments ----

  override def createAgentEnvironmentTemplate(
    settings: JsObject
  ): Future[AgentEnvironmentTemplate] =
    execPOSTBody(
      EndPoint.agent_environment_templates,
      body = settings,
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentEnvironmentTemplate])

  override def listAgentEnvironmentTemplates(
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String]
  ): Future[AgentsPage[AgentEnvironmentTemplate]] =
    execGET(
      EndPoint.agent_environment_templates,
      params = listParams(limit, order, after),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsPage[AgentEnvironmentTemplate]])

  override def getAgentEnvironmentTemplate(
    templateId: String
  ): Future[AgentEnvironmentTemplate] =
    execGET(EndPoint.agent_environment_templates, Some(templateId), extraHeaders = betaHeaders)
      .map(_.asSafeJson[AgentEnvironmentTemplate])

  override def updateAgentEnvironmentTemplate(
    templateId: String,
    settings: JsObject
  ): Future[AgentEnvironmentTemplate] =
    execPOSTBody(
      EndPoint.agent_environment_templates,
      Some(templateId),
      body = settings,
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentEnvironmentTemplate])

  override def deleteAgentEnvironmentTemplate(templateId: String): Future[AgentsDeleted] =
    execDELETE(
      EndPoint.agent_environment_templates,
      Some(templateId),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[AgentsDeleted])

  override def getAgentEnvironment(environmentId: String): Future[JsObject] =
    execGET(EndPoint.agent_environments, Some(environmentId), extraHeaders = betaHeaders)
      .map(_.asSafeJson[JsObject])

  override def listAgentEnvironmentFiles(
    environmentId: String,
    path: Option[String],
    limit: Option[Int],
    order: Option[SortOrder],
    page: Option[String]
  ): Future[JsObject] =
    execGET(
      EndPoint.agent_environments,
      Some(s"$environmentId/files"),
      params = Seq(
        Param.path -> path,
        Param.limit -> limit,
        Param.order -> order.map(_.toString),
        Param.page -> page
      ),
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[JsObject])

  override def addAgentEnvironmentFile(
    environmentId: String,
    file: JsObject
  ): Future[JsObject] =
    execPOSTBody(
      EndPoint.agent_environments,
      Some(s"$environmentId/files"),
      body = file,
      extraHeaders = betaHeaders
    ).map(_.asSafeJson[JsObject])
}

object OpenAIAgentsServiceImpl {

  /** The beta header every Agents API call needs (the only `OpenAI-Beta` one sent). */
  val betaHeaders: Seq[(String, String)] = Seq("OpenAI-Beta" -> "agents=v1")
}
