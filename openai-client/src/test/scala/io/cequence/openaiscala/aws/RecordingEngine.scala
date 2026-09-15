package io.cequence.openaiscala.aws

import io.cequence.wsclient.domain.{RichResponse, SiteBinding}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.spi.TransportSettings
import io.cequence.wsclient.service.ws.FilePart
import play.api.libs.json.JsValue

import java.io.File
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/** Records the site and extra headers of every call; never does I/O. */
class RecordingEngine extends WSClientEngine {
  val calls: mutable.ListBuffer[(String, SiteBinding, Seq[(String, String)])] =
    mutable.ListBuffer.empty
  var closed = 0

  implicit val ec: ExecutionContext = ExecutionContext.global
  override def transportSettings: TransportSettings = TransportSettings()

  override def copy(
    transportSettings: TransportSettings,
    reuseExecContext: Boolean
  ): WSClientEngine = new RecordingEngine

  override def close(): Unit = closed += 1

  private def record(
    method: String,
    site: SiteBinding,
    headers: Seq[(String, String)]
  ): Future[RichResponse] = {
    calls += ((method, site, headers))
    Future.failed(new RuntimeException("recorded"))
  }

  override def execGETRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("GET", site, extraHeaders)

  override def execDELETERich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("DELETE", site, extraHeaders)

  override def execPOSTRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("POST", site, extraHeaders)

  override def execPATCHRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("PATCH", site, extraHeaders)

  override def execPUTRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[JsValue])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("PUT", site, extraHeaders)

  override def execPOSTBodyRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    body: JsValue,
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("POST-body", site, extraHeaders)

  override def execPUTBodyRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    body: JsValue,
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("PUT-body", site, extraHeaders)

  override def execPOSTMultipartRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    fileParams: Seq[(String, File, Option[String])],
    bodyParams: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int],
    useInMemoryBody: Boolean
  )(
    implicit filePartToContent: FilePart => String
  ): Future[RichResponse] = record("POST-multipart", site, extraHeaders)

  override def execPUTMultipartRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    fileParams: Seq[(String, File, Option[String])],
    bodyParams: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int],
    useInMemoryBody: Boolean
  )(
    implicit filePartToContent: FilePart => String
  ): Future[RichResponse] = record("PUT-multipart", site, extraHeaders)

  override def execPOSTFileRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    file: File,
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("POST-file", site, extraHeaders)

  override def execPUTFileRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    file: File,
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("PUT-file", site, extraHeaders)

  override def execPOSTURLEncodedRich(
    site: SiteBinding,
    endPoint: String,
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    bodyParams: Seq[(String, Option[Any])],
    extraHeaders: Seq[(String, String)],
    acceptableStatusCodes: Seq[Int]
  ): Future[RichResponse] = record("POST-form", site, extraHeaders)
}
