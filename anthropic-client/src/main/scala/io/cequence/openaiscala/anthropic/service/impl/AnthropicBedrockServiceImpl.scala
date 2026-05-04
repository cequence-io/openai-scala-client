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
import play.api.libs.json.{JsString, JsValue}

import java.io.File
import scala.concurrent.Future

/**
 * Anthropic-on-Bedrock service implementation. Speaks the Anthropic Messages API over AWS
 * Bedrock's `model/{modelId}/invoke` and `invoke-with-response-stream` endpoints.
 *
 * Auth is dispatched in [[createSignatureHeaders]] based on [[BedrockConnectionSettings]]:
 *   - if `bearerToken` is set, SigV4 is bypassed and the token is sent as `Authorization:
 *     Bearer <token>`;
 *   - otherwise the request is SigV4-signed with `accessKey`/`secretKey`/`region`, plus
 *     `X-Amz-Security-Token` when `sessionToken` is set.
 *
 * The Skills and Files APIs are not exposed by Bedrock and fail with
 * `UnsupportedOperationException`.
 */
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
    // Bedrock does not support output_format (structured outputs) - skip it
    if (settings.output_format.isDefined) {
      logger.warn(
        "Anthropic Bedrock does not support 'output_format' (structured outputs). " +
          "The output_format parameter will be ignored. Consider using tool use with JSON schema " +
          "or prompt engineering to get structured JSON responses."
      )
    }
    val coreBodyParams =
      createBodyParamsForMessageCreation(
        messages,
        settings,
        stream = None,
        ignoreModel = true,
        ignoreOutputFormat = true
      )
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
    // Bedrock does not support output_format (structured outputs) - skip it
    if (settings.output_format.isDefined) {
      logger.warn(
        "Anthropic Bedrock does not support 'output_format' (structured outputs). " +
          "The output_format parameter will be ignored. Consider using tool use with JSON schema " +
          "or prompt engineering to get structured JSON responses."
      )
    }
    val coreBodyParams =
      createBodyParamsForMessageCreation(
        messages,
        settings,
        stream = None,
        ignoreModel = true,
        ignoreOutputFormat = true
      )
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

    connectionSettings.bearerToken match {
      case Some(token) =>
        // Bedrock API key - skip SigV4 entirely.
        headers :+ ("Authorization" -> s"Bearer $token")

      case None =>
        addAuthHeaders(
          method,
          url,
          headers.toMap,
          PlayJsonUtil.wsClientStringify(body),
          accessKey = connectionSettings.accessKey,
          secretKey = connectionSettings.secretKey,
          region = connectionSettings.region,
          service = serviceName,
          sessionToken = connectionSettings.sessionToken
        ).toSeq
    }
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

/**
 * Connection settings for Anthropic-on-Bedrock. Encodes one of three mutually-exclusive auth
 * modes:
 *
 *   1. Static IAM user — accessKey + secretKey only (long-lived AKIA-prefixed creds). 2. STS
 *      temporary credentials — accessKey + secretKey + sessionToken (the ASIA triple from `aws
 *      sts get-session-token` / `assume-role` / IMDS). 3. Bearer token — bearerToken set;
 *      SigV4 bypassed, accessKey/secretKey ignored.
 *
 * @param accessKey
 *   AWS access key (AKIA for static, ASIA for STS). Ignored when bearerToken is set.
 * @param secretKey
 *   Secret matching accessKey. Ignored when bearerToken is set.
 * @param region
 *   AWS region for Bedrock (e.g. us-east-1, eu-central-1). Required in all modes.
 * @param inferenceProfilePrefix
 *   Optional cross-region inference profile prefix (e.g. "us." or "eu.") prepended to the
 *   model id at request time when not already present.
 * @param sessionToken
 *   STS session token. When set, included as `X-Amz-Security-Token` in SigV4 signed headers.
 *   Must come from the same STS triple as accessKey/secretKey.
 * @param bearerToken
 *   Bedrock API key. When set, SigV4 is skipped entirely and the token is sent as
 *   `Authorization: Bearer <token>`. Mutually exclusive with the SigV4 fields.
 */
case class BedrockConnectionSettings(
  accessKey: String,
  secretKey: String,
  region: String,
  inferenceProfilePrefix: Option[String] = None,
  sessionToken: Option[String] = None,
  bearerToken: Option[String] = None
)
