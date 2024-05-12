package io.cequence.openaiscala.service.ws

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import io.cequence.openaiscala.JsonUtil.toJson
import io.cequence.openaiscala._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.{BodyWritable, StandaloneWSRequest}
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.JsonBodyReadables._
import MultipartWritable.writeableOf_MultipartFormData
import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.io.File
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Either

/**
 * Base class for web services with handy GET, POST, and DELETE request builders, and response
 * handling
 *
 * @since Jan
 *   2023
 */
trait WSRequestHelper extends HasWSClient {

  protected val coreUrl: String

  protected implicit val ec: ExecutionContext

  protected type PEP
  protected type PT

  protected val serviceName: String = getClass.getSimpleName

  private val defaultAcceptableStatusCodes = Seq(200, 201)

  protected type RichResponse[T] = Either[T, (Int, String)]
  protected type RichJsResponse = RichResponse[JsValue]
  protected type RichStringResponse = RichResponse[String]
  protected type RichSourceResponse = RichResponse[Source[ByteString, _]]

  /////////
  // GET //
  /////////

  protected def execGET(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil
  ): Future[JsValue] =
    execGETWithStatus(
      endPoint,
      endPointParam,
      params
    ).map(handleErrorResponse)

  protected def execGETWithStatus(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, toStringParams(params))

