package io.cequence.openaiscala.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.service.{
  OpenAIService,
  OpenAIServiceConsts,
  OpenAIServiceFactoryHelper
}
import io.cequence.wsclient.domain.WsRequestContext
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws.ahc.StandaloneAhcWSResponse
import play.api.libs.ws.ahc.cache.CacheableResponse
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import play.shaded.ahc.org.asynchttpclient.{
  DefaultAsyncHttpClientConfig,
  Response => AHCResponse
}

import scala.concurrent.{ExecutionContext, Future}

class TestOpenAIServiceImpl(
  override val coreUrl: String,
  override val requestContext: WsRequestContext,
  mockedResponse: AHCResponse
)(
  implicit override val ec: ExecutionContext,
  override val materializer: Materializer
) extends OpenAIServiceClassImpl(coreUrl, requestContext) {

  val defaultAcceptableStatusCodes = Seq(200, 201, 202, 204)

  // TODO: this function is hidden in the parent class
//  override def execRequestRaw(
//    request: StandaloneWSRequest,
//    exec: StandaloneWSRequest => Future[StandaloneWSRequest#Response],
//    acceptableStatusCodes: Seq[Int] = Nil,
//    endPointForLogging: Option[PEP] = None // only for logging
//  ): Future[Either[StandaloneWSRequest#Response, (Int, String)]] = {
//
//    val response =
//      new StandaloneAhcWSResponse(mockedResponse).asInstanceOf[StandaloneWSRequest#Response]
//    def mockedExec(request: StandaloneWSRequest): Future[StandaloneWSRequest#Response] =
//      Future.successful(response)
//
//    mockedExec(request).map { response =>
//      if (!acceptableStatusCodes.contains(response.status))
//        Right((response.status, response.body))
//      else
//        Left(response)
//    }
//  }.recover(recoverErrors(endPointForLogging))
}

case class TestOpenAIServiceFactory(mockedResponse: AHCResponse)
    extends OpenAIServiceFactoryHelper[OpenAIService]
    with OpenAIServiceConsts {

  override def customInstance(
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIService =
    new TestOpenAIServiceImpl(coreUrl, requestContext, mockedResponse)
}

class OpenAIServiceClassImpl(
  val coreUrl: String,
  override val requestContext: WsRequestContext
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends OpenAIServiceImpl

object TestFactory extends OpenAIServiceConsts {
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val materializer: Materializer = Materializer(ActorSystem())

  val factory = TestOpenAIServiceFactory

  def mockedResponse(
    statusCode: Int,
    statusText: String,
    uri: Uri = Uri.create("http://localhost")
  ): CacheableResponse = {

    CacheableResponse(
      code = statusCode,
      urlString = uri.toString,
      body = statusText,
      ahcConfig = new DefaultAsyncHttpClientConfig.Builder().build()
    )
  }

  private val mockedResponse401 =
    mockedResponse(401, "authentication_error: There’s an issue with your API key.")

//  def withResponse(mockedResponse: AHCResponse) =
//    new TestOpenAIServiceImpl(
//      defaultCoreUrl,
//      WsRequestContext(authHeaders = customInstance),
//      mockedResponse = mockedResponse
//    )

  def withResponse(mockedResponse: AHCResponse) =
    TestOpenAIServiceFactory(mockedResponse).customInstance(defaultCoreUrl)

  def mockedService401(): OpenAIService = withResponse(mockedResponse401)
  def mockedService429(): OpenAIService = withResponse(
    mockedResponse(429, "Rate limit exceeded")
  )
  def mockedService500(): OpenAIService = withResponse(
    mockedResponse(500, "Internal Server Error")
  )
  def mockedService503(): OpenAIService = withResponse(
    mockedResponse(503, "Service Unavailable")
  )
  def mockedService400token(): OpenAIService = withResponse(
    mockedResponse(400, "Please reduce your prompt; or completion length")
  )
  def mockedService400token2(): OpenAIService = withResponse(
    mockedResponse(400, "Please reduce the length of the messages")
  )
  def mockedService400(): OpenAIService = withResponse(mockedResponse(400, "Bad Request"))
  def mockedServiceOther(): OpenAIService = withResponse(
    mockedResponse(501, "Not implemented")
  )
}
