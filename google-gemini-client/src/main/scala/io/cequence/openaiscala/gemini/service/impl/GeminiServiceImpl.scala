package io.cequence.openaiscala.gemini.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.gemini.domain.response.{
  GenerateContentResponse,
  ListCachedContentsResponse,
  ListModelsResponse
}
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.domain.{
  BatchOutput,
  BatchRequestItem,
  BatchRpcError,
  CachedContent,
  Content,
  Expiration,
  GeminiFile,
  GenerateContentBatch,
  ListBatchesResponse
}
import io.cequence.openaiscala.gemini.service.{GeminiService, HandleGeminiErrorCodes}
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientWithEngineStreamTypes.WSClientWithOutputStreamEngine
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}
import play.api.libs.json._

import java.io.File
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files => JFiles}
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try
import io.cequence.wsclient.service.ws.Timeouts

/**
 * @param timeouts
 *   client-level timeouts for the PRIVATELY-created engine - mutually exclusive with
 *   `externalEngine` (the factory passes one or the other, never both): a caller-supplied
 *   engine already carries its own `TransportSettings`, so `timeouts` is ignored with it -
 *   pass `engine.copy(TransportSettings(timeouts = ...))` instead if that engine's timeouts
 *   don't fit
 * @param externalEngine
 *   a caller-supplied, site-stateless engine (e.g. one shared with other providers); not
 *   closed by this service
 */