    execGETJsonAux(request, Some(endPoint), acceptableStatusCodes)
  }

  protected def execGETJsonAux(
    request: StandaloneWSRequest,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] =
    execRequestAux(ResponseConverters.json)(
      request,
      _.get(),
      acceptableStatusCodes,
      endPointForLogging
    )

  protected def execGETStringAux(
    request: StandaloneWSRequest,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichStringResponse] =
    execRequestAux(ResponseConverters.string)(
      request,
      _.get(),
      acceptableStatusCodes,
      endPointForLogging
    )

  //////////
  // POST //
  //////////

  /**
   * @param fileParams
   *   the third param in a tuple is a display (header) file name
   */
  protected def execPOSTMultipart(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    fileParams: Seq[(PT, File, Option[String])] = Nil,
    bodyParams: Seq[(PT, Option[Any])] = Nil
  ): Future[JsValue] =
    execPOSTMultipartWithStatus(
      endPoint,
      endPointParam,
      params,
      fileParams,
      bodyParams
    ).map(handleErrorResponse)

  /**
   * @param fileParams
   *   the third param in a tuple is a display (header) file name
   */
  protected def execPOSTMultipartWithStatus(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    fileParams: Seq[(PT, File, Option[String])] = Nil,
    bodyParams: Seq[(PT, Option[Any])] = Nil,
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, toStringParams(params))
    val formData = createMultipartFormData(fileParams, bodyParams)

    implicit val writeable: BodyWritable[MultipartFormData] =
      writeableOf_MultipartFormData("utf-8")

    execPOSTJsonAux(request, formData, Some(endPoint), acceptableStatusCodes)
  }

  /**
   * @param fileParams
   *   the third param in a tuple is a display (header) file name
   */
  protected def execPOSTMultipartWithStatusString(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    fileParams: Seq[(PT, File, Option[String])] = Nil,
    bodyParams: Seq[(PT, Option[Any])] = Nil,
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichStringResponse] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, toStringParams(params))
    val formData = createMultipartFormData(fileParams, bodyParams)

    implicit val writeable: BodyWritable[MultipartFormData] =
      writeableOf_MultipartFormData("utf-8")

    execPOSTStringAux(request, formData, Some(endPoint), acceptableStatusCodes)
  }

  // create a multipart form data holder contain classic data (key-value) parts as well as file parts
  private def createMultipartFormData(
    fileParams: Seq[(PT, File, Option[String])],
    bodyParams: Seq[(PT, Option[Any])] = Nil
  ) = MultipartFormData(
    dataParts = bodyParams.collect { case (key, Some(value)) =>
      (key.toString, Seq(value.toString))
    }.toMap,
    files = fileParams.map { case (key, file, headerFileName) =>
      FilePart(key.toString, file.getPath, headerFileName)
    }
  )

  protected def execPOST(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    bodyParams: Seq[(PT, Option[JsValue])] = Nil
  ): Future[JsValue] =
    execPOSTWithStatus(
      endPoint,
      endPointParam,
      params,
      bodyParams
    ).map(handleErrorResponse)

  protected def execPOSTWithStatus(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    bodyParams: Seq[(PT, Option[JsValue])] = Nil,
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, toStringParams(params))
    val bodyParamsX = bodyParams.collect { case (fieldName, Some(jsValue)) =>
      (fieldName.toString, jsValue)
    }

    execPOSTJsonAux(
      request,
      JsObject(bodyParamsX),
      Some(endPoint),
      acceptableStatusCodes
    )
  }

  protected def execPOSTSource(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    bodyParams: Seq[(PT, Option[JsValue])] = Nil
  ): Future[Source[ByteString, _]] =
    execPOSTSourceWithStatus(
      endPoint,
      endPointParam,
      params,
      bodyParams
    ).map(handleErrorResponse)

  protected def execPOSTSourceWithStatus(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    bodyParams: Seq[(PT, Option[JsValue])] = Nil,
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichSourceResponse] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, toStringParams(params))
    val bodyParamsX = bodyParams.collect { case (fieldName, Some(jsValue)) =>
      (fieldName.toString, jsValue)
    }

    execPOSTSourceAux(
      request,
      JsObject(bodyParamsX),
      Some(endPoint),
      acceptableStatusCodes
    )
  }

  protected def execPOSTJsonAux[T: BodyWritable](
    request: StandaloneWSRequest,
    body: T,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] =
    execRequestAux(ResponseConverters.json)(
      request,
      _.post(body),
      acceptableStatusCodes,
      endPointForLogging
    )

  protected def execPOSTStringAux[T: BodyWritable](
    request: StandaloneWSRequest,
    body: T,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichStringResponse] =
    execRequestAux(ResponseConverters.string)(
      request,
      _.post(body),
      acceptableStatusCodes,
      endPointForLogging
    )

  protected def execPOSTSourceAux[T: BodyWritable](
    request: StandaloneWSRequest,
    body: T,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichSourceResponse] =
    execRequestAux(ResponseConverters.source)(
      request,
      _.post(body),
      acceptableStatusCodes,
      endPointForLogging
    )

  ////////////
  // DELETE //
  ////////////

  protected def execDELETE(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil
  ): Future[JsValue] =
    execDELETEWithStatus(
      endPoint,
      endPointParam,
      params
    ).map(handleErrorResponse)

  protected def execDELETEWithStatus(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] = {
    val request = getWSRequestOptional(Some(endPoint), endPointParam, toStringParams(params))

    execDeleteAux(request, Some(endPoint), acceptableStatusCodes)
  }

  private def execDeleteAux(
    request: StandaloneWSRequest,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] =
    execRequestAux(ResponseConverters.json)(
      request,
      _.delete(),
      acceptableStatusCodes,
      endPointForLogging
    )

  ////////////////
  // WS Request //
  ////////////////

  protected def getWSRequest(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(String, Any)]
  ): StandaloneWSRequest = {
    val paramsString = paramsAsString(params)
    val url = createUrl(endPoint, endPointParam) + paramsString

    client.url(url)
  }

  protected def getWSRequestOptional(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])]
  ): StandaloneWSRequest = {
    val paramsString = paramsOptionalAsString(params)
    val url = createUrl(endPoint, endPointParam) + paramsString

    client.url(url)
  }

  protected def execPOSTWithStatusAndHeaders(
    endPoint: PEP,
    endPointParam: Option[String] = None,
    params: Seq[(PT, Option[Any])] = Nil,
    bodyParams: Seq[(PT, Option[JsValue])] = Nil,
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes,
    headers: Seq[(String, String)] = Nil
  ): Future[RichJsResponse] = {
    val request = getWSRequestOptionalWithHeaders(
      Some(endPoint),
      endPointParam,
      toStringParams(params),
      headers
    )
    val bodyParamsX = bodyParams.collect { case (fieldName, Some(jsValue)) =>
      (fieldName.toString, jsValue)
    }

    execPOSTJsonAux(
      request,
      JsObject(bodyParamsX),
      Some(endPoint),
      acceptableStatusCodes
    )
  }

  protected def getWSRequestOptionalWithHeaders(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(String, Option[Any])],
    headers: Seq[(String, String)]
  ) = {
    val paramsString = paramsOptionalAsString(params)
    val url = createUrl(endPoint, endPointParam) + paramsString
    client.url(url).addHttpHeaders(headers: _*)
  }

  private def execRequestAux[T](
    responseConverter: ResponseConverters.ResponseConverter[T]
  )(
    request: StandaloneWSRequest,
    exec: StandaloneWSRequest => Future[StandaloneWSRequest#Response],
    acceptableStatusCodes: Seq[Int],
    endPointForLogging: Option[PEP] // only for logging
  ): Future[RichResponse[T]] =
    execRequestRaw(
      request,
      exec,
      acceptableStatusCodes,
      endPointForLogging
    ).map(_ match {
      case Left(response) =>
        Left(responseConverter.apply(response, endPointForLogging))
      case Right(response) =>
        Right(response)
    })

  private object ResponseConverters {

    type ResponseConverter[T] = (
      StandaloneWSRequest#Response,
      Option[PEP] // for logging
    ) => T

    val string: ResponseConverter[String] = {
      (
        response,
        _
      ) => response.body
    }

    val source: ResponseConverter[Source[ByteString, _]] = {
      (
        response,
        _
      ) => response.bodyAsSource
    }

    val json: ResponseConverter[JsValue] = {
      (
        response,
        endPointForLogging
      ) =>
        try {
          response.body[JsValue]
        } catch {
          case _: JsonParseException =>
            throw new OpenAIScalaClientException(
              s"$serviceName.${endPointForLogging.map(_.toString).getOrElse("")}: '${response.body}' is not a JSON."
            )
          case _: JsonMappingException =>
            throw new OpenAIScalaClientException(
              s"$serviceName.${endPointForLogging.map(_.toString).getOrElse("")}: '${response.body}' is an unmappable JSON."
            )
        }
    }
  }

  private def execRequestRaw(
    request: StandaloneWSRequest,
    exec: StandaloneWSRequest => Future[StandaloneWSRequest#Response],
    acceptableStatusCodes: Seq[Int] = Nil,
    endPointForLogging: Option[PEP] = None // only for logging
  ): Future[Either[StandaloneWSRequest#Response, (Int, String)]] = {
    exec(request).map { response =>
      if (!acceptableStatusCodes.contains(response.status))
        Right((response.status, response.body))
      else
        Left(response)
    }
  }.recover {
    case e: TimeoutException =>
      throw new OpenAIScalaClientTimeoutException(
        s"$serviceName.${endPointForLogging.map(_.toString).getOrElse("")} timed out: ${e.getMessage}."
      )
    case e: UnknownHostException =>
      throw new OpenAIScalaClientUnknownHostException(
        s"$serviceName.${endPointForLogging.map(_.toString).getOrElse("")} cannot resolve a host name: ${e.getMessage}."
      )
  }

  // aux

  protected def jsonBodyParams[T](
    params: (T, Option[Any])*
  ): Seq[(T, Option[JsValue])] =
    params.map { case (paramName, value) => (paramName, value.map(toJson)) }

  protected def handleNotFoundAndError[T](response: RichResponse[T]): Option[T] =
    response match {
      case Left(value) =>
        Some(value)

      case Right((errorCode, _)) =>
        if (errorCode == 404) None
        else
          Some(handleErrorResponse(response))
    }

  protected def handleErrorResponse[T](response: RichResponse[T]): T =
    response match {
      case Left(data) => data

      case Right((errorCode, message)) =>
        handleErrorCodes(errorCode, message)
    }

  protected def handleErrorCodes(
    httpCode: Int,
    message: String
  ): Nothing =
    throw new CequenceWSException(s"Code ${httpCode} : ${message}")

  protected def paramsAsString(params: Seq[(String, Any)]): String = {
    val string =
      params.map { case (tag, value) => s"$tag=$value" }.mkString("&")

    if (string.nonEmpty) s"?$string" else ""
  }

  protected def paramsOptionalAsString(params: Seq[(String, Option[Any])]): String = {
    val string =
      params.collect { case (tag, Some(value)) => s"$tag=$value" }.mkString("&")

    if (string.nonEmpty) s"?$string" else ""
  }

  protected def createUrl(
    endpoint: Option[PEP],
    value: Option[String] = None
  ): String = {
    val slash = if (coreUrl.endsWith("/")) "" else "/"
    val endpointString = endpoint.map(_.toString).getOrElse("")
    val valueString = value.map("/" + _).getOrElse("")

    coreUrl + slash + endpointString + valueString
  }

  protected def toOptionalParams(
    params: Seq[(PT, Any)]
  ): Seq[(PT, Some[Any])] =
    params.map { case (a, b) => (a, Some(b)) }

  protected def toStringParams(
    params: Seq[(PT, Option[Any])]
  ): Seq[(String, Option[Any])] =
    params.map { case (a, b) => (a.toString, b) }
}
