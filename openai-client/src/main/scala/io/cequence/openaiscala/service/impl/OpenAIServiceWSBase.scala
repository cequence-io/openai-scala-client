package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.service.OpenAIServiceConsts
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine
import io.cequence.wsclient.stream.{AsyncStream, Bytes}
import play.api.libs.json.JsValue

import scala.concurrent.Future

trait OpenAIServiceWSBase extends WSClientWithEngine with OpenAIServiceConsts {

  override protected type PEP = EndPoint
  override protected type PT = Param

  /**
   * ws-client's `execStreamDownload` for a resource that must exist: a 404 fails like any
   * other non-2xx status (through `handleErrorCodes`). Completes once the response started,
   * with a SINGLE-RUN stream of the body - binary-safe on every engine (an engine without
   * output streaming serves the buffered body as one chunk).
   */
  protected def execStreamDownloadExisting(
    endPoint: PEP,
    method: String,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    bodyParams: Seq[(PT, Option[JsValue])] = Nil,
    extraHeaders: Seq[(String, String)] = Nil
  ): Future[AsyncStream[Bytes]] =
    execStreamDownload(endPoint, method, endPointParam, params, bodyParams, extraHeaders).map(
      _.getOrElse(handleErrorCodes(404, s"'$endPoint' not found."))
    )
}
