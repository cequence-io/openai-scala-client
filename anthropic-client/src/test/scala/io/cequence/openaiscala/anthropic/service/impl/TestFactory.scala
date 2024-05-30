package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.ws.Timeouts
import org.scalamock.scalatest.MockFactory
import org.scalatest.PrivateMethodTester.{PrivateMethod, _}
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws.ahc.StandaloneAhcWSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseStatus, CacheableResponse}
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import play.shaded.ahc.org.asynchttpclient.{
  DefaultAsyncHttpClientConfig,
  Response => AHCResponse
}

import scala.concurrent.{ExecutionContext, Future}

class TestAnthropicServiceImpl(
  override val coreUrl: String,
//  override val authHeaders: Seq[(String, String)],
//  override val explTimeouts: Option[Timeouts] = None,
  override val requestContext: WsRequestContext,
  mockedResponse: AHCResponse
)(
  implicit override val ec: ExecutionContext,
  override val materializer: Materializer
) extends AnthropicServiceClassImpl(coreUrl, requestContext)
    with MockFactory {

  val defaultAcceptableStatusCodes = Seq(200, 201, 202, 204)

  override def execRequestRaw(
    request: StandaloneWSRequest,
    exec: StandaloneWSRequest => Future[StandaloneWSRequest#Response],
    acceptableStatusCodes: Seq[Int] = Nil,
    endPointForLogging: Option[PEP] = None // only for logging
  ): Future[Either[StandaloneWSRequest#Response, (Int, String)]] = {

    val response =
      new StandaloneAhcWSResponse(mockedResponse).asInstanceOf[StandaloneWSRequest#Response]

    Future.successful {
      if (!acceptableStatusCodes.contains(response.status))
        Right((response.status, response.body))
      else
        Left(response)
    }
  }.recover(recoverErrors(endPointForLogging))

}

class AnthropicServiceClassImpl(
  val coreUrl: String,
  val requestContext: WsRequestContext
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends AnthropicServiceImpl {}

object TestFactory {
  val getAPIKeyFromEnv = PrivateMethod[String]('getAPIKeyFromEnv)
  val apiVersionMethod = PrivateMethod[String]('apiVersion)
  val defaultCoreUrlMethod = PrivateMethod[String]('defaultCoreUrl)

  val factory = AnthropicServiceFactory
  val apiKey = factory invokePrivate getAPIKeyFromEnv()
  val apiVersion = factory invokePrivate apiVersionMethod()
  val defaultCoreUrl = factory invokePrivate defaultCoreUrlMethod()

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val materializer: Materializer = Materializer(ActorSystem())

  private val authHeaders = Seq(
    ("x-api-key", s"$apiKey"),
    ("anthropic-version", apiVersion)
  )

  def mockedResponse(
    statusCode: Int,
    statusText: String,
    uri: Uri = Uri.create(defaultCoreUrl)
  ): CacheableResponse =
    CacheableResponse(
      new CacheableHttpResponseStatus(
        uri,
        statusCode,
        statusText,
        ""
      ),
      headers = new DefaultHttpHeaders(),
      bodyParts = java.util.Collections.emptyList(),
      ahcConfig = new DefaultAsyncHttpClientConfig.Builder().build()
    )

  private val mockedResponse401 =
    mockedResponse(401, "authentication_error: There’s an issue with your API key.")

  private val mockedResponse403 = mockedResponse(
    403,
    "permission_error: Your API key does not have permission to use the specified resource."
  )
  private val mockedResponse404 =
    mockedResponse(404, "not_found_error: The requested resource was not found.")

  private val mockedResponse429 =
    mockedResponse(429, "rate_limit_error: Your account has hit a rate limit.")

  private val mockedResponse500 =
    mockedResponse(
      500,
      "api_error: An unexpected error has occurred internal to Anthropic’s systems."
    )

  private val mockedResponse529 =
    mockedResponse(529, "overloaded_error: Anthropic’s API is temporarily overloaded.")

  private val mockedResponse400 =
    mockedResponse(
      400,
      "invalid_request_error: There was an issue with the format or content of your request. We may also use this error type for other 4XX status codes not listed below."
    )

  private val mockedResponseOther =
    mockedResponse(
      503,
      "Service unavailable"
    )

  println(s"authHeaders = $authHeaders")

  def withResponse(mockedResponse: AHCResponse) =
    new TestAnthropicServiceImpl(
      defaultCoreUrl,
      WsRequestContext(authHeaders = authHeaders),
      mockedResponse = mockedResponse
    )

  def mockedService401(): TestAnthropicServiceImpl = withResponse(mockedResponse401)
  def mockedService403(): TestAnthropicServiceImpl = withResponse(mockedResponse403)
  def mockedService404(): TestAnthropicServiceImpl = withResponse(mockedResponse404)
  def mockedService429(): TestAnthropicServiceImpl = withResponse(mockedResponse429)
  def mockedService500(): TestAnthropicServiceImpl = withResponse(mockedResponse500)
  def mockedService529(): TestAnthropicServiceImpl = withResponse(mockedResponse529)
  def mockedService400(): TestAnthropicServiceImpl = withResponse(mockedResponse400)
  def mockedServiceOther(): TestAnthropicServiceImpl = withResponse(mockedResponseOther)

}