private[service] class GeminiServiceImpl(
  apiKey: String,
  timeouts: Option[Timeouts] = None,
  externalEngine: Option[WSClientEngine with WSClientOutputStreamExtraAkka] = None
)(
  override implicit val ec: ExecutionContext
) extends GeminiService
    with HandleGeminiErrorCodes
    with WSClientWithOutputStreamEngine {

  override protected type PEP = EndPoint
  override protected type PT = Param

  // a caller-supplied, site-stateless engine (e.g. one shared with other providers), or a
  // privately-owned classpath-discovered engine with output streaming (SSE) support; note that
  // the raw upload/download paths below use the apiKey directly, engine or not
  override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
    externalEngine.getOrElse(
      StreamedEngineRegistry.outputStreamed(
        TransportSettings(timeouts = timeouts.getOrElse(Timeouts()))
      )
    )

  // the engine is only closed by this service when it was privately created here - a
  // caller-supplied engine is closed by its creator
  override protected def ownsEngine: Boolean = externalEngine.isEmpty

  override protected val site: SiteBinding =
    SiteBinding(
      coreUrl,
      WsRequestContext(extraParams = Seq(Param.key.toString() -> apiKey)),
      label = Some("gemini")
    )

  override def generateContent(
    contents: Seq[Content],
    settings: GenerateContentSettings
  ): Future[GenerateContentResponse] =
    execPOST(
      EndPoint.generateContent(settings.model),
      bodyParams = createBodyParams(contents, settings)
    ).map(
      _.asSafeJson[GenerateContentResponse].copy(cachedContent = settings.cachedContent)
    )

  override def generateContentStreamed(
    contents: Seq[Content],
    settings: GenerateContentSettings
  ): Source[GenerateContentResponse, NotUsed] = {
    val bodyParams = createBodyParams(contents, settings)
    val stringParams = paramTuplesToStrings(bodyParams)

    engine
      .execJsonStream(
        site,
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
          json.asSafe[GenerateContentResponse].copy(cachedContent = settings.cachedContent)
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

  // -- Batches (Batch Mode) --

  override def createBatchGenerateContent(
    displayName: String,
    requests: Seq[BatchRequestItem],
    settings: GenerateContentSettings,
    priority: Option[Long],
    useFileInput: Boolean
  ): Future[GenerateContentBatch] = {
    require(requests.nonEmpty, "At least one batch request expected.")

    val keyedRequestBodies = requests.map { item =>
      val baseRequestBody = JsObject(
        createBodyParams(item.contents, settings).collect { case (param, Some(value)) =>
          param.toString -> value
        }
      )

      // a per-item systemInstruction (if set on the BatchRequestItem) overrides the
      // batch-wide (settings-level) one for this request only; when unset, the batch-wide
      // systemInstruction (e.g. from explicit caching) is kept as-is.
      val requestBody = withOverriddenContentField(
        baseRequestBody,
        Param.system_instruction.toString,
        item.systemInstruction
      )

      item.key -> requestBody
    }

    def createBatch(inputConfigJson: JsObject): Future[GenerateContentBatch] = {
      val batchJson = Json.obj(
        "display_name" -> displayName,
        "input_config" -> inputConfigJson
      ) ++ priority.map(p => Json.obj("priority" -> p)).getOrElse(Json.obj())

      execPOSTBody(
        EndPoint.batchGenerateContent(settings.model),
        body = Json.obj("batch" -> batchJson)
      ).map(response => toBatch(response.json))
    }

    val inlineRequestJsons = keyedRequestBodies.map { case (key, requestBody) =>
      Json.obj(
        "request" -> requestBody,
        "metadata" -> Json.obj("key" -> key)
      )
    }

    val estimatedPayloadBytes =
      inlineRequestJsons.foldLeft(0L)(
        (
          size,
          json
        ) => size + json.toString().length
      )

    if (!useFileInput && estimatedPayloadBytes <= inlineBatchPayloadLimitBytes)
      createBatch(Json.obj("requests" -> Json.obj("requests" -> JsArray(inlineRequestJsons))))
    else {
      // too large for the 20 MB inline limit (or forced) - stage the requests as a JSONL file
      val lines = keyedRequestBodies.map { case (key, requestBody) =>
        Json.obj("key" -> key, "request" -> requestBody).toString()
      }

      val tempPath = JFiles.createTempFile("gemini-batch-", ".jsonl")
      JFiles.write(tempPath, lines.mkString("\n").getBytes(StandardCharsets.UTF_8))

      val result = for {
        file <- uploadFile(
          tempPath.toFile,
          displayName = Some(displayName),
          mimeType = Some("application/jsonl")
        )
        batch <- createBatch(Json.obj("file_name" -> file.name))
      } yield batch

      result.andThen { case _ => Try(JFiles.deleteIfExists(tempPath)) }
    }
  }

  // stay well below the documented 20 MB inline-request cap (envelope + escaping overhead)
  private val inlineBatchPayloadLimitBytes = 15L * 1024 * 1024

  override def getBatch(name: String): Future[GenerateContentBatch] =
    execGET(
      EndPoint.batches(name)
    ).map(response => toBatch(response.json))

  override def listBatches(
    pageSize: Option[Int],
    pageToken: Option[String]
  ): Future[ListBatchesResponse] =
    execGET(
      EndPoint.batches,
      params = Seq(
        Param.page_size -> pageSize,
        Param.page_token -> pageToken
      )
    ).map { response =>
      val json = response.json
      ListBatchesResponse(
        batches = (json \ "operations").asOpt[Seq[JsValue]].getOrElse(Nil).map(toBatch),
        nextPageToken = (json \ "nextPageToken").asOpt[String]
      )
    }

  override def cancelBatch(name: String): Future[Unit] =
    execPOST(
      EndPoint.cancelBatch(name)
    ).map(_ => ())

  override def deleteBatch(name: String): Future[Unit] =
    execDELETE(
      EndPoint.batches(name)
    ).map(_ => ())

  // -- Files --

  override def uploadFile(
    file: File,
    displayName: Option[String],
    mimeType: Option[String]
  ): Future[GeminiFile] = Future {
    blocking {
      val fileLength = file.length()
      val contentType = mimeType.getOrElse("application/octet-stream")

      // step 1: start a resumable upload - the actual upload URL rides on a response header
      val startConnection = openConnection(s"${uploadBaseUrl}files", "POST")
      startConnection.setDoOutput(true)
      startConnection.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
      startConnection.setRequestProperty("X-Goog-Upload-Command", "start")
      startConnection.setRequestProperty(
        "X-Goog-Upload-Header-Content-Length",
        fileLength.toString
      )
      startConnection.setRequestProperty("X-Goog-Upload-Header-Content-Type", contentType)
      startConnection.setRequestProperty("Content-Type", "application/json")

      val metadata = Json
        .obj(
          "file" -> JsObject(
            displayName.map(name => "display_name" -> (JsString(name): JsValue)).toSeq
          )
        )
        .toString()

      val startOut = startConnection.getOutputStream
      try startOut.write(metadata.getBytes(StandardCharsets.UTF_8))
      finally startOut.close()

      handleRawResponse(startConnection, "start of the file upload")

      val uploadUrl = Option(startConnection.getHeaderField("X-Goog-Upload-URL")).getOrElse(
        throw new OpenAIScalaClientException(
          "The file-upload start response carries no X-Goog-Upload-URL header."
        )
      )

      // step 2: upload the bytes and finalize - streamed from disk in fixed-size chunks so
      // neither the file (up to the 2 GB Files API limit) nor its HttpURLConnection copy is
      // ever held in memory in full
      val uploadConnection =
        new URL(uploadUrl).openConnection().asInstanceOf[HttpURLConnection]
      uploadConnection.setRequestMethod("POST")
      uploadConnection.setDoOutput(true)
      uploadConnection.setFixedLengthStreamingMode(fileLength)
      uploadConnection.setRequestProperty("X-Goog-Upload-Offset", "0")
      uploadConnection.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")

      val uploadOut = uploadConnection.getOutputStream
      val fileIn = new java.io.FileInputStream(file)
      try {
        val buffer = new Array[Byte](8192)
        var read = fileIn.read(buffer)
        while (read != -1) {
          uploadOut.write(buffer, 0, read)
          read = fileIn.read(buffer)
        }
      } finally {
        fileIn.close()
        uploadOut.close()
      }

      val responseBody = handleRawResponse(uploadConnection, "finalization of the file upload")
      (Json.parse(responseBody) \ "file").as[GeminiFile]
    }
  }

  override def getFile(name: String): Future[GeminiFile] =
    execGET(
      EndPoint.files(name)
    ).map(_.asSafeJson[GeminiFile])

  override def deleteFile(name: String): Future[Unit] =
    execDELETE(
      EndPoint.files(name)
    ).map(_ => ())

  override def downloadFile(name: String): Future[String] = Future {
    blocking {
      val fileId = name.stripPrefix("files/")
      val connection =
        openConnection(s"${downloadBaseUrl}files/$fileId:download?alt=media", "GET")
      handleRawResponse(connection, s"download of the file '$name'")
    }
  }

  // the upload/download endpoints live under dedicated base paths and use raw bodies,
  // which the JSON ws engine does not support - hence plain HTTP connections
  private lazy val uploadBaseUrl = coreUrl.replace("/v1beta/", "/upload/v1beta/")
  private lazy val downloadBaseUrl = coreUrl.replace("/v1beta/", "/download/v1beta/")

  private def openConnection(
    url: String,
    method: String
  ): HttpURLConnection = {
    val separator = if (url.contains("?")) "&" else "?"
    val connection =
      new URL(s"$url${separator}key=$apiKey").openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestMethod(method)
    connection
  }

  private def handleRawResponse(
    connection: HttpURLConnection,
    operation: String
  ): String = {
    val status = connection.getResponseCode
    val stream = if (status >= 400) connection.getErrorStream else connection.getInputStream

    val body =
      if (stream == null) ""
      else {
        val buffer = new java.io.ByteArrayOutputStream()
        val chunk = new Array[Byte](8192)
        var read = stream.read(chunk)
        while (read != -1) {
          buffer.write(chunk, 0, read)
          read = stream.read(chunk)
        }
        stream.close()
        buffer.toString(StandardCharsets.UTF_8.name())
      }

    if (status >= 400)
      throw new OpenAIScalaClientException(
        s"Gemini $operation failed with the status $status: $body"
      )

    body
  }

  /**
   * The batches endpoints return a long-running Operation whose `metadata` carries the
   * [[GenerateContentBatch]]; once done, results may additionally ride on the `response`
   * union. Unwrap both (tolerating a bare batch object too).
   */
  private def toBatch(json: JsValue): GenerateContentBatch = {
    val batchJson = (json \ "metadata").asOpt[JsObject].getOrElse(json.as[JsObject])
    val batch = batchJson.asSafe[GenerateContentBatch]

    val responseOutput = (json \ "response").asOpt[JsObject].flatMap { response =>
      val direct = response.asSafe[BatchOutput]
      if (direct.responsesFile.isDefined || direct.inlinedResponses.nonEmpty)
        Some(direct)
      else
        (response \ "output").asOpt[BatchOutput]
    }

    batch.copy(
      output = responseOutput.orElse(batch.output),
      done = (json \ "done").asOpt[Boolean],
      error = (json \ "error").asOpt[BatchRpcError]
    )
  }
}
