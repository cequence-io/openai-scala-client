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
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
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
      // add skill headers if container (with skills) is passed
      extraHeaders = if (settings.container.isDefined) skillHeaders else Nil
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
        bodyParams = stringParams
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
      fileParams = fileParams
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
      params = queryParams
    ).map(
      _.asSafeJson[FileListResponse]
    )
  }

  override def getFileMetadata(
    fileId: String
  ): Future[Option[FileMetadata]] = {
    execGETRich(
      EndPoint.files,
      Some(fileId)
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
      Some(s"$fileId/content")
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafeSource)
    }
  }

  override def deleteFile(
    fileId: String
  ): Future[FileDeleteResponse] = {
    execDELETE(
      EndPoint.files,
      Some(fileId)
    ).map(
      _.asSafeJson[FileDeleteResponse]
    )
  }
}
