package io.cequence.openaiscala.gemini.service.impl

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.gemini.domain.response.{
  GenerateContentResponse,
  ListCachedContentsResponse,
  ListModelsResponse
}
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.domain.{CachedContent, Content, Expiration}
import io.cequence.openaiscala.gemini.service.GeminiService
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithStreamEngine
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import io.cequence.wsclient.service.{WSClientEngine, WSClientEngineStreamExtra}
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

private[service] class GeminiServiceImpl(
  apiKey: String
)(
  override implicit val ec: ExecutionContext,
  implicit val materializer: Materializer
) extends GeminiService
    with WSClientWithStreamEngine {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override protected val engine: WSClientEngine with WSClientEngineStreamExtra =
    PlayWSStreamClientEngine(
      coreUrl,
      WsRequestContext(
        extraParams = Seq(
          Param.key.toString() -> apiKey
        )
      )
    )

  override def generateContent(
    contents: Seq[Content],
    settings: GenerateContentSettings
  ): Future[GenerateContentResponse] =
    execPOST(
      EndPoint.generateContent(settings.model),
      bodyParams = createBodyParams(contents, settings)
    ).map(
      _.asSafeJson[GenerateContentResponse]
    )

  override def generateContentStreamed(
    contents: Seq[Content],
    settings: GenerateContentSettings
  ): Source[GenerateContentResponse, NotUsed] = {
    val bodyParams = createBodyParams(contents, settings)
    val stringParams = paramTuplesToStrings(bodyParams)

    engine
      .execJsonStream(
        EndPoint.streamGenerateContent(settings.model).toString(),
        "POST",
        bodyParams = stringParams,
        maxFrameLength = Some(20000),
        framingDelimiter = "\n,\r\n",
        stripPrefix = Some("["),
        stripSuffix = Some("]")
      )
      .map { json =>
        (json \ "error").toOption.map { error =>
          throw new OpenAIScalaClientException(error.toString())
        }.getOrElse {
          json.asSafe[GenerateContentResponse]
        }
      }
  }

  private def createBodyParams(
    contents: Seq[Content],
    settings: GenerateContentSettings
  ): Seq[(Param, Option[JsValue])] = {
    assert(contents.nonEmpty, "At least one content/message expected.")

    jsonBodyParams(
      Param.contents -> Some(Json.toJson(contents)),
      Param.tools -> settings.tools.map(Json.toJson(_)),
      Param.tool_config -> settings.toolConfig.map(Json.toJson(_)),
      Param.safety_settings -> settings.safetySettings.map(Json.toJson(_)),
      Param.system_instruction -> settings.systemInstruction.map(Json.toJson(_)),
      Param.generation_config -> settings.generationConfig.map(Json.toJson(_)),
      Param.cached_content -> settings.cachedContent.map(Json.toJson(_))
    )
  }

  override def listModels(
    pageSize: Option[Int],
    pageToken: Option[String]
  ): Future[ListModelsResponse] =
    execGET(
      EndPoint.models,
      params = Seq(
        Param.page_size -> pageSize,
        Param.page_token -> pageToken
      )
    ).map(
      _.asSafeJson[ListModelsResponse]
    )

  override def listCachedContents(
    pageSize: Option[Int],
    pageToken: Option[String]
  ): Future[ListCachedContentsResponse] =
    execGET(
      EndPoint.cachedContents,
      params = Seq(
        Param.page_size -> pageSize,
        Param.page_token -> pageToken
      )
    ).map(
      _.asSafeJson[ListCachedContentsResponse]
    )

  override def createCachedContent(
    cachedContent: CachedContent
  ): Future[CachedContent] =
    execPOSTBody(
      EndPoint.cachedContents,
      body = Json.toJson(cachedContent)(cachedContentFormat)
    ).map(
      _.asSafeJson[CachedContent]
    )

  override def updateCachedContent(
    name: String,
    expiration: Expiration
  ): Future[CachedContent] = {
    val (updateMask, value) = expiration match {
      case Expiration.ExpireTime(value) =>
        (Param.expireTime, value)
      case Expiration.TTL(value) =>
        (Param.ttl, value)
    }

    execPATCH(
      EndPoint.cachedContents(name),
      bodyParams = jsonBodyParams(
//        Param.name -> Some(name),
//        Param.updateMask -> Some(updateMask),
//       Param.cachedContent -> Some(Json.obj(updateMask -> JsString(value)))
        updateMask -> Some(value)
      )
    ).map(
      _.asSafeJson[CachedContent]
    )
  }

  override def getCachedContent(name: String): Future[CachedContent] =
    execGET(
      EndPoint.cachedContents(name)
    ).map(
      _.asSafeJson[CachedContent]
    )

  override def deleteCachedContent(name: String): Future[Unit] =
    execDELETE(
      EndPoint.cachedContents(name)
    ).map(_ => ())
}
