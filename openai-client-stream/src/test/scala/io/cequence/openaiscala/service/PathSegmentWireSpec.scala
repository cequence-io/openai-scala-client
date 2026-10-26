package io.cequence.openaiscala.service

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * A resource id or a model name put into a REST path is exactly ONE path segment: a '/', '?'
 * or '#' in it is escaped (the HTTP stack would otherwise resolve `../..` or cut the path, and
 * address another resource with the integrator's credential), and an empty id, "." or ".." is
 * refused before any request is sent - as a failed Future (or stream), never a synchronous
 * throw, so `.recover`, `Future.traverse` and the service adapters handle it like any other
 * failure. The request line is what the local server sees.
 */
class PathSegmentWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("path-segment-wire")
  private implicit val materializer: Materializer = Materializer(system)

  private final case class Recorded(
    method: String,
    rawPath: String,
    rawQuery: Option[String]
  )

  @volatile private var requests: Vector[Recorded] = Vector.empty

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val service = OpenAIServiceFactory.withStreaming.customEngineInstance(
    engine,
    s"http://localhost:${server.getAddress.getPort}/v1/",
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))
  )

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val uri = exchange.getRequestURI
          synchronized {
            requests = requests :+ Recorded(
              exchange.getRequestMethod,
              uri.getRawPath,
              Option(uri.getRawQuery)
            )
          }
          // a 404 reads as "not found" (None) wherever the API supports it, anything else as an
          // error - the tests look at the request only
          val bytes = """{"error":{"message":"not found"}}""".getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(404, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    service.close()
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  /** The one request the call sent (whatever its outcome). */
  private def sent(call: => Future[_]): Recorded = {
    val before = requests.size
    Await.ready(call, 20.seconds)
    requests.size shouldBe before + 1
    requests.last
  }

  /** The failure of a call that must be refused - a failed Future, and no request sent. */
  private def refused(call: => Future[_]): Throwable = {
    val before = requests.size

    val failure = Try(call) match {
      case Failure(e)      => fail(s"thrown synchronously: $e")
      case Success(future) => Await.result(future.failed, 20.seconds)
    }

    Thread.sleep(100)
    requests.size shouldBe before
    failure
  }

  "A path id" should {

    "be escaped as one segment: '../..' in a nested id" in {
      val request = sent(service.deleteVectorStoreFile("vs_mine", "../../vs_victim"))
      request.method shouldBe "DELETE"
      request.rawPath shouldBe "/v1/vector_stores/vs_mine/files/..%2F..%2Fvs_victim"
      request.rawQuery shouldBe None
    }

    "not open a query with '?'" in {
      val request = sent(service.retrieveFile("x?a=b"))
      request.rawPath shouldBe "/v1/files/x%3Fa=b"
      request.rawQuery shouldBe None
    }

    "not cut the path at '#', and keep the '/content' suffix" in {
      val request = sent(service.retrieveFileContent("a#"))
      request.rawPath shouldBe "/v1/files/a%23/content"
      request.rawQuery shouldBe None
    }

    "keep ':' of a fine-tuned model id literal" in {
      val request = sent(service.retrieveModel("ft:gpt-4o-mini:org:custom:id"))
      request.rawPath shouldBe "/v1/models/ft:gpt-4o-mini:org:custom:id"
    }

    "escape '%' (no double decoding) and '/' in the Agents API ids" in {
      val request = sent(service.getAgent("a%2e%2e/b"))
      request.rawPath shouldBe "/v1/agents/a%252e%252e%2Fb"
    }

    "be escaped on the streamed session events too" in {
      val before = requests.size
      Await.ready(
        service.streamAgentSessionEvents("s/../x").runWith(Sink.ignore),
        20.seconds
      )
      requests.size shouldBe before + 1
      requests.last.rawPath shouldBe "/v1/agents/sessions/s%2F..%2Fx/events"
    }

    "refuse '..', '.' and an empty id before any request is sent" in {
      refused(service.retrieveFile("..")) shouldBe an[IllegalArgumentException]
      refused(service.retrieveFileContent(".")) shouldBe an[IllegalArgumentException]
      refused(
        service.deleteVectorStoreFile("vs_mine", "..")
      ) shouldBe an[IllegalArgumentException]
      refused(
        service.deleteVectorStoreFile("", "file_1")
      ) shouldBe an[IllegalArgumentException]
      refused(service.getAgent("..")) shouldBe an[IllegalArgumentException]
      refused(service.retrieveModel("")) shouldBe an[IllegalArgumentException]
    }

    "refuse '..' on the session streams - as a failed stream" in {
      val failure = Try(service.streamAgentSessionEvents("..")) match {
        case Failure(e)   => fail(s"thrown synchronously: $e")
        case Success(src) => Await.result(src.runWith(Sink.ignore).failed, 20.seconds)
      }
      failure shouldBe an[IllegalArgumentException]
    }

    "let .recover, Future.traverse and the retry adapter handle a refused id" in {
      val recovered = Await.result(
        Future
          .traverse(Seq("file_1", ".."))(service.retrieveFile)
          .map(_ => "all retrieved")
          .recover { case _: IllegalArgumentException => "refused" },
        20.seconds
      )
      recovered shouldBe "refused"

      implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 2)
      implicit val scheduler: Scheduler = system.scheduler
      val withRetry = OpenAIServiceAdapters.forFullService.retry(service)
      refused(withRetry.getAgent("..")) shouldBe an[IllegalArgumentException]
    }

    "fail the Agents API item listing as a failed Future (never a synchronous throw)" in {
      val failure = Try(
        service.listAgentSessionItems("..", None, None, None, None, None)
      ) match {
        case Failure(e)   => fail(s"thrown synchronously: $e")
        case Success(fut) => Await.result(fut.failed, 20.seconds)
      }
      failure shouldBe an[IllegalArgumentException]
    }
  }
}
