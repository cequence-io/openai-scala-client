package io.cequence.openaiscala.typesafe.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsString, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * What actually goes over the wire, through the real engine against a local HTTP server: the
 * paths, the bearer header, the request body, the response parsing (incl. the request-id
 * header), the error mapping and the shared-engine ownership rule.
 */
class TypeSafeServiceWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private case class Received(
    method: String,
    path: String,
    headers: Map[String, String],
    body: String
  )

  @volatile private var received: Option[Received] = None
  @volatile private var reply: (Int, String, Map[String, String]) = (200, "{}", Map.empty)
  // when set, decides the reply per request (for the retry test)
  @volatile private var onRequest: () => (Int, String, Map[String, String]) = null

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = WSClientEngineRegistry(TransportSettings())
  private lazy val baseUrl = s"http://localhost:${server.getAddress.getPort}"

  private lazy val service =
    TypeSafeServiceFactory.withEngine(engine, apiKey = "test-key", baseUrl = baseUrl)

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          received = Some(
            Received(
              exchange.getRequestMethod,
              exchange.getRequestURI.getPath,
              exchange.getRequestHeaders
                .keySet()
                .toArray
                .map { key =>
                  key.toString.toLowerCase -> exchange.getRequestHeaders.getFirst(key.toString)
                }
                .toMap,
              body
            )
          )

          val (status, responseBody, responseHeaders) =
            if (onRequest != null) onRequest() else reply
          responseHeaders.foreach { case (k, v) => exchange.getResponseHeaders.add(k, v) }
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          val bytes = responseBody.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(status, bytes.length.toLong)
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
  }

  private def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  private def respond(
    status: Int,
    body: String,
    headers: Map[String, String] = Map.empty
  ): Unit = {
    reply = (status, body, headers)
    received = None
  }

  private val quickStartResponse =
    """{
      |  "model": "jev-1.12",
      |  "answers": {
      |    "department": {"type":"choice","choice":"technical","probabilities":{"billing":0.159,"technical":0.84,"sales":0.001},"confidence":0.596},
      |    "is_urgent": {"type":"noul","noul":0.999}
      |  },
      |  "usage": {"input_tokens": 312, "output_tokens": 48}
      |}""".stripMargin

  private val questions: Map[String, Question] = Map(
    "department" -> ChoiceQuestion(
      "Which team should handle this",
      "billing" -> "Payment or subscription issues",
      "technical" -> "Bugs or integration problems",
      "sales" -> "Pricing or account questions"
    ),
    "is_urgent" -> NoulQuestion("The message conveys urgency or time-sensitivity")
  )

  "systemOne" should {

    "POST the documented body to /v1/systemone with the bearer key" in {
      respond(200, quickStartResponse, Map("x-typesafe-request-id" -> "req-42"))

      val response = await(service.systemOne("Please help ASAP.", questions))

      val request = received.get
      request.method shouldBe "POST"
      request.path shouldBe "/v1/systemone"
      request.headers("authorization") shouldBe "Bearer test-key"
      request.headers("content-type") should startWith("application/json")

      Json.parse(request.body) shouldBe Json.obj(
        "state" -> "Please help ASAP.",
        "model" -> "jev-latest",
        "questions" -> Json.obj(
          "department" -> Json.obj(
            "type" -> "choice",
            "instructions" -> "Which team should handle this",
            "criteria" -> Json.obj(
              "billing" -> "Payment or subscription issues",
              "technical" -> "Bugs or integration problems",
              "sales" -> "Pricing or account questions"
            )
          ),
          "is_urgent" -> Json.obj(
            "type" -> "noul",
            "instructions" -> "The message conveys urgency or time-sensitivity"
          )
        )
      )

      response.model shouldBe "jev-1.12"
      response.requestId shouldBe Some("req-42")
      response.choice("department").choice shouldBe "technical"
      response.noul("is_urgent").noul shouldBe 0.999
      response.usage shouldBe Usage(Some(312), Some(48))
    }

    "send the model that was asked for, and a JSON state as is" in {
      respond(200, quickStartResponse)

      val state = Json.obj("subject" -> "Duplicate charge", "message" -> "Please help.")
      await(service.systemOne(state, questions, model = TypeSafeModelId.jev_preview))

      val body = Json.parse(received.get.body).as[JsObject]
      (body \ "model").as[String] shouldBe TypeSafeModelId.jev_preview
      (body \ "state").get shouldBe state
    }

    "use the configured default model" in {
      respond(200, quickStartResponse)
      val custom =
        TypeSafeServiceFactory.withEngine(engine, "k", baseUrl, defaultModel = "jev-1.12")

      await(custom.systemOne(JsString("x"), questions))

      (Json.parse(received.get.body) \ "model").as[String] shouldBe "jev-1.12"
      custom.close()
    }

    "fail fast on an empty question map, without calling the API" in {
      respond(200, quickStartResponse)

      an[IllegalArgumentException] should be thrownBy service.systemOne(
        "x",
        Map.empty[String, Question]
      )
      received shouldBe None
    }

    "map a 401 to an unauthorized exception carrying the API's message and the request id" in {
      respond(
        401,
        """{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server. Please check your API key and try again."}}""",
        Map("x-typesafe-request-id" -> "req-err-1")
      )

      val e = await(service.systemOne("x", questions).failed)

      e shouldBe a[TypeSafeScalaUnauthorizedException]
      val typed = e.asInstanceOf[TypeSafeScalaUnauthorizedException]
      typed.httpCode shouldBe Some(401)
      typed.errorType shouldBe Some("authentication_error")
      typed.requestId shouldBe Some("req-err-1")
      e.getMessage should include("Cannot authenticate with the server")
      e.getMessage should include("[request req-err-1]")
      e.getMessage should not include "test-key"
    }

    "map a 422 to an invalid-request exception naming the offending field" in {
      respond(
        422,
        """{"detail":[{"loc":["body","state"],"msg":"Field required","type":"missing"}]}"""
      )

      val e = await(service.systemOne("x", questions).failed)

      e shouldBe a[TypeSafeScalaInvalidRequestException]
      e.asInstanceOf[TypeSafeScalaInvalidRequestException].violations shouldBe
        Seq(TypeSafeViolation("state", "Field required"))
      e.getMessage should include("state: Field required")
    }

    "map the input limit, an unknown model and a wrong path" in {
      respond(400, """{"detail":{"error_type":"max_tokens_exceeded"}}""")
      await(service.systemOne("x", questions).failed) shouldBe
        a[TypeSafeScalaTokenCountExceededException]

      respond(
        400,
        """{"detail":{"error_type":"api_usage_error","message":"Unknown model: x"}}"""
      )
      await(service.systemOne("x", questions).failed) shouldBe a[
        TypeSafeScalaApiUsageException
      ]

      respond(404, """{"detail":"Not Found"}""")
      await(service.listModels.failed) shouldBe a[TypeSafeScalaNotFoundException]
    }

    "map 429 and 529 to retryable exceptions" in {
      respond(429, """{"detail":"Rate limit exceeded"}""")
      val rateLimited = await(service.systemOne("x", questions).failed)
      rateLimited shouldBe a[TypeSafeScalaRateLimitException]

      respond(529, """{"detail":"Overloaded"}""")
      val overloaded = await(service.systemOne("x", questions).failed)
      overloaded shouldBe a[TypeSafeScalaEngineOverloadedException]

      Seq(rateLimited, overloaded).foreach {
        case TypeSafeRetryable(_) => succeed
        case other                => fail(s"$other should be retryable")
      }
    }

    "retry a 529 through the retry adapter and give up on a 401" in {
      import io.cequence.openaiscala.RetryHelpers.RetrySettings
      import scala.concurrent.duration._
      implicit val retrySettings: RetrySettings =
        RetrySettings(maxRetries = 2, delayOffset = 10.millis)
      implicit val system: akka.actor.ActorSystem = akka.actor.ActorSystem("retry-spec")
      implicit val scheduler: akka.actor.Scheduler = system.scheduler
      val retrying = TypeSafeServiceAdapters.retry(service)

      var calls = 0
      onRequest = () => {
        calls += 1
        if (calls < 3) (529, """{"detail":"Overloaded"}""", Map.empty[String, String])
        else (200, quickStartResponse, Map.empty[String, String])
      }
      await(retrying.systemOne("x", questions))
        .choice("department")
        .choice shouldBe "technical"
      calls shouldBe 3

      calls = 0
      onRequest = () => {
        calls += 1;
        (
          401,
          """{"detail":{"error_type":"authentication_error","message":"no"}}""",
          Map.empty[String, String]
        )
      }
      await(retrying.systemOne("x", questions).failed) shouldBe a[
        TypeSafeScalaUnauthorizedException
      ]
      calls shouldBe 1

      onRequest = null
      await(system.terminate())
    }
  }

  "listModels" should {

    "GET /v1/models with the bearer key" in {
      respond(
        200,
        """{"models":[{"name":"jev-latest","description":"General-purpose system one model.","release_date":"2026-09-15"}]}"""
      )

      val models = await(service.listModels)

      received.get.method shouldBe "GET"
      received.get.path shouldBe "/v1/models"
      received.get.headers("authorization") shouldBe "Bearer test-key"
      models shouldBe Seq(
        ModelMetadata("jev-latest", "General-purpose system one model.", "2026-09-15")
      )
    }
  }

  "a service on a shared engine" should {

    "not close the engine when closed - a sibling service keeps working" in {
      respond(200, """{"models":[]}""")
      val sibling = TypeSafeServiceFactory.withEngine(engine, "k", baseUrl)
      sibling.close()

      await(service.listModels) shouldBe empty
    }

    "accept a base URL with or without the trailing slash" in {
      respond(200, """{"models":[]}""")
      val slashed = TypeSafeServiceFactory.withEngine(engine, "k", baseUrl + "/")

      await(slashed.listModels)

      received.get.path shouldBe "/v1/models"
      slashed.close()
    }
  }
}
