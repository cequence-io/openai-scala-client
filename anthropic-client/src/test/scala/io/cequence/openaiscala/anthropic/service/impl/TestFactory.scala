package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.wsclient.service.ws.Timeouts
import org.scalamock.scalatest.MockFactory
import org.scalatest.PrivateMethodTester.PrivateMethod
import org.scalatest.PrivateMethodTester._
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.JsValue
import play.api.libs.ws.ahc.StandaloneAhcWSResponse
import play.api.libs.ws.ahc.cache.{CacheableHttpResponseStatus, CacheableResponse}
import play.api.libs.ws.{BodyWritable, StandaloneWSRequest, ahc}
import play.shaded.ahc.io.netty.handler.codec.http.DefaultHttpHeaders
import play.shaded.ahc.org.asynchttpclient.{
  AsyncHttpClientConfig,
  DefaultAsyncHttpClientConfig
}
import play.shaded.ahc.org.asynchttpclient.netty.{NettyResponse, NettyResponseStatus}
import play.shaded.ahc.org.asynchttpclient.uri.Uri

import java.util
import scala.concurrent.{ExecutionContext, Future}

class TestAnthropicServiceImpl(
  override val coreUrl: String,
  override val authHeaders: Seq[(String, String)],
  override val explTimeouts: Option[Timeouts] = None
)(
  implicit override val ec: ExecutionContext,
  override val materializer: Materializer
) extends AnthropicServiceClassImpl(coreUrl, authHeaders, explTimeouts)
    with MockFactory {

  val defaultAcceptableStatusCodes = Seq(200, 201, 202, 204)

  override def execRequestRaw(
    request: StandaloneWSRequest,
    exec: StandaloneWSRequest => Future[StandaloneWSRequest#Response],
    acceptableStatusCodes: Seq[Int] = Nil,
    endPointForLogging: Option[PEP] = None // only for logging
  ): Future[Either[StandaloneWSRequest#Response, (Int, String)]] = {

    val cacheResponse = CacheableResponse(
      new CacheableHttpResponseStatus(
        Uri.create(defaultCoreUrl),
        401,
        "authentication_error: There’s an issue with your API key.",
        ""
      ),
      headers = new DefaultHttpHeaders(),
      bodyParts = java.util.Collections.emptyList(),
      ahcConfig = new DefaultAsyncHttpClientConfig.Builder().build()
    )

    val myResponse = new StandaloneAhcWSResponse(cacheResponse)
    val response: StandaloneWSRequest#Response =
      myResponse.asInstanceOf[StandaloneWSRequest#Response]

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
  override val authHeaders: Seq[(String, String)],
  override val explTimeouts: Option[Timeouts] = None
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends AnthropicServiceImpl {
  override protected val extraParams: Seq[(String, String)] = Nil
}

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

  val authHeaders = Seq(
    ("x-api-key", s"$apiKey"),
    ("anthropic-version", apiVersion)
  )

  def testService() = new TestAnthropicServiceImpl(
    defaultCoreUrl,
    authHeaders
  )

}
