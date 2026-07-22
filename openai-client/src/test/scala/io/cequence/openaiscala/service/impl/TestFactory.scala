package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.service.{
  OpenAIService,
  OpenAIServiceConsts,
  OpenAIServiceFactoryHelper,
  ProjectWSClientEngine
}
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientEngine
import io.cequence.wsclient.service.ws.Timeouts
import play.api.libs.ws.ahc.cache.CacheableResponse
import play.shaded.ahc.org.asynchttpclient.uri.Uri
import play.shaded.ahc.org.asynchttpclient.{
  DefaultAsyncHttpClientConfig,
  Response => AHCResponse
}

import scala.concurrent.ExecutionContext

class TestOpenAIServiceImpl(
  coreUrl: String,
  requestContext: WsRequestContext,
  mockedResponse: AHCResponse
)(
  implicit override val ec: ExecutionContext
) extends OpenAIServiceClassImpl(coreUrl, requestContext) {

  protected override val defaultAcceptableStatusCodes: Seq[Int] = Seq(200, 201, 202, 204)

  // TODO: this function is hidden in the parent class - try to sneak it through WSClientEngine
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
    requestContext: WsRequestContext = WsRequestContext(),
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIService =
    new TestOpenAIServiceImpl(coreUrl, requestContext, mockedResponse)

  // the mocked-response tests never route through a caller-supplied engine
  override def customEngineInstance(
    engine: WSClientEngine,
    coreUrl: String,
    requestContext: WsRequestContext = WsRequestContext()
  )(
    implicit ec: ExecutionContext
  ): OpenAIService =
    throw new UnsupportedOperationException(
      "TestOpenAIServiceFactory does not support customEngineInstance."
    )
}

class OpenAIServiceClassImpl(
  coreUrl: String,
  requestContext: WsRequestContext
)(
  implicit val ec: ExecutionContext
) extends OpenAIServiceImpl {
  // ONE engine (and thus one actor system) shared by ALL mocked services - the
  // mockedService* helpers are created per test and never closed, so a per-service
  // discovery engine would leak an actor system per call
  protected val engine: WSClientEngine = TestFactory.sharedEngine

  // shared - never closed by an individual mocked service
  override protected def ownsEngine: Boolean = false

  protected val site: SiteBinding =
    ProjectWSClientEngine.siteBinding(coreUrl, requestContext)
}

object TestFactory extends OpenAIServiceConsts {
  implicit val ec: ExecutionContext = ExecutionContext.global

  // the single stateless engine behind every mocked service (daemon-threaded, so even
  // left unclosed it cannot pin the JVM)
  private[impl] lazy val sharedEngine: WSClientEngine = ProjectWSClientEngine()

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

  def withResponse(mockedResponse: AHCResponse): OpenAIService =
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
