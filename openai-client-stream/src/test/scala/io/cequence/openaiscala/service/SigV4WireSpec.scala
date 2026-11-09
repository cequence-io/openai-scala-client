package io.cequence.openaiscala.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.aws.{AwsCredentialsProvider, AwsSigV4, SigningWSClientEngine}
import io.cequence.openaiscala.testkit.{StreamSpecSupport, TestEngines}
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.WSClientOutputStreamCore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * SigV4 signing on the engine under test (`-Dws-client.engine`), against a local server that
 * VERIFIES every signature the way AWS does - recomputed from the request exactly as received:
 * the raw path and query, the body bytes, the `X-Amz-Date` timestamp. So the signer must sign
 * the engine's own wire format (its query encoding, its JSON escaping), and a stream must be
 * signed when its run starts, not when it was built.
 */
class SigV4WireSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with StreamSpecSupport {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val accessKey = "AKIDEXAMPLE"
  private val secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
  private val region = "us-east-1"

  // per request: the received raw path + query, the verdict and the signing time
  private final case class Received(
    pathAndQuery: String,
    body: String,
    valid: Boolean,
    signedAt: Instant
  )

  private val received = new ConcurrentLinkedQueue[Received]()

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val plainEngine = TestEngines.streaming()
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"

  // the local server is not an AWS host - sign every site
  private lazy val engine =
    SigningWSClientEngine(
      plainEngine,
      AwsCredentialsProvider.static(accessKey, secretKey),
      region,
      signedSites = _ => true,
      ownsUnderlying = false
    ).asInstanceOf[io.cequence.wsclient.service.WSClientEngine with WSClientOutputStreamCore]

  private lazy val site = SiteBinding(coreUrl, WsRequestContext())

  private val amzDate =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

  private def verify(exchange: HttpExchange): Received = {
    val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
    val uri = exchange.getRequestURI
    val pathAndQuery =
      uri.getRawPath + Option(uri.getRawQuery).fold("")("?" + _)
    val host = exchange.getRequestHeaders.getFirst("Host")
    val date = exchange.getRequestHeaders.getFirst("X-Amz-Date")
    val signedAt = LocalDateTime.parse(date, amzDate).toInstant(ZoneOffset.UTC)

    val expected = AwsSigV4.signedHeaders(
      method = exchange.getRequestMethod,
      url = s"http://$host$pathAndQuery",
      headers = Map.empty,
      body = body,
      accessKey = accessKey,
      secretKey = secretKey,
      region = region,
      service = "bedrock",
      now = signedAt
    )

    val valid =
      Option(exchange.getRequestHeaders.getFirst("Authorization")) == expected.get(
        "Authorization"
      )
    Received(pathAndQuery, body, valid, signedAt)
  }

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val verdict = verify(exchange)
          received.add(verdict)

          val (status, contentType, body) =
            if (!verdict.valid)
              (
                403,
                "application/json",
                """{"message":"The request signature we calculated does not match"}"""
              )
            else if (exchange.getRequestURI.getPath.endsWith("/stream"))
              (
                200,
                "text/event-stream",
                "data: {\"n\":1}\n\ndata: {\"n\":2}\n\ndata: [DONE]\n\n"
              )
            else
              (200, "application/json", """{"ok":true}""")

          val bytes = body.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", contentType)
          exchange.sendResponseHeaders(status, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    plainEngine.close()
    server.stop(0)
    ()
  }

  // a body no two engines would render alike if they disagreed: a non-BMP emoji, CJK, quotes
  private val body: Seq[(String, Option[JsValue])] = Seq(
    "text" -> Some(Json.toJson("call 📞 日本 \"quoted\" a\\b")),
    "n" -> Some(Json.toJson(1))
  )

  private def last: Received = received.asScala.last

  "a SigV4-signed request" should {

    "carry a signature over the JSON body exactly as the engine sends it" in {
      received.clear()
      val response = Await.result(
        engine.execPOSTRich(site, "invoke", bodyParams = body),
        20.seconds
      )

      response.status.code shouldBe 200
      last.valid shouldBe true
      last.body shouldBe engine.renderJsonBody(
        play.api.libs.json.JsObject(body.collect { case (k, Some(v)) => k -> v })
      )
    }

    "carry a signature over the query as the engine encodes it - '+', '/', '=' and spaces" in {
      received.clear()
      val response = Await.result(
        engine.execGETRich(
          site,
          "items?q=a%2Fb%20c&sig=x+y%3D",
          params = Seq("after" -> Some("a+b/c=d e"), "limit" -> Some(5))
        ),
        20.seconds
      )

      response.status.code shouldBe 200
      last.valid shouldBe true
      // an endpoint-string query is sent byte for byte (a list token signed as written)
      last.pathAndQuery should startWith("/items?q=a%2Fb%20c&sig=x+y%3D&")
      last.pathAndQuery shouldBe engine
        .wireUrl(
          site,
          "items?q=a%2Fb%20c&sig=x+y%3D",
          None,
          Seq("after" -> Some("a+b/c=d e"), "limit" -> Some(5))
        )
        .stripPrefix(coreUrl.stripSuffix("/"))
    }

    "sign a stream when its run starts - a run a second later is signed a second later" in {
      received.clear()
      val stream = engine.execJsonAsyncStream(site, "stream", "POST", bodyParams = body)
      val built = Instant.now()

      Thread.sleep(1100)
      runStream(stream).map(json => (json \ "n").as[Int]) shouldBe Seq(1, 2)
      last.valid shouldBe true
      last.signedAt.isAfter(built) shouldBe true

      // a re-run is a new, newly signed request
      runStream(stream) should have size 2
      received.size shouldBe 2
      received.asScala.forall(_.valid) shouldBe true
    }

    "sign the raw byte stream per run too" in {
      received.clear()
      val bytes = engine.execRawAsyncStream(site, "stream", "POST", bodyParams = body)

      runStream(bytes) should not be empty
      runStream(bytes) should not be empty
      received.size shouldBe 2
      received.asScala.forall(_.valid) shouldBe true
    }
  }
}
