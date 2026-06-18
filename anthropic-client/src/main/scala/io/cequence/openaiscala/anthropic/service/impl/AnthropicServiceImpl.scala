package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.anthropic.domain.{
  FileDeleteResponse,
  FileListResponse,
  FileMetadata,
  Message
}
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.managedagents.{
  Agent,
  Environment,
  EnvironmentDeleteResponse,
  PagedResponse,
  SelfHostedWork,
  WorkHeartbeatResponse,
  WorkQueueStats
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateAgentSettings,
  AnthropicCreateEnvironmentSettings,
  AnthropicCreateMessageSettings,
  AnthropicUpdateAgentSettings,
  AnthropicUpdateEnvironmentSettings
}
import io.cequence.openaiscala.anthropic.domain.skills.{
  DeleteSkillResponse,
  DeleteSkillVersionResponse,
  ListSkillVersionsResponse,
  ListSkillsResponse,
  Skill,
  SkillSource,
  SkillVersion
}
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.StreamResponseImplicits.StreamSafeOps
import play.api.libs.json.{JsNull, JsNumber, JsObject, JsString, JsValue, Json}

import java.io.File
import scala.concurrent.Future

private[service] trait AnthropicServiceImpl extends Anthropic {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createMessage(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Future[CreateMessageResponse] = {
    val bodyParams =
      createBodyParamsForMessageCreation(messages, settings, stream = Some(false))

    execPOST(
      EndPoint.messages,
      bodyParams = bodyParams,
      // message-feature betas + skill headers if a container (with skills) is passed
      extraHeaders =
        messageBetaHeaders ++ (if (settings.container.isDefined) skillHeaders else Nil)
    ).map(
      _.asSafeJson[CreateMessageResponse]
    )
  }

  override def createMessageStreamed(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Source[ContentBlockDelta, NotUsed] = {
    val bodyParams =
      createBodyParamsForMessageCreation(messages, settings, stream = Some(true))
    val stringParams = paramTuplesToStrings(bodyParams)

    engine
      .execJsonStream(
        EndPoint.messages.toString(),
        "POST",
        bodyParams = stringParams,
        extraHeaders = messageBetaHeaders
      )
      .map(serializeStreamedJson)
      .collect { case Some(delta) => delta }
  }

  override def createSkill(
    displayTitle: Option[String],
    files: Seq[(File, String)]
  ): Future[Skill] = {
    require(files.nonEmpty, "At least one file is required to create a skill")

    val fileParams = files.map { case (file, filename) =>
      (Param.files, file, Some(filename))
    }
    val bodyParams = Seq(
      Param.display_title -> displayTitle
    )

    execPOSTMultipart(
      EndPoint.skills,
      fileParams = fileParams,
      bodyParams = bodyParams
    ).map(
      _.asSafeJson[Skill]
    )
  }

  override def listSkills(
    page: Option[String],
    limit: Option[Int],
    source: Option[SkillSource]
  ): Future[ListSkillsResponse] = {
    val queryParams = Seq(
      Param.page -> page,
      Param.limit -> limit.map(_.toString),
      Param.source -> source.map(_.toString)
    )

    execGET(
      EndPoint.skills,
      params = queryParams,
      extraHeaders = skillHeaders
    ).map(
      _.asSafeJson[ListSkillsResponse]
    )
  }

  override def getSkill(skillId: String): Future[Skill] = {
    execGET(
      EndPoint.skills,
      Some(skillId),
      extraHeaders = skillHeaders
    ).map(
      _.asSafeJson[Skill]
    )
  }

  override def deleteSkill(skillId: String): Future[DeleteSkillResponse] = {
    execDELETE(
      EndPoint.skills,
      Some(skillId),
      extraHeaders = skillHeaders
    ).map(
      _.asSafeJson[DeleteSkillResponse]
    )
  }

  override def createSkillVersion(
    skillId: String,
    files: Seq[(File, String)]
  ): Future[SkillVersion] = {
    require(files.nonEmpty, "At least one file is required to create a skill version")

    val fileParams = files.map { case (file, filename) =>
      (Param.files, file, Some(filename))
    }

    execPOSTMultipart(
      EndPoint.skills,
      endPointParam = Some(s"$skillId/versions"),
      fileParams = fileParams,
      extraHeaders = skillHeaders
    ).map(
      _.asSafeJson[SkillVersion]
    )
  }

  override def listSkillVersions(
    skillId: String,
    page: Option[String],
    limit: Option[Int]
  ): Future[ListSkillVersionsResponse] = {
    val queryParams = Seq(
      Param.page -> page,
      Param.limit -> limit.map(_.toString)
    )

    execGET(
      EndPoint.skills,
      Some(s"$skillId/versions"),
      params = queryParams,
      extraHeaders = skillHeaders
    ).map(
      _.asSafeJson[ListSkillVersionsResponse]
    )
  }

  override def getSkillVersion(
    skillId: String,
    version: String
  ): Future[SkillVersion] = {
    execGET(
      EndPoint.skills,
      Some(s"$skillId/versions/$version"),
      extraHeaders = skillHeaders
    ).map(
      _.asSafeJson[SkillVersion]
    )
  }

  override def deleteSkillVersion(
    skillId: String,
    version: String
  ): Future[DeleteSkillVersionResponse] = {
    execDELETE(
      EndPoint.skills,
      Some(s"$skillId/versions/$version"),
      extraHeaders = skillHeaders
    ).map(
      _.asSafeJson[DeleteSkillVersionResponse]
    )
  }

  override def createFile(
    file: File,
    filename: Option[String] = None
  ): Future[FileMetadata] = {
    val fileParams = Seq(
      (Param.file, file, Some(filename.getOrElse(file.getName)))
    )

    execPOSTMultipart(
      EndPoint.files,
      fileParams = fileParams,
      extraHeaders = fileBetaHeaders
    ).map(
      _.asSafeJson[FileMetadata]
    )
  }

  override def listFiles(
    beforeId: Option[String] = None,
    afterId: Option[String] = None,
    limit: Option[Int] = None
  ): Future[FileListResponse] = {
    val queryParams = Seq(
      Param.before_id -> beforeId,
      Param.after_id -> afterId,
      Param.limit -> limit.map(_.toString)
    )

    execGET(
      EndPoint.files,
      params = queryParams,
      extraHeaders = fileBetaHeaders
    ).map(
      _.asSafeJson[FileListResponse]
    )
  }

  override def getFileMetadata(
    fileId: String
  ): Future[Option[FileMetadata]] = {
    execGETRich(
      EndPoint.files,
      Some(fileId),
      extraHeaders = fileBetaHeaders
    ).map { response =>
      handleNotFoundAndError(response).map(
        _.asSafeJson[FileMetadata]
      )
    }
  }

  override def downloadFile(
    fileId: String
  ): Future[Option[Source[ByteString, _]]] = {
    execGETRich(
      EndPoint.files,
      Some(s"$fileId/content"),
      extraHeaders = fileBetaHeaders
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafeSource)
    }
  }

  override def deleteFile(
    fileId: String
  ): Future[FileDeleteResponse] = {
    execDELETE(
      EndPoint.files,
      Some(fileId),
      extraHeaders = fileBetaHeaders
    ).map(
      _.asSafeJson[FileDeleteResponse]
    )
  }

  // ============================================================================
  // Managed Agents — agents
  // ============================================================================

  override def createAgent(
    settings: AnthropicCreateAgentSettings
  ): Future[Agent] = {
    val bodyParams: Seq[(Param, Option[JsValue])] = Seq(
      Param.name -> Some(JsString(settings.name)),
      Param.model -> Some(Json.toJson(settings.model)),
      Param.description -> settings.description.map(JsString),
      Param.system -> settings.system.map(JsString),
      Param.tools -> optSeq(settings.tools),
      Param.mcp_servers -> optSeq(settings.mcpServers),
      Param.skills -> optSeq(settings.skills),
      Param.metadata -> (if (settings.metadata.nonEmpty) Some(Json.toJson(settings.metadata))
                         else None),
      Param.multiagent -> settings.multiagent.map(Json.toJson(_))
    )

    execPOST(
      EndPoint.agents,
      bodyParams = bodyParams,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[Agent])
  }

  override def listAgents(
    limit: Option[Int],
    page: Option[String],
    createdAtGte: Option[String],
    createdAtLte: Option[String],
    includeArchived: Option[Boolean]
  ): Future[PagedResponse[Agent]] = {
    val queryParams = Seq(
      Param.limit -> limit.map(_.toString),
      Param.page -> page,
      Param.created_at_gte -> createdAtGte,
      Param.created_at_lte -> createdAtLte,
      Param.include_archived -> includeArchived.map(_.toString)
    )

    execGET(
      EndPoint.agents,
      params = queryParams,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[PagedResponse[Agent]])
  }

  override def getAgent(
    agentId: String,
    version: Option[Int]
  ): Future[Agent] = {
    execGET(
      EndPoint.agents,
      Some(agentId),
      params = Seq(Param.version -> version.map(_.toString)),
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[Agent])
  }

  override def updateAgent(
    agentId: String,
    settings: AnthropicUpdateAgentSettings
  ): Future[Agent] = {
    val metadataJson = settings.metadata.map { m =>
      JsObject(m.map { case (k, v) => k -> v.map(JsString(_)).getOrElse(JsNull) })
    }

    val bodyParams: Seq[(Param, Option[JsValue])] = Seq(
      Param.version -> Some(JsNumber(settings.version)),
      Param.name -> settings.name.map(JsString),
      Param.description -> settings.description.map(JsString),
      Param.system -> settings.system.map(JsString),
      Param.model -> settings.model.map(Json.toJson(_)),
      Param.tools -> settings.tools.map(Json.toJson(_)),
      Param.mcp_servers -> settings.mcpServers.map(Json.toJson(_)),
      Param.skills -> settings.skills.map(Json.toJson(_)),
      Param.metadata -> metadataJson,
      Param.multiagent -> settings.multiagent.map(Json.toJson(_))
    )

    execPOST(
      EndPoint.agents,
      Some(agentId),
      bodyParams = bodyParams,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[Agent])
  }

  override def archiveAgent(agentId: String): Future[Agent] = {
    execPOST(
      EndPoint.agents,
      Some(s"$agentId/archive"),
      bodyParams = Nil,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[Agent])
  }

  override def listAgentVersions(
    agentId: String,
    limit: Option[Int],
    page: Option[String]
  ): Future[PagedResponse[Agent]] = {
    val queryParams = Seq(
      Param.limit -> limit.map(_.toString),
      Param.page -> page
    )

    execGET(
      EndPoint.agents,
      Some(s"$agentId/versions"),
      params = queryParams,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[PagedResponse[Agent]])
  }

  // Serialize a sequence as a JSON array body param only when non-empty.
  private def optSeq[T](
    items: Seq[T]
  )(
    implicit writes: play.api.libs.json.Writes[T]
  ): Option[JsValue] =
    if (items.nonEmpty) Some(Json.toJson(items)) else None

  // metadata patch: a None value clears the key (sent as JSON null).
  private def metadataPatchJson(metadata: Map[String, Option[String]]): JsObject =
    JsObject(metadata.map { case (k, v) => k -> v.map(JsString(_)).getOrElse(JsNull) })

  // ============================================================================
  // Managed Agents — environments
  // ============================================================================

  override def createEnvironment(
    settings: AnthropicCreateEnvironmentSettings
  ): Future[Environment] = {
    val bodyParams: Seq[(Param, Option[JsValue])] = Seq(
      Param.name -> Some(JsString(settings.name)),
      Param.config -> settings.config.map(Json.toJson(_)),
      Param.description -> settings.description.map(JsString),
      Param.metadata -> (if (settings.metadata.nonEmpty) Some(Json.toJson(settings.metadata))
                         else None),
      Param.scope -> settings.scope.map(Json.toJson(_))
    )

    execPOST(
      EndPoint.environments,
      bodyParams = bodyParams,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[Environment])
  }

  override def listEnvironments(
    includeArchived: Option[Boolean],
    limit: Option[Int],
    page: Option[String]
  ): Future[PagedResponse[Environment]] = {
    val queryParams = Seq(
      Param.include_archived -> includeArchived.map(_.toString),
      Param.limit -> limit.map(_.toString),
      Param.page -> page
    )

    execGET(
      EndPoint.environments,
      params = queryParams,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[PagedResponse[Environment]])
  }

  override def getEnvironment(environmentId: String): Future[Environment] =
    execGET(
      EndPoint.environments,
      Some(environmentId),
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[Environment])

  override def updateEnvironment(
    environmentId: String,
    settings: AnthropicUpdateEnvironmentSettings
  ): Future[Environment] = {
    val bodyParams: Seq[(Param, Option[JsValue])] = Seq(
      Param.config -> settings.config.map(Json.toJson(_)),
      Param.name -> settings.name.map(JsString),
      Param.description -> settings.description.map(JsString),
      Param.metadata -> settings.metadata.map(metadataPatchJson),
      Param.scope -> settings.scope.map(Json.toJson(_))
    )

    execPOST(
      EndPoint.environments,
      Some(environmentId),
      bodyParams = bodyParams,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[Environment])
  }

  override def deleteEnvironment(
    environmentId: String
  ): Future[EnvironmentDeleteResponse] =
    execDELETE(
      EndPoint.environments,
      Some(environmentId),
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[EnvironmentDeleteResponse])

  override def archiveEnvironment(environmentId: String): Future[Environment] =
    execPOST(
      EndPoint.environments,
      Some(s"$environmentId/archive"),
      bodyParams = Nil,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[Environment])

  // -- Self-hosted work queue --

  override def listWork(
    environmentId: String,
    limit: Option[Int],
    page: Option[String]
  ): Future[PagedResponse[SelfHostedWork]] =
    execGET(
      EndPoint.environments,
      Some(s"$environmentId/work"),
      params = Seq(Param.limit -> limit.map(_.toString), Param.page -> page),
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[PagedResponse[SelfHostedWork]])

  override def getWork(
    environmentId: String,
    workId: String
  ): Future[SelfHostedWork] =
    execGET(
      EndPoint.environments,
      Some(s"$environmentId/work/$workId"),
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[SelfHostedWork])

  override def pollWork(
    environmentId: String,
    blockMs: Option[Int],
    reclaimOlderThanMs: Option[Int],
    workerId: Option[String]
  ): Future[SelfHostedWork] = {
    val headers =
      managedAgentsHeaders ++ workerId.map(id => ("Anthropic-Worker-ID", id)).toSeq
    execGET(
      EndPoint.environments,
      Some(s"$environmentId/work/poll"),
      params = Seq(
        Param.block_ms -> blockMs.map(_.toString),
        Param.reclaim_older_than_ms -> reclaimOlderThanMs.map(_.toString)
      ),
      extraHeaders = headers
    ).map(_.asSafeJson[SelfHostedWork])
  }

  override def acknowledgeWork(
    environmentId: String,
    workId: String
  ): Future[SelfHostedWork] =
    execPOST(
      EndPoint.environments,
      Some(s"$environmentId/work/$workId/ack"),
      bodyParams = Nil,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[SelfHostedWork])

  override def recordWorkHeartbeat(
    environmentId: String,
    workId: String,
    desiredTtlSeconds: Option[Int],
    expectedLastHeartbeat: Option[String]
  ): Future[WorkHeartbeatResponse] =
    execPOST(
      EndPoint.environments,
      Some(s"$environmentId/work/$workId/heartbeat"),
      params = Seq(
        Param.desired_ttl_seconds -> desiredTtlSeconds.map(_.toString),
        Param.expected_last_heartbeat -> expectedLastHeartbeat
      ),
      bodyParams = Nil,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[WorkHeartbeatResponse])

  override def stopWork(
    environmentId: String,
    workId: String
  ): Future[SelfHostedWork] =
    execPOST(
      EndPoint.environments,
      Some(s"$environmentId/work/$workId/stop"),
      bodyParams = Nil,
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[SelfHostedWork])

  override def updateWork(
    environmentId: String,
    workId: String,
    metadata: Map[String, Option[String]]
  ): Future[SelfHostedWork] =
    execPOST(
      EndPoint.environments,
      Some(s"$environmentId/work/$workId"),
      bodyParams = Seq(Param.metadata -> Some(metadataPatchJson(metadata))),
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[SelfHostedWork])

  override def getWorkQueueStats(environmentId: String): Future[WorkQueueStats] =
    execGET(
      EndPoint.environments,
      Some(s"$environmentId/work/stats"),
      extraHeaders = managedAgentsHeaders
    ).map(_.asSafeJson[WorkQueueStats])
}
