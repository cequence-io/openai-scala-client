package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.javadsl.{Framing, FramingTruncation}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.wsclient.service.ws.PlayJsonUtil
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
import play.api.libs.json.{JsString, JsValue, Json}

import java.io.File
import scala.concurrent.Future

private[service] trait AnthropicBedrockServiceImpl extends Anthropic with BedrockAuthHelper {

  override protected type PEP = String
  override protected type PT = Param

  private def invokeEndpoint(model: String) = s"model/$model/invoke"
  private def invokeWithResponseStreamEndpoint(model: String) =
    s"model/$model/invoke-with-response-stream"
  private val serviceName = "bedrock"

  private val bedrockAnthropicVersion = "bedrock-2023-05-31"

  override def createMessage(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Future[CreateMessageResponse] = {
    val coreBodyParams =
      createBodyParamsForMessageCreation(messages, settings, stream = None, ignoreModel = true)
    val bodyParams =
      coreBodyParams :+ (Param.anthropic_version -> Some(JsString(bedrockAnthropicVersion)))

    val jsBodyObject = toJsBodyObject(paramTuplesToStrings(bodyParams))
    val endpoint = invokeEndpoint(settings.model)

    val extraSignatureHeaders = createSignatureHeaders(
      "POST",
      createURL(Some(endpoint)),
      headers = requestContext.authHeaders,
      jsBodyObject
    )

    val extraSkillsHeaders = if (settings.container.isDefined) skillHeaders else Nil

    execPOST(
      endpoint,
      bodyParams = bodyParams,
      extraHeaders = extraSignatureHeaders ++ extraSkillsHeaders
    ).map(
      _.asSafeJson[CreateMessageResponse]
    )
  }

  override def createMessageStreamed(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Source[ContentBlockDelta, NotUsed] = {
    val coreBodyParams =
      createBodyParamsForMessageCreation(messages, settings, stream = None, ignoreModel = true)
    val bodyParams =
      coreBodyParams :+ (Param.anthropic_version -> Some(JsString(bedrockAnthropicVersion)))

    val stringParams = paramTuplesToStrings(bodyParams)
    val jsBodyObject = toJsBodyObject(stringParams)
    val endpoint = invokeWithResponseStreamEndpoint(settings.model)

    val extraHeaders = createSignatureHeaders(
      "POST",
      createURL(Some(endpoint)),
      headers = requestContext.authHeaders,
      jsBodyObject
    )

    engine
      .execRawStream(
        endpoint,
        "POST",
        endPointParam = None,
        params = Nil,
        bodyParams = stringParams,
        extraHeaders = extraHeaders
      )
      .via(
        Framing.delimiter(
          ByteString(":content-type"),
          maximumFrameLength = 65536,
          FramingTruncation.ALLOW
        )
      )
      .via(AwsEventStreamEventParser.flow) // parse frames into JSON with "bytes"
      .collect { case Some(x) => x }
      .via(AwsEventStreamBytesDecoder.flow) // decode the "
      .map(serializeStreamedJson)
      .collect { case Some(delta) => delta }
  }

  protected def createSignatureHeaders(
    method: String,
    url: String,
    headers: Seq[(String, String)],
    body: JsValue
  ): Seq[(String, String)] = {
    val connectionSettings = connectionInfo

    addAuthHeaders(
      method,
      url,
      headers.toMap,
      PlayJsonUtil.wsClientStringify(body),
      accessKey = connectionSettings.accessKey,
      secretKey = connectionSettings.secretKey,
      region = connectionSettings.region,
      service = serviceName
    ).toSeq
  }

  // Skills API is not supported in Bedrock
  override def createSkill(
    displayTitle: Option[String],
    files: Seq[(File, String)]
  ): Future[Skill] =
    Future.failed(
      new UnsupportedOperationException("Skills API is not supported in Anthropic Bedrock")
    )

  override def listSkills(
    page: Option[String],
    limit: Option[Int],
    source: Option[SkillSource]
  ): Future[ListSkillsResponse] =
    Future.failed(
      new UnsupportedOperationException("Skills API is not supported in Anthropic Bedrock")
    )

  override def getSkill(skillId: String): Future[Skill] =
    Future.failed(
      new UnsupportedOperationException("Skills API is not supported in Anthropic Bedrock")
    )

  override def deleteSkill(skillId: String): Future[DeleteSkillResponse] =
    Future.failed(
      new UnsupportedOperationException("Skills API is not supported in Anthropic Bedrock")
    )

  override def createSkillVersion(
    skillId: String,
    files: Seq[(File, String)]
  ): Future[SkillVersion] =
    Future.failed(
      new UnsupportedOperationException("Skills API is not supported in Anthropic Bedrock")
    )

  override def listSkillVersions(
    skillId: String,
    page: Option[String],
    limit: Option[Int]
  ): Future[ListSkillVersionsResponse] =
    Future.failed(
      new UnsupportedOperationException("Skills API is not supported in Anthropic Bedrock")
    )

  override def getSkillVersion(
    skillId: String,
    version: String
  ): Future[SkillVersion] =
    Future.failed(
      new UnsupportedOperationException("Skills API is not supported in Anthropic Bedrock")
    )

  override def deleteSkillVersion(
    skillId: String,
    version: String
  ): Future[DeleteSkillVersionResponse] =
    Future.failed(
      new UnsupportedOperationException("Skills API is not supported in Anthropic Bedrock")
    )

  override def createFile(
    file: File,
    filename: Option[String] = None
  ): Future[FileMetadata] =
    Future.failed(
      new UnsupportedOperationException("Files API is not supported in Anthropic Bedrock")
    )

  override def listFiles(
    beforeId: Option[String] = None,
    afterId: Option[String] = None,
    limit: Option[Int] = None
  ): Future[FileListResponse] =
    Future.failed(
      new UnsupportedOperationException("Files API is not supported in Anthropic Bedrock")
    )

  override def getFileMetadata(fileId: String): Future[Option[FileMetadata]] =
    Future.failed(
      new UnsupportedOperationException("Files API is not supported in Anthropic Bedrock")
    )

  override def downloadFile(fileId: String): Future[Option[Source[ByteString, _]]] =
    Future.failed(
      new UnsupportedOperationException("Files API is not supported in Anthropic Bedrock")
    )

  override def deleteFile(fileId: String): Future[FileDeleteResponse] =
    Future.failed(
      new UnsupportedOperationException("Files API is not supported in Anthropic Bedrock")
    )

  def connectionInfo: BedrockConnectionSettings
}

case class BedrockConnectionSettings(
  accessKey: String,
  secretKey: String,
  region: String
)
