package io.cequence.openaiscala.typesafe.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Base64
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
    body: String,
    query: Option[String] = None
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
              body,
              Option(exchange.getRequestURI.getRawQuery)
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

  // recorded from https://api.perplexity.ai/v1/decisions on 2026-10-02 (the docs' example)
  private val perplexityResponse =
    """{"model":"pplx-decider-v1-27b","answers":{"defect":{"type":"noul","noul":0.9424522889347015},"sentiment":{"type":"choice","choice":"mixed","confidence":0.92724236393624,"probabilities":{"positive":0.020674765614559172,"mixed":0.9514949092908267,"negative":0.02783032509461421}},"severity":{"type":"score","score":1.7875857287150294,"confidence":0.7875857287150292,"legend":{"0":"Cosmetic","1":"Inconvenient","2":"Product unusable"},"probabilities":{"0":0.0082215259908096,"1":0.19597121930335157,"2":0.7958072547058389}}},"usage":{"input_tokens":367,"output_tokens":3}}"""

  private val review =
    "The headphones sound great, but the battery stopped charging after two weeks."

  private val reviewQuestions: Map[String, Question] = Map(
    "defect" -> NoulQuestion("Does the review report a product defect?"),
    "sentiment" -> ChoiceQuestion(
      "What is the overall sentiment of the review?",
      "positive" -> "Mostly satisfied",
      "mixed" -> "Praise and complaints in one review",
      "negative" -> "Mostly dissatisfied"
    ),
    "severity" -> ScoreQuestion(
      "How severe is the reported problem?",
      "Cosmetic",
      "Inconvenient",
      "Product unusable"
    )
  )

  // a PNG data URL whose header says width x height - the size is all the check reads
  private def pngDataUrl(
    width: Int,
    height: Int
  ): String =
    "data:image/png;base64," + Base64.getEncoder.encodeToString(
      Array(0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a).map(_.toByte) ++
        ByteBuffer
          .allocate(16)
          .putInt(13)
          .put("IHDR".getBytes(StandardCharsets.US_ASCII))
          .putInt(width)
          .putInt(height)
          .array()
    )

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

    "serve Liquid AI's d1 through the liquid preset - its /decisions prefix and d1:free" in {
      respond(200, quickStartResponse)
      val liquid =
        TypeSafeServiceFactory.liquidWithEngine(
          engine,
          "liquid_k",
          baseUrl = baseUrl + "/decisions"
        )

      await(liquid.systemOne(JsString("x"), questions))

      received.get.path shouldBe "/decisions/v1/systemone"
      received.get.headers("authorization") shouldBe "Bearer liquid_k"
      (Json.parse(received.get.body) \ "model")
        .as[String] shouldBe TypeSafeModelId.liquid_d1_free
      liquid.close()
    }

    "serve Perplexity's decider through the perplexity preset - /v1/decisions, x-request-id" in {
      respond(
        200,
        perplexityResponse,
        Map("x-request-id" -> "7a1504a6-a884-48e6-af6e-0c3140aeb6db")
      )
      val perplexity =
        TypeSafeServiceFactory.perplexityWithEngine(engine, "pplx_k", baseUrl = baseUrl)

      val response = await(perplexity.systemOne(Json.obj("review" -> review), reviewQuestions))

      received.get.path shouldBe "/v1/decisions"
      received.get.headers("authorization") shouldBe "Bearer pplx_k"
      val body = Json.parse(received.get.body).as[JsObject]
      // the Decisions API answers 400 to any other field
      body.keys shouldBe Set("model", "state", "questions")
      (body \ "model").as[String] shouldBe TypeSafeModelId.pplx_decider_v1_27b
      (body \ "state" \ "review").as[String] shouldBe review

      response.requestId shouldBe Some("7a1504a6-a884-48e6-af6e-0c3140aeb6db")
      response.noul("defect").noul shouldBe 0.9424522889347015
      response.choice("sentiment").choice shouldBe "mixed"
      response.score("severity").mostLikelyLevel shouldBe 2
      perplexity.close()
    }

    "check the images in a Perplexity state before sending - an oversized one never leaves" in {
      respond(200, perplexityResponse)
      val perplexity =
        TypeSafeServiceFactory.perplexityWithEngine(engine, "pplx_k", baseUrl = baseUrl)
      val photo =
        Json.arr("Is the device damaged?", DecisionImage.part(pngDataUrl(4032, 3024)))

      the[IllegalArgumentException]
        .thrownBy(
          perplexity.systemOne(photo, reviewQuestions)
        )
        .getMessage should include("4032 x 3024")
      received shouldBe None

      // one that fits goes out as it is
      val fits =
        Json.arr("Is the device damaged?", DecisionImage.part(pngDataUrl(1440, 1440)))
      await(perplexity.systemOne(fits, reviewQuestions))
      (Json.parse(received.get.body) \ "state").as[JsArray] shouldBe fits

      // TypeSafe's Jev does not read images, so its state is not checked
      respond(200, quickStartResponse)
      await(service.systemOne(photo, questions))
      received shouldBe defined
      perplexity.close()
    }

    "take TypeSafe's own request id over a generic x-request-id, whatever their order" in {
      respond(
        200,
        quickStartResponse,
        Map("x-request-id" -> "proxy-1", "x-typesafe-request-id" -> "ts-1")
      )

      await(service.systemOne("x", questions)).requestId shouldBe Some("ts-1")
    }

    "refuse more than 128 questions up front for Perplexity and Liquid, not for Jev" in {
      val many: Map[String, Question] =
        (1 to 129).map(i => s"q$i" -> (NoulQuestion(s"Is point $i raised?"): Question)).toMap

      Seq(
        TypeSafeServiceFactory.perplexityWithEngine(engine, "pplx_k", baseUrl = baseUrl),
        TypeSafeServiceFactory.liquidWithEngine(engine, "liquid_k", baseUrl = baseUrl)
      ).foreach { host =>
        respond(200, quickStartResponse)
        the[IllegalArgumentException]
          .thrownBy(host.systemOne("x", many))
          .getMessage should include("at most 128 questions")
        received shouldBe None
        host.close()
      }

      // Jev took 129 live - its request goes out
      respond(200, quickStartResponse)
      await(service.systemOne("x", many))
      received shouldBe defined
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

  "a decision provider" should {

    // recorded from https://openrouter.ai/api/v1/models?output_modalities=decisions (2026-10-02),
    // two of the ten entries, trimmed
    val openRouterModels =
      """{"data":[
        |  {"id":"liquid/d1","canonical_slug":"liquid/d1-20260930","name":"LiquidAI: D1","created":1790878711,"description":"d1 is Liquid AI's first decision model.","context_length":65536,"architecture":{"input_modalities":["text"],"output_modalities":["decisions"]},"pricing":{"prompt":"0.00000004"}},
        |  {"id":"~typesafe/jev-latest","name":"TypeSafe: Jev Latest","created":1789689685,"description":"The latest Jev.","architecture":{"output_modalities":["decisions"]}}
        |]}""".stripMargin

    "serve OpenRouter's decision models - its /api base, the x-generation-id, the listing query" in {
      val openRouter = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.openRouter.copy(baseUrl = baseUrl + "/api"),
        "or_k"
      )

      respond(200, quickStartResponse, Map("x-generation-id" -> "gen-dec-1790947149-W0Aj"))
      val response = await(openRouter.systemOne("x", questions))
      received.get.path shouldBe "/api/v1/systemone"
      received.get.headers("authorization") shouldBe "Bearer or_k"
      (Json.parse(received.get.body) \ "model").as[String] shouldBe
        TypeSafeModelId.openrouter_jev_latest
      response.requestId shouldBe Some("gen-dec-1790947149-W0Aj")

      respond(200, openRouterModels)
      await(openRouter.listModels) shouldBe Seq(
        ModelMetadata("liquid/d1", "d1 is Liquid AI's first decision model.", "2026-10-01"),
        ModelMetadata("~typesafe/jev-latest", "The latest Jev.", "2026-09-18")
      )
      received.get.path shouldBe "/api/v1/models"
      received.get.query shouldBe Some("output_modalities=decisions")
      openRouter.close()
    }

    "take a host of its own - path, question cap, fixed models and request id header" in {
      val custom = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProvider(
          baseUrl,
          "NO_SUCH_DECISIONS_KEY",
          "custom-decider",
          decisionsPath = "v2/decide",
          models = DecisionModelListing.Fixed(Seq(ModelMetadata("custom-decider", "", ""))),
          maxQuestions = Some(2),
          requestIdHeaders = Seq("x-trace-id"),
          name = Some("custom")
        ),
        "c_k"
      )

      respond(200, quickStartResponse, Map("x-trace-id" -> "t-1", "x-request-id" -> "r-1"))
      await(custom.systemOne("x", questions)).requestId shouldBe Some("t-1")
      received.get.path shouldBe "/v2/decide"
      (Json.parse(received.get.body) \ "model").as[String] shouldBe "custom-decider"

      respond(200, quickStartResponse)
      the[IllegalArgumentException]
        .thrownBy(custom.systemOne("x", questions + ("third" -> NoulQuestion("Third?"))))
        .getMessage should include("custom takes at most 2 questions")
      received shouldBe None

      await(custom.listModels).map(_.name) shouldBe Seq("custom-decider")
      received shouldBe None
      custom.close()
    }

    "name every variable it reads for a missing key, and default its label to the host" in {
      val provider = DecisionProvider(
        "https://decisions.example.com/",
        "NO_SUCH_DECISIONS_KEY",
        "m",
        apiKeyEnvFallbacks = Seq("NOR_THIS_ONE")
      )
      the[IllegalStateException].thrownBy(provider.apiKeyFromEnv).getMessage should include(
        "NO_SUCH_DECISIONS_KEY or NOR_THIS_ONE"
      )
      provider.label shouldBe "decisions.example.com"
      DecisionProviderSettings.openRouter.label shouldBe "openrouter"
    }
  }

  "listModels" should {

    "return Perplexity's one decision model without a request" in {
      respond(200, "{}")
      val perplexity =
        TypeSafeServiceFactory.perplexityWithEngine(engine, "pplx_k", baseUrl = baseUrl)

      await(perplexity.listModels).map(_.name) shouldBe Seq(
        TypeSafeModelId.pplx_decider_v1_27b
      )
      received shouldBe None
      perplexity.close()
    }

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
