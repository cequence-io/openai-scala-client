package io.cequence.openaiscala.service.ws

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import io.cequence.openaiscala.JsonUtil.toJson
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaClientTimeoutException,
  OpenAIScalaClientUnknownHostException,
  OpenAIScalaTokenCountExceededException
}
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.ws.{BodyWritable, StandaloneWSRequest}
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.JsonBodyReadables._
import MultipartWritable.writeableOf_MultipartFormData
import java.io.File
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}

/**
 * Base class for web services with handy GET, POST, and DELETE request builders, and response
 * handling
 *
 * @since Jan
 *   2023
 */
trait WSRequestHelper extends WSHelper {

  protected val coreUrl: String

  protected implicit val ec: ExecutionContext

  protected type PEP
  protected type PT

  protected val serviceName: String = getClass.getSimpleName

  private val defaultAcceptableStatusCodes = Seq(200)

  protected type RichResponse[T] = Either[T, (Int, String)]
  protected type RichJsResponse = RichResponse[JsValue]
  protected type RichStringResponse = RichResponse[String]

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
    val request = getWSRequestOptional(Some(endPoint), endPointParam, params)

    execGETJsonAux(request, Some(endPoint), acceptableStatusCodes)
  }

  protected def execGETJsonAux(
    request: StandaloneWSRequest,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] =
    execRequestJsonAux(
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
    execRequestStringAux(
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
    val request = getWSRequestOptional(Some(endPoint), endPointParam, params)
    val formData = createMultipartFormData(fileParams, bodyParams)

    implicit val writeable: BodyWritable[MultipartFormData] = writeableOf_MultipartFormData("utf-8")

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
    val request = getWSRequestOptional(Some(endPoint), endPointParam, params)
    val formData = createMultipartFormData(fileParams, bodyParams)

    implicit val writeable: BodyWritable[MultipartFormData] = writeableOf_MultipartFormData("utf-8")

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
    val request = getWSRequestOptional(Some(endPoint), endPointParam, params)
    val bodyParamsX = bodyParams.collect { case (fieldName, Some(jsValue)) =>
      (fieldName.toString, jsValue)
    }

    execPOSTJsonAux(request, JsObject(bodyParamsX), Some(endPoint), acceptableStatusCodes)
  }

  protected def execPOSTJsonAux[T: BodyWritable](
    request: StandaloneWSRequest,
    body: T,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] =
    execRequestJsonAux(
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
    execRequestStringAux(
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
    val request = getWSRequestOptional(Some(endPoint), endPointParam, params)

    execDeleteAux(request, Some(endPoint), acceptableStatusCodes)
  }

  private def execDeleteAux(
    request: StandaloneWSRequest,
    endPointForLogging: Option[PEP], // only for logging
    acceptableStatusCodes: Seq[Int] = defaultAcceptableStatusCodes
  ): Future[RichJsResponse] =
    execRequestJsonAux(
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
    params: Seq[(PT, Any)]
  ): StandaloneWSRequest = {
    val paramsString = paramsAsString(params)
    val url = createUrl(endPoint, endPointParam) + paramsString

    client.url(url)
  }

  protected def getWSRequestOptional(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(PT, Option[Any])]
  ): StandaloneWSRequest = {
    val paramsString = paramsOptionalAsString(params)
    val url = createUrl(endPoint, endPointParam) + paramsString

    client.url(url)
  }

  private def execRequestJsonAux(
    request: StandaloneWSRequest,
    exec: StandaloneWSRequest => Future[StandaloneWSRequest#Response],
    acceptableStatusCodes: Seq[Int] = Nil,
    endPointForLogging: Option[PEP] = None // only for logging
  ): Future[RichJsResponse] =
    execRequestRaw(
      request,
      exec,
      acceptableStatusCodes,
      endPointForLogging
    ).map(_ match {
      case Left(response) =>
        try {
          Left(response.body[JsValue])
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
      case Right(response) => Right(response)
    })

  private def execRequestStringAux(
    request: StandaloneWSRequest,
    exec: StandaloneWSRequest => Future[StandaloneWSRequest#Response],
    acceptableStatusCodes: Seq[Int] = Nil,
    endPointForLogging: Option[PEP] = None // only for logging
  ): Future[RichStringResponse] =
    execRequestRaw(
      request,
      exec,
      acceptableStatusCodes,
      endPointForLogging
    ).map(_ match {
      case Left(response)  => Left(response.body)
      case Right(response) => Right(response)
    })

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

  protected def jsonBodyParams(
    params: (PT, Option[Any])*
  ): Seq[(PT, Option[JsValue])] =
    params.map { case (paramName, value) => (paramName, value.map(toJson)) }

  protected def handleErrorResponse[T](response: RichResponse[T]): T =
    response match {
      case Left(data) => data

      case Right((errorCode, message)) =>
        val errorMessage = s"Code ${errorCode} : ${message}"
        if (
          message.contains("Please reduce your prompt; or completion length") ||
          message.contains("Please reduce the length of the messages")
        )
          throw new OpenAIScalaTokenCountExceededException(errorMessage)
        else
          throw new OpenAIScalaClientException(errorMessage)
    }

  protected def handleNotFoundAndError[T](response: Either[T, (Int, String)]): Option[T] =
    response match {
      case Left(value) => Some(value)

      case Right((errorCode, message)) =>
        if (errorCode == 404) None
        else throw new OpenAIScalaClientException(s"Code ${errorCode} : ${message}")
    }

  protected def paramsAsString(params: Seq[(PT, Any)]): String = {
    val string = params.map { case (tag, value) => s"$tag=$value" }.mkString("&")

    if (string.nonEmpty) s"?$string" else ""
  }

  protected def paramsOptionalAsString(params: Seq[(PT, Option[Any])]): String = {
    val string = params.collect { case (tag, Some(value)) => s"$tag=$value" }.mkString("&")

    if (string.nonEmpty) s"?$string" else ""
  }

  protected def createUrl(
    endpoint: Option[PEP],
    value: Option[String] = None
  ): String =
    coreUrl + endpoint.map(_.toString).getOrElse("") + value.map("/" + _).getOrElse("")

  protected def toOptionalParams(
    params: Seq[(PT, Any)]
  ): Seq[(PT, Some[Any])] =
    params.map { case (a, b) => (a, Some(b)) }

  // close

  // Create Akka system for thread and streaming management
  //  system.registerOnTermination {
  //    System.exit(0)
  //  }
  //
  //  implicit val materializer = SystemMaterializer(system).materializer
  //
  //  // Create the standalone WS client
  //  // no argument defaults to a AhcWSClientConfig created from
  //  // "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"
  //  val wsClient = StandaloneAhcWSClient()
  //
  //  call(wsClient)
  //    .andThen { case _ => wsClient.close() }
  //    .andThen { case _ => system.terminate() }

}
