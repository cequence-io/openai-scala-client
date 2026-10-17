package io.cequence.openaiscala.typesafe.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.decisions.JsonFormats.createDecisionBody
import io.cequence.openaiscala.domain.decisions.{
  ChoiceProbability,
  CreateDecisionSettings,
  DecisionAnswer,
  DecisionChoice,
  DecisionContent,
  DecisionImageDetail,
  DecisionInput,
  DecisionMessage,
  DecisionQuestion,
  DecisionValue
}
import io.cequence.openaiscala.typesafe.JsonFormats._
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
      await(service.systemOne(state, questions, model = DecisionModelId.jev_preview))

      val body = Json.parse(received.get.body).as[JsObject]
      (body \ "model").as[String] shouldBe DecisionModelId.jev_preview
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
      (Json.parse(received.get.body) \ "model").as[String] shouldBe DecisionModelId.liquid_d1
      liquid.close()
    }

    "send Liquid the images of a state in a top-level images array, marked where they stood" in {
      // recorded from https://api.liquid.ai/decisions/v1/systemone (d1) on 2026-10-07
      respond(
        200,
        """{"model":"d1","answers":{"first":{"type":"choice","choice":"red","probabilities":{"red":0.9998840806664074,"blue":0.00011591933359264961},"confidence":0.9997681613328149}},"usage":{"input_tokens":105,"output_tokens":0,"cost":0.0000042}}"""
      )
      val liquid = TypeSafeServiceFactory.liquidWithEngine(
        engine,
        "liquid_k",
        baseUrl = baseUrl + "/decisions",
        defaultModel = DecisionModelId.liquid_d1
      )
      val (red, blue) = (pngDataUrl(64, 64), pngDataUrl(32, 32))
      val colours: Map[String, Question] =
        Map(
          "first" -> ChoiceQuestion.ofLabels("Which colour is the first image?", "red", "blue")
        )

      val response = await(
        liquid.systemOne(
          Json.obj(
            "note" -> "Two photos.",
            "photos" -> Json.arr(DecisionImage.part(red), DecisionImage.part(blue))
          ),
          colours
        )
      )

      val body = Json.parse(received.get.body)
      (body \ "state").get shouldBe
        Json.obj("note" -> "Two photos.", "photos" -> Json.arr("[image 1]", "[image 2]"))
      (body \ "images").get shouldBe Json.arr(red, blue)
      (body \ "model").as[String] shouldBe "d1"
      response.choice("first").choice shouldBe "red"

      // a lone image is the whole state
      await(liquid.systemOne(DecisionImage.part(red), colours))
      (Json.parse(received.get.body) \ "state").get shouldBe JsString("[image 1]")

      // a state without images goes out as it is, with no images field
      await(liquid.systemOne(JsString("x"), colours))
      (Json.parse(received.get.body) \ "images").toOption shouldBe None

      // the format is checked before sending - Liquid fetches no URL either
      received = None
      the[IllegalArgumentException]
        .thrownBy(
          liquid.systemOne(
            Json.arr("x", DecisionImage.part("https://example.com/a.png")),
            colours
          )
        )
        .getMessage should include("never fetches")
      received shouldBe None
      liquid.close()
    }

    "serve a local llama.cpp - no key, no Authorization header, a 501 not retried" in {
      // recorded from llama-server b11476 (Julia-1) on 2026-10-07
      respond(
        200,
        """{"model":"/models/Julia-1-Q8_0.gguf","answers":{"r":{"type":"noul","noul":0.999514219918827}},"usage":{"input_tokens":19,"output_tokens":0}}"""
      )
      val llamaCpp = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.llamaCpp.copy(baseUrl = baseUrl),
        ""
      )
      val photo = Json.arr("x", DecisionImage.part(pngDataUrl(64, 64)))

      await(llamaCpp.systemOne(photo, questions)).model shouldBe "/models/Julia-1-Q8_0.gguf"
      received.get.path shouldBe "/v1/systemone"
      received.get.headers.get("authorization") shouldBe None
      val body = Json.parse(received.get.body)
      (body \ "model").as[String] shouldBe DecisionModelId.liquid_d1_3b_gguf
      (body \ "images").get shouldBe Json.arr(pngDataUrl(64, 64))

      respond(
        501,
        """{"error":{"code":501,"message":"This server does not support image input for decisions. For a model that supports it, start it with `--mmproj`","type":"not_supported_error"}}"""
      )
      val error = Await.result(llamaCpp.systemOne(photo, questions).failed, 20.seconds)
      error shouldBe a[TypeSafeScalaInvalidRequestException]
      error.getMessage should include("start it with `--mmproj`")
      error.asInstanceOf[TypeSafeScalaClientException].errorType shouldBe
        Some("not_supported_error")
      TypeSafeRetryable.unapply(error) shouldBe None
      llamaCpp.close()
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
      (body \ "model").as[String] shouldBe DecisionModelId.pplx_decider_v1_2
      (body \ "state" \ "review").as[String] shouldBe review

      response.requestId shouldBe Some("7a1504a6-a884-48e6-af6e-0c3140aeb6db")
      response.noul("defect").noul shouldBe 0.9424522889347015
      response.choice("sentiment").choice shouldBe "mixed"
      response.score("severity").mostLikelyLevel shouldBe 2
      perplexity.close()
    }

    "speak OpenAI's Decisions API through the openAI preset - its protocol, translated" in {
      // the shape of a live answer (2026-10-07)
      respond(
        200,
        """{"model":"gpt-6-luna","answers":[
          |{"type":"predicate","name":"defect","probability":0.93},
          |{"type":"choice","name":"sentiment","choice":"mixed","probabilities":[{"value":"positive","probability":0.04},{"value":"mixed","probability":0.9},{"value":"negative","probability":0.06}],"confidence":0.82},
          |{"type":"score","name":"severity","score":1.4,"probabilities":[{"value":0,"label":"Cosmetic","probability":0.05},{"value":1,"label":"Inconvenient","probability":0.5},{"value":2,"label":"Product unusable","probability":0.45}],"confidence":0.4}
          |],"usage":{"input_tokens":312,"input_tokens_details":{"cached_tokens":0,"cache_write_tokens":0},"output_tokens":0,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":312}}""".stripMargin,
        Map("x-request-id" -> "req_42")
      )
      val openAI = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.openAI.copy(baseUrl = baseUrl),
        "sk-test"
      )

      val response = await(openAI.systemOne(JsString(review), reviewQuestions))

      received.get.path shouldBe "/v1/decisions"
      received.get.headers("authorization") shouldBe "Bearer sk-test"
      val body = Json.parse(received.get.body).as[JsObject]
      body.keys shouldBe Set("model", "input", "questions")
      (body \ "model").as[String] shouldBe "gpt-6-luna"
      (body \ "input").as[String] shouldBe review
      (body \ "questions")
        .as[Seq[JsObject]]
        .map(q => (q \ "name").as[String] -> q)
        .toMap shouldBe Map(
        "defect" -> Json.obj(
          "type" -> "predicate",
          "instructions" -> "Does the review report a product defect?",
          "name" -> "defect"
        ),
        "sentiment" -> Json.obj(
          "type" -> "choice",
          "choices" -> Json.arr(
            Json.obj("value" -> "positive", "description" -> "Mostly satisfied"),
            Json
              .obj("value" -> "mixed", "description" -> "Praise and complaints in one review"),
            Json.obj("value" -> "negative", "description" -> "Mostly dissatisfied")
          ),
          "instructions" -> "What is the overall sentiment of the review?",
          "name" -> "sentiment"
        ),
        "severity" -> Json.obj(
          "type" -> "score",
          "levels" -> Json.arr(
            Json.obj("label" -> "Cosmetic"),
            Json.obj("label" -> "Inconvenient"),
            Json.obj("label" -> "Product unusable")
          ),
          "instructions" -> "How severe is the reported problem?",
          "name" -> "severity"
        )
      )

      response.model shouldBe "gpt-6-luna"
      response.requestId shouldBe Some("req_42")
      response.usage shouldBe Usage(Some(312), Some(0))
      response.noul("defect").noul shouldBe 0.93
      response.choice("sentiment") shouldBe
        ChoiceAnswer(
          "mixed",
          0.82,
          Map("positive" -> 0.04, "mixed" -> 0.9, "negative" -> 0.06)
        )
      response.score("severity") shouldBe ScoreAnswer(
        1.4,
        0.4,
        Map(
          0 -> JsString("Cosmetic"),
          1 -> JsString("Inconvenient"),
          2 -> JsString("Product unusable")
        ),
        Map(0 -> 0.05, 1 -> 0.5, 2 -> 0.45)
      )
      openAI.close()
    }

    "send OpenAI the images of a state as input images, marked where they stood" in {
      respond(
        200,
        """{"model":"gpt-6-luna","answers":[{"type":"refusal","name":"defect"}],"usage":null}"""
      )
      val openAI = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.openAI.copy(baseUrl = baseUrl),
        "sk-test"
      )
      // OpenAI scales a large image itself - no tile cap
      val photo = DecisionImage.part(pngDataUrl(4032, 3024))

      val response = await(
        openAI.systemOne(
          Json.obj("review" -> review, "photo" -> photo),
          Map("defect" -> reviewQuestions("defect"))
        )
      )

      (Json.parse(received.get.body) \ "input").get shouldBe Json.arr(
        Json.obj(
          "role" -> "user",
          "content" -> Json.arr(
            Json.obj(
              "type" -> "input_text",
              "text" -> Json.stringify(Json.obj("review" -> review, "photo" -> "[image 1]"))
            ),
            Json.obj("type" -> "input_image", "image_url" -> pngDataUrl(4032, 3024))
          )
        )
      )
      // a refusal is no answer System One knows
      response.answers("defect") shouldBe
        UnknownAnswer("refusal", Json.obj("type" -> "refusal", "name" -> "defect"))
      openAI.close()
    }

    "check OpenAI's question cap before sending, and classify its token limit" in {
      val openAI = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.openAI.copy(baseUrl = baseUrl),
        "sk-test"
      )
      received = None
      val tooMany = (1 to 201).map(i => s"q$i" -> NoulQuestion(s"Is it about $i?")).toMap

      the[IllegalArgumentException]
        .thrownBy(openAI.systemOne(JsString(review), tooMany))
        .getMessage should include("at most 200 questions")
      received shouldBe None

      respond(
        400,
        """{"error":{"message":"Decision input exceeds the token limit.","type":"invalid_request_error","param":null,"code":null}}"""
      )
      Await.result(
        openAI.systemOne(JsString(review), reviewQuestions).failed,
        20.seconds
      ) shouldBe
        a[TypeSafeScalaTokenCountExceededException]
      openAI.close()
    }

    "check the images in a state before sending - a host's tile cap, where it has one" in {
      respond(200, perplexityResponse)
      val perplexity =
        TypeSafeServiceFactory.perplexityWithEngine(engine, "pplx_k", baseUrl = baseUrl)
      val photo =
        Json.arr("Is the device damaged?", DecisionImage.part(pngDataUrl(4032, 3024)))

      // Perplexity scales any image since 2026-10-06: a phone photo goes out as it is
      await(perplexity.systemOne(photo, reviewQuestions))
      (Json.parse(received.get.body) \ "state").as[JsArray] shouldBe photo

      // a host with a cap refuses a larger image up front
      val capped = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.perplexity
          .copy(baseUrl = baseUrl, maxImageTiles = Some(2048)),
        "pplx_k"
      )
      received = None
      the[IllegalArgumentException]
        .thrownBy(capped.systemOne(photo, reviewQuestions))
        .getMessage should (include("4032 x 3024") and include("2048"))
      received shouldBe None

      val fits =
        Json.arr("Is the device damaged?", DecisionImage.part(pngDataUrl(1440, 1440)))
      await(capped.systemOne(fits, reviewQuestions))
      (Json.parse(received.get.body) \ "state").as[JsArray] shouldBe fits

      // TypeSafe's Jev reads images since 2026-10-14 - but takes none over 1,568 pixels a side
      // and never resizes one, so the phone photo is refused up front there
      received = None
      the[IllegalArgumentException]
        .thrownBy(service.systemOne(photo, questions))
        .getMessage should include("4032 x 3024")
      received shouldBe None
      perplexity.close()
      capped.close()
    }

    "send OpenRouter an image as an image_url part of the state, checked before sending" in {
      // live 2026-10-10: a top-level `images` is a 400 there; the part stays where it is
      respond(200, perplexityResponse)
      val openRouter = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.openRouter.copy(baseUrl = baseUrl + "/api"),
        "or_k"
      )
      DecisionProviderSettings.openRouter.readsImages shouldBe true

      val square =
        Json.arr("Which color is the square?", DecisionImage.part(pngDataUrl(64, 64)))
      await(openRouter.systemOne(square, reviewQuestions))
      received.get.path shouldBe "/api/v1/systemone"
      val body = Json.parse(received.get.body)
      (body \ "state").as[JsArray] shouldBe square
      (body \ "images").toOption shouldBe None

      // an image the hosts cannot take (a URL instead of a data URL) is refused before I/O
      received = None
      the[IllegalArgumentException]
        .thrownBy(
          openRouter.systemOne(
            Json.arr(DecisionImage.part("https://example.com/square.png")),
            reviewQuestions
          )
        )
        .getMessage should include("carries an image the API cannot take")
      received shouldBe None
      openRouter.close()
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

      try {
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
      } finally {
        onRequest = null
        await(system.terminate())
      }
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

    // recorded against Microsoft Foundry on 2026-10-09
    val foundryResponse =
      """{"model":"microsoft-decision-1","answers":{"team":{"choice":"billing","confidence":0.9977213773098679,"probabilities":{"account":0.0010317279963969746,"billing":0.9984809182065786,"technical":0.0004873537970244329},"type":"choice"}},"usage":{"input_tokens":67,"output_tokens":1}}"""

    // GET /openai/deployments?api-version=2023-03-15-preview - plus a chat deployment and one
    // still being created, which the listing leaves out / labels
    val foundryDeployments =
      """{"data":[
        |  {"scale_settings":{"scale_type":"standard"},"model":"Microsoft-Decision-1","owner":"organization-owner","id":"decision-1","status":"succeeded","created_at":1791578757,"updated_at":1791578757,"object":"deployment"},
        |  {"scale_settings":{"scale_type":"standard"},"model":"gpt-5.4-mini","owner":"organization-owner","id":"chat","status":"succeeded","created_at":1791578757,"updated_at":1791578757,"object":"deployment"},
        |  {"scale_settings":{"scale_type":"standard"},"model":{"name":"Microsoft-Decision-1","version":"1"},"owner":"organization-owner","id":"decision-1-eu","status":"creating","created_at":1791578757,"updated_at":1791578757,"object":"deployment"}
        |],"object":"list"}""".stripMargin

    "serve Microsoft-Decision-1 on a Foundry resource - /providers/microsoft/v1/systemone, the deployment as the model, apim-request-id, the deployments listed" in {
      // recorded against a Foundry resource on 2026-10-09 (the deployment `decision-1`); the
      // launch example's base URL carries the `/providers/microsoft`, which is stripped
      val foundry = TypeSafeServiceFactory.microsoftFoundryWithEngine(
        engine,
        "f_k",
        baseUrl + "/providers/microsoft/",
        "decision-1"
      )
      val routing = Map(
        "team" -> ChoiceQuestion(
          "Which team should handle this customer support request?",
          "billing" -> "Charges, invoices, refunds, or subscription payments",
          "technical" -> "Software errors, bugs, or integration failures",
          "account" -> "Sign-in, password, or account-access problems"
        )
      )

      respond(
        200,
        foundryResponse,
        Map(
          "apim-request-id" -> "420297b1-b9ef-4ce8-a615-bcc76acb87f1",
          "x-request-id" -> "66b2e28a-b8f9-4ad7-84dd-50586c7d8294"
        )
      )
      val response = await(foundry.systemOne("I was charged twice.", routing))
      received.get.path shouldBe "/providers/microsoft/v1/systemone"
      received.get.headers("authorization") shouldBe "Bearer f_k"
      (Json.parse(received.get.body) \ "model").as[String] shouldBe "decision-1"
      response.model shouldBe "microsoft-decision-1"
      response.choice("team").choice shouldBe "billing"
      response.requestId shouldBe Some("420297b1-b9ef-4ce8-a615-bcc76acb87f1")

      // the resource's deployments, those of the model only, under Azure's old api-version
      respond(200, foundryDeployments)
      await(foundry.listModels) shouldBe Seq(
        ModelMetadata("decision-1", "Microsoft-Decision-1 deployment", "2026-10-09"),
        ModelMetadata(
          "decision-1-eu",
          "Microsoft-Decision-1 deployment (creating)",
          "2026-10-09"
        )
      )
      received.get.method shouldBe "GET"
      received.get.path shouldBe "/openai/deployments"
      received.get.query shouldBe Some("api-version=2023-03-15-preview")

      // the deployment name is the default model, and the endpoint itself works as the base URL
      val bare = TypeSafeServiceFactory.microsoftFoundryWithEngine(engine, "f_k", baseUrl)
      respond(200, foundryResponse)
      await(bare.systemOne("I was charged twice.", routing, "decision-1-eu"))
      received.get.path shouldBe "/providers/microsoft/v1/systemone"
      (Json.parse(received.get.body) \ "model").as[String] shouldBe "decision-1-eu"

      // Foundry's token limit is a 422 - a token-count exception, as on the other hosts
      respond(
        422,
        """{"detail":"invalid TypeSafe request: request is about 165021 tokens; the limit for the state plus all questions is 64000"}"""
      )
      the[TypeSafeScalaTokenCountExceededException]
        .thrownBy(await(bare.systemOne("x" * 100, routing)))
        .getMessage should include("the limit for the state plus all questions is 64000")

      bare.close()
      foundry.close()
    }

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
        DecisionModelId.openrouter_jev_latest
      response.requestId shouldBe Some("gen-dec-1790947149-W0Aj")

      respond(200, openRouterModels)
      await(openRouter.listModels) shouldBe Seq(
        ModelMetadata(
          "liquid/d1",
          "d1 is Liquid AI's first decision model.",
          "2026-10-01",
          Some(Seq("text"))
        ),
        ModelMetadata("~typesafe/jev-latest", "The latest Jev.", "2026-09-18")
      )
      received.get.path shouldBe "/api/v1/models"
      received.get.query shouldBe Some("output_modalities=decisions")
      openRouter.close()
    }

    // recorded against ai-gateway.vercel.sh/typesafe on 2026-10-10
    val vercelResponse =
      """{"model":"convaiinnovations/laya-free","answers":{"refund":{"type":"noul","noul":0.9504}},"usage":{"input_tokens":44,"output_tokens":0},"provider_metadata":{"typesafe":{"confidence":{}},"gateway":{"routing":{"originalModelId":"convaiinnovations/laya-free","resolvedProvider":"boundless","canonicalSlug":"convaiinnovations/laya","finalProvider":"boundless"},"cost":"0","marketCost":"0","surchargeCost":"0","gatewayCost":"0","generationId":"gen_x"}}}"""

    val vercelModels =
      """{"models":[
        |  {"name":"convaiinnovations/laya","description":"Laya is Convai Innovations' System One evaluation model for fast, structured decisions.","release_date":"2026-10-01"},
        |  {"name":"liquid/d1","description":"Liquid d1 is a decision model for classification, routing, and scoring.","release_date":"2026-09-29"},
        |  {"name":"jev","description":"TypeSafe AI System One decision model.","release_date":"2026-09-15"}
        |]}""".stripMargin

    "serve Vercel AI Gateway - /typesafe/v1/systemone, the Bearer key, x-vercel-id, its listing and flat errors" in {
      // recorded against the gateway on 2026-10-10 (a free-tier team, Laya)
      val vercel = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.vercel.copy(baseUrl = baseUrl + "/typesafe"),
        "vck_k"
      )
      DecisionProviderSettings.vercel.readsImages shouldBe true
      val refund = Map("refund" -> NoulQuestion("Does the customer ask for money back?"))

      respond(
        200,
        vercelResponse,
        Map("x-vercel-id" -> "arn1::fra1::x8v95-1791670212019-a35b13e95cba")
      )
      val response =
        await(vercel.systemOne("I was charged twice and want my money back.", refund))
      received.get.path shouldBe "/typesafe/v1/systemone"
      received.get.headers("authorization") shouldBe "Bearer vck_k"
      (Json.parse(received.get.body) \ "model").as[String] shouldBe DecisionModelId.vercel_jev
      response.model shouldBe "convaiinnovations/laya-free"
      response.noul("refund").noul shouldBe 0.9504
      response.usage.input_tokens shouldBe Some(44)
      response.requestId shouldBe Some("arn1::fra1::x8v95-1791670212019-a35b13e95cba")

      // TypeSafe's listing shape, at the gateway's /typesafe prefix
      respond(200, vercelModels)
      await(vercel.listModels).map(_.name) shouldBe
        Seq("convaiinnovations/laya", "liquid/d1", "jev")
      received.get.path shouldBe "/typesafe/v1/models"

      // the gateway's own validation answers in a flat shape, the error type kept
      respond(
        400,
        """{"message":"questions.s.criteria: Too small: expected array to have >=2 items","error_type":"invalid_request"}"""
      )
      val tooFew = the[TypeSafeScalaInvalidRequestException].thrownBy(
        await(vercel.systemOne("x", Map("s" -> ScoreQuestion("How much?", "one"))))
      )
      tooFew.errorType shouldBe Some("invalid_request")
      tooFew.getMessage should include("Too small")
      vercel.close()
    }

    // recorded against api.subq.ai on 2026-10-12
    val subqResponse =
      """{"model":"subq-snap-preview","answers":{"blue":{"type":"noul","noul":0.9862655997276306}},"usage":{"input_tokens":159,"output_tokens":0}}"""

    "serve SubQ Snap - /v1/systemone, the Bearer key, images as {mediaType, base64} objects, a fixed listing and its errors" in {
      val subq = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.subq.copy(baseUrl = baseUrl),
        "subq_k"
      )
      DecisionProviderSettings.subq.readsImages shouldBe true
      val blue = Map("blue" -> NoulQuestion("Is the image a solid blue square?"))
      val png = pngDataUrl(64, 64)

      respond(200, subqResponse)
      val response =
        await(subq.systemOne(Json.arr("A photo.", DecisionImage.part(png)), blue))
      received.get.path shouldBe "/v1/systemone"
      received.get.headers("authorization") shouldBe "Bearer subq_k"
      val body = Json.parse(received.get.body)
      (body \ "model").as[String] shouldBe DecisionModelId.subq_snap_preview
      // the image lifted out of the state, as an object with the bare base64
      (body \ "state").get shouldBe Json.arr("A photo.", "[image 1]")
      (body \ "images").get shouldBe Json.arr(
        Json.obj(
          "mediaType" -> "image/png",
          "base64" -> png.stripPrefix("data:image/png;base64,")
        )
      )
      response.noul("blue").noul shouldBe 0.9862655997276306
      response.usage.input_tokens shouldBe Some(159)
      response.requestId shouldBe None

      // a fixed listing, no request
      respond(200, "{}")
      await(subq.listModels).map(_.name) shouldBe Seq(DecisionModelId.subq_snap_preview)
      received shouldBe None

      // over its context: a 400 with TypeSafe's detail shape
      respond(
        400,
        """{"detail":{"error_type":"validation_error","message":"Input exceeds the token limit."}}"""
      )
      the[TypeSafeScalaTokenCountExceededException]
        .thrownBy(await(subq.systemOne("x", blue)))
        .errorType shouldBe Some("validation_error")

      // an unknown model (its chat model too) is a 422
      respond(
        422,
        """{"detail":{"error_type":"validation_error","message":"Unknown model 'subq-preview'."}}"""
      )
      the[TypeSafeScalaInvalidRequestException]
        .thrownBy(await(subq.systemOne(JsString("x"), blue, model = "subq-preview")))
        .getMessage should include("Unknown model 'subq-preview'.")
      subq.close()
    }

    // recorded through OpenRouter on 2026-10-13 (ScaleDown's own answer, as relayed)
    val scaleDownResponse =
      """{"model":"classify-1","answers":{"defect":{"type":"noul","noul":0.9481502334676174},"sentiment":{"type":"choice","choice":"mixed","probabilities":{"positive":0.006024675083653423,"mixed":0.9853046969340996,"negative":0.008670627982247088},"confidence":0.9853046969340996}},"usage":{"input_tokens":714,"output_tokens":3,"cost":0.00002856}}"""

    // api.scaledown.xyz/v1/models on 2026-10-13, abridged: its decision models next to the others
    val scaleDownModels =
      """{"object":"list","data":[
        |  {"schema_version":"2.4","id":"compress","created":1791849600,"name":"ScaleDown : Compress","description":"Compress shortens prompts.","input_modalities":[{"type":"text"}],"output_modalities":[{"type":"text","streaming":false}]},
        |  {"schema_version":"2.4","id":"classify-1","created":1791849600,"name":"ScaleDown : Classify-1","description":"Classify is a Constrained Decoding Model (CDM) from ScaleDown.","input_modalities":[{"type":"text"},{"type":"image"},{"type":"file"}],"output_modalities":[{"type":"decisions"}],"openrouter":{"slug":"scaledown/classify-1"}},
        |  {"schema_version":"2.4","id":"extract","created":1791849600,"name":"ScaleDown : Extract","description":"Extract pulls entities.","input_modalities":[{"type":"text"}],"output_modalities":[{"type":"text"}]}
        |]}""".stripMargin

    "serve ScaleDown - /v1/scaledown, the key in x-api-key, its text-only requests, the image as the state's document" in {
      val scaleDown = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.scaleDown.copy(baseUrl = baseUrl),
        "sd_k"
      )
      val png = pngDataUrl(64, 64)
      val questions = Map[String, Question](
        "defect" -> NoulQuestion(
          "Does the review report a product defect?",
          "A part stopped working",
          "Only opinions"
        ),
        "sentiment" -> ChoiceQuestion(
          "What is the overall sentiment of the review?",
          "positive" -> "Mostly satisfied",
          "mixed" -> "Praise and complaints in one review",
          "negative" -> "Mostly dissatisfied"
        )
      )

      respond(200, scaleDownResponse, Map("x-amzn-requestid" -> "b922c927-f8d7-46a2"))
      val response = await(
        scaleDown.systemOne(
          Json.obj("review" -> review, "photo" -> DecisionImage.part(png)),
          questions
        )
      )
      received.get.path shouldBe "/v1/scaledown"
      received.get.headers("x-api-key") shouldBe "sd_k"
      received.get.headers.get("authorization") shouldBe None
      val body = Json.parse(received.get.body)
      (body \ "model").as[String] shouldBe DecisionModelId.scaledown_classify_1
      // the state as text - the image lifted into the document, a marker in its place
      (body \ "state").get shouldBe Json.obj(
        "text" -> Json.prettyPrint(Json.obj("review" -> review, "photo" -> "[image 1]")),
        "document" -> png.stripPrefix("data:image/png;base64,"),
        "document_mime_type" -> "image/png"
      )
      // the noul's criteria in its instructions, which ScaleDown reads
      (body \ "questions" \ "defect").get shouldBe Json.obj(
        "type" -> "noul",
        "instructions" -> "Does the review report a product defect?\nYes when: A part stopped working\nNo when: Only opinions"
      )
      response.noul("defect").noul shouldBe 0.9481502334676174
      response.choice("sentiment").choice shouldBe "mixed"
      response.usage.input_tokens shouldBe Some(714)
      response.requestId shouldBe Some("b922c927-f8d7-46a2")

      // a second image is refused before sending
      respond(200, scaleDownResponse)
      an[IllegalArgumentException] should be thrownBy
        scaleDown.systemOne(
          Json.arr(DecisionImage.part(png), DecisionImage.part(png)),
          questions
        )
      received shouldBe None

      // its decision models only, with what they read
      respond(200, scaleDownModels)
      await(scaleDown.listModels) shouldBe Seq(
        ModelMetadata(
          "classify-1",
          "Classify is a Constrained Decoding Model (CDM) from ScaleDown.",
          "2026-10-13",
          Some(Seq("text", "image", "file"))
        )
      )
      received.get.path shouldBe "/v1/models"
      received.get.headers("x-api-key") shouldBe "sd_k"

      // the body is validated before the credits are checked: a 402 for a valid one without
      // them, a 422 FastAPI list for an invalid one
      respond(402, """{"detail":"Insufficient credits"}""")
      the[TypeSafeScalaApiUsageException]
        .thrownBy(await(scaleDown.systemOne("x", questions)))
        .getMessage should include("Insufficient credits")
      respond(
        422,
        """{"detail":[{"type":"value_error","loc":["body"],"msg":"Value error, Unsupported model 'bogus'. Supported: classify, classify-1.","input":{},"ctx":{"error":{}}}]}"""
      )
      the[TypeSafeScalaInvalidRequestException]
        .thrownBy(await(scaleDown.systemOne(JsString("x"), questions, model = "bogus")))
        .getMessage should include("Unsupported model 'bogus'")

      // the OpenAI adapter's state of instructions and a message goes as its text too
      val asOpenAI = TypeSafeServiceFactory.asOpenAI(scaleDown, imageInput = true)
      respond(200, scaleDownResponse)
      await(
        asOpenAI.createChatCompletion(
          Seq(
            io.cequence.openaiscala.domain.SystemMessage("Judge product reviews."),
            io.cequence.openaiscala.domain.UserMessage(review)
          ),
          io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings(
            model = DecisionModelId.scaledown_classify_1,
            response_format_type = Some(
              io.cequence.openaiscala.domain.settings.ChatCompletionResponseFormatType.json_schema
            ),
            jsonSchema = Some(
              io.cequence.openaiscala.domain.settings.JsonSchemaDef(
                "review",
                strict = true,
                Left(
                  io.cequence.openaiscala.domain.JsonSchema.Object(
                    properties = Seq(
                      "defect" -> io.cequence.openaiscala.domain.JsonSchema
                        .Boolean(Some("Does the review report a product defect?"))
                    ),
                    required = Seq("defect")
                  )
                )
              )
            )
          )
        )
      )
      (Json.parse(received.get.body) \ "state").get shouldBe JsString(
        Json.prettyPrint(
          Json.obj("instructions" -> "Judge product reviews.", "message" -> review)
        )
      )
      scaleDown.close()
    }

    "send OpenRouter's scaledown/ models ScaleDown's text, its other models any JSON" in {
      val openRouter = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.openRouter.copy(baseUrl = baseUrl + "/api"),
        "or_k"
      )
      val state = Json.obj("ticket" -> "I was charged twice.")
      val refund = Map("refund" -> NoulQuestion("Does the customer ask for money back?"))

      respond(200, scaleDownResponse.replace("defect", "refund"))
      await(
        openRouter
          .systemOne(state, refund, model = DecisionModelId.openrouter_scaledown_classify_1)
      )
      received.get.headers("authorization") shouldBe "Bearer or_k"
      (Json.parse(received.get.body) \ "state").get shouldBe JsString(Json.prettyPrint(state))

      respond(200, quickStartResponse)
      await(openRouter.systemOne(state, refund))
      (Json.parse(received.get.body) \ "state").get shouldBe state
      openRouter.close()
    }

    // the shape Jev 1.14 answered live on 2026-10-14 (values rounded)
    val jevBoxResponse =
      """{"model":"jev-1.14.0","answers":{"red_box":{"type":"box","box":{"top":0.49,"bottom":0.82,"left":0.25,"right":0.5},"confidence":0.81},"has_red":{"type":"noul","noul":0.99}},"usage":{"input_tokens":1637,"output_tokens":72}}"""

    "send TypeSafe an image as an asset under a placeholder, opted in by header, and read a box" in {
      val png = pngDataUrl(800, 600)
      val boxQuestions = Map[String, Question](
        "red_box" -> BoxQuestion("the red rectangle in `photo`"),
        "has_red" -> NoulQuestion("Is there a red rectangle in `photo`?")
      )

      respond(200, jevBoxResponse)
      val response = await(
        service.systemOne(
          Json.obj("photo" -> DecisionImage.part(png)),
          boxQuestions,
          DecisionModelId.jev_1_14_0
        )
      )
      received.get.path shouldBe "/v1/systemone"
      received.get.headers("typesafe-danger-zone-images") shouldBe "true"
      received.get.headers.get("typesafe-danger-zone-long-context") shouldBe None
      val body = Json.parse(received.get.body)
      val placeholder = (body \ "state" \ "photo").as[String]
      (body \ "assets" \ placeholder).get shouldBe Json.obj(
        "type" -> "image",
        "format" -> "png",
        "data_base64" -> png.stripPrefix("data:image/png;base64,")
      )
      (body \ "questions" \ "red_box").get shouldBe
        Json.obj("type" -> "box", "instructions" -> "the red rectangle in `photo`")

      response
        .box("red_box") shouldBe BoxAnswer(BoundingBox(0.49, 0.82, 0.25, 0.5), Some(0.81))
      response.boxes.keySet shouldBe Set("red_box")
      response.box("red_box").box.inPixels(800, 600) shouldBe ((200, 294, 400, 492))

      // text only: no assets, no opt-in
      respond(200, quickStartResponse)
      await(service.systemOne("x", questions))
      received.get.headers.get("typesafe-danger-zone-images") shouldBe None
      (Json.parse(received.get.body) \ "assets").toOption shouldBe None

      // an image the API would refuse is refused before sending - Jev never resizes
      respond(200, jevBoxResponse)
      the[IllegalArgumentException]
        .thrownBy(
          service.systemOne(Json.arr(DecisionImage.part(pngDataUrl(64, 64))), questions)
        )
        .getMessage should include("256 to 1568 pixels a side")
      received shouldBe None

      // the API's own refusal of a request without the opt-in, as answered live
      respond(
        400,
        """{"detail":{"error_type":"api_usage_error","message":"Images are experimental. Send the header TypeSafe-Danger-Zone-Images: true to use them."}}"""
      )
      the[TypeSafeScalaApiUsageException]
        .thrownBy(await(service.systemOne("x", boxQuestions)))
        .getMessage should include("Images are experimental")
    }

    "refuse a box question before sending to a host that answers none - natively and through the JSON schema adapter" in {
      val questions =
        Map("mug" -> BoxQuestion("the coffee mug"), "red" -> NoulQuestion("Is the mug red?"))
      Seq(
        DecisionProviderSettings.liquid,
        DecisionProviderSettings.perplexity,
        DecisionProviderSettings.subq,
        DecisionProviderSettings.openRouter,
        DecisionProviderSettings.vercel,
        DecisionProviderSettings.microsoftFoundry(baseUrl)
      ).foreach { preset =>
        preset.boxQuestions shouldBe false
        val local = preset.copy(baseUrl = baseUrl)
        val other = TypeSafeServiceFactory.withEngine(engine, local, "k")
        received = None
        the[IllegalArgumentException]
          .thrownBy(other.systemOne(JsString("A photo."), questions))
          .getMessage should include(s"${local.label} answers no box questions (mug)")

        // a BoundingBox-shaped field of the JSON schema adapter - an OpenAI exception there
        val fraction = io.cequence.openaiscala.domain.JsonSchema
          .Number(minimum = Some(0), maximum = Some(1))
        val adapterFailure = the[io.cequence.openaiscala.OpenAIScalaClientException] thrownBy
          await(
            TypeSafeServiceFactory
              .asOpenAI(other)
              .createChatCompletion(
                Seq(io.cequence.openaiscala.domain.UserMessage("A photo.")),
                io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings(
                  model = preset.defaultModel,
                  response_format_type = Some(
                    io.cequence.openaiscala.domain.settings.ChatCompletionResponseFormatType.json_schema
                  ),
                  jsonSchema = Some(
                    io.cequence.openaiscala.domain.settings.JsonSchemaDef(
                      "mug",
                      strict = true,
                      Left(
                        io.cequence.openaiscala.domain.JsonSchema.Object(
                          properties = Seq(
                            "mug" -> io.cequence.openaiscala.domain.JsonSchema.Object(
                              properties = Seq(
                                "top" -> fraction,
                                "bottom" -> fraction,
                                "left" -> fraction,
                                "right" -> fraction
                              ),
                              required = Seq("top", "bottom", "left", "right")
                            )
                          ),
                          required = Seq("mug")
                        )
                      )
                    )
                  )
                )
              )
          )
        adapterFailure.getMessage should include("answers no box questions (mug)")
        received shouldBe None
        other.close()
      }
      DecisionProviderSettings.typeSafe.boxQuestions shouldBe true
      DecisionProviderSettings.typeSafeLongContext.boxQuestions shouldBe true
    }

    "send a box question to a host whose provider says it answers them" in {
      val custom = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.liquid.copy(baseUrl = baseUrl, boxQuestions = true),
        "k"
      )
      respond(200, jevBoxResponse)
      await(
        custom.systemOne(
          JsString("A photo."),
          Map(
            "red_box" -> BoxQuestion("the red rectangle"),
            "has_red" -> NoulQuestion("Is there a red rectangle?")
          )
        )
      ).box("red_box").confidence shouldBe Some(0.81)
      (Json.parse(received.get.body) \ "questions" \ "red_box" \ "type")
        .as[String] shouldBe "box"
      custom.close()
    }

    "say why Jev before 1.14 answers a box question with a bare 400" in {
      val invalid =
        """{"detail":{"error_type":"api_usage_error","message":"Invalid request."}}"""
      respond(400, invalid)
      val failure = the[TypeSafeScalaApiUsageException].thrownBy(
        await(
          service.systemOne(
            JsString("A photo."),
            Map("mug" -> BoxQuestion("the coffee mug")),
            DecisionModelId.jev_latest
          )
        )
      )
      failure.getMessage should include("Invalid request.")
      failure.getMessage should include("box questions need Jev 1.14 or later")
      failure.httpCode shouldBe Some(400)
      failure.getCause shouldBe a[TypeSafeScalaApiUsageException]

      // the same 400 without a box question stays as it is
      respond(400, invalid)
      the[TypeSafeScalaApiUsageException]
        .thrownBy(await(service.systemOne("x", Map("q" -> NoulQuestion("Is it?")))))
        .getMessage should not include "box questions"
    }

    "send TypeSafe's long-context opt-in with every request of its preset" in {
      val longContext = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.typeSafeLongContext.copy(baseUrl = baseUrl),
        "k"
      )

      respond(200, quickStartResponse)
      await(longContext.systemOne("a long contract", questions))
      received.get.headers("typesafe-danger-zone-long-context") shouldBe "true"
      received.get.headers.get("typesafe-danger-zone-images") shouldBe None

      respond(200, """{"models":[]}""")
      await(longContext.listModels)
      received.get.headers("typesafe-danger-zone-long-context") shouldBe "true"
      longContext.close()
    }

    // a chat completion whose user message carries a picture, through an OpenAI adapter
    def imageChat(
      adapter: io.cequence.openaiscala.service.OpenAIChatCompletionService,
      png: String
    ) =
      adapter.createChatCompletion(
        Seq(
          io.cequence.openaiscala.domain.UserSeqMessage(
            Seq(
              io.cequence.openaiscala.domain.TextContent("A photo."),
              io.cequence.openaiscala.domain.ImageURLContent(png)
            )
          )
        ),
        io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings(
          model = DecisionModelId.jev_latest,
          response_format_type = Some(
            io.cequence.openaiscala.domain.settings.ChatCompletionResponseFormatType.json_schema
          ),
          jsonSchema = Some(
            io.cequence.openaiscala.domain.settings.JsonSchemaDef(
              "square",
              strict = true,
              Left(
                io.cequence.openaiscala.domain.JsonSchema.Object(
                  properties = Seq(
                    "red" -> io.cequence.openaiscala.domain.JsonSchema
                      .Boolean(Some("Is the shape red?"))
                  ),
                  required = Seq("red")
                )
              )
            )
          )
        )
      )

    "map a chat message's image into TypeSafe's assets through the OpenAI adapter - " +
      "asOpenAI(service) knowing its host reads images, also through the retry adapter" in {
        import io.cequence.openaiscala.RetryHelpers.RetrySettings
        implicit val retrySettings: RetrySettings =
          RetrySettings(maxRetries = 2, delayOffset = 10.millis)
        implicit val system: akka.actor.ActorSystem =
          akka.actor.ActorSystem("image-adapter-spec")
        implicit val scheduler: akka.actor.Scheduler = system.scheduler

        try {
          val retrying = TypeSafeServiceAdapters.retry(service)
          retrying.readsImages shouldBe true
          val asOpenAI = TypeSafeServiceFactory.asOpenAI(retrying)
          val png = pngDataUrl(512, 512)

          respond(
            200,
            """{"model":"jev-1.13.0","answers":{"red":{"type":"noul","noul":0.98}},"usage":{"input_tokens":1033,"output_tokens":20}}"""
          )
          await(imageChat(asOpenAI, png))
          received.get.headers("typesafe-danger-zone-images") shouldBe "true"
          val body = Json.parse(received.get.body)
          val state = (body \ "state").as[Seq[String]]
          state.head shouldBe "A photo."
          (body \ "assets" \ state(1) \ "data_base64").as[String] shouldBe
            png.stripPrefix("data:image/png;base64,")
        } finally {
          system.terminate()
        }
      }

    "refuse a chat message's image before sending when the service's host reads none" in {
      val textOnly = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.typeSafe
          .copy(baseUrl = baseUrl, images = DecisionImages.Unsupported),
        "k"
      )
      textOnly.readsImages shouldBe false
      received = None

      val refused = the[io.cequence.openaiscala.OpenAIScalaClientException] thrownBy
        await(imageChat(TypeSafeServiceFactory.asOpenAI(textOnly), pngDataUrl(512, 512)))
      refused.getMessage should include("Image content goes only to a host that reads images")
      received shouldBe None
      textOnly.close()
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

    "answer OpenAI's Decisions API interface on Jev - System One on the wire" in {
      respond(200, quickStartResponse)
      val decisions = TypeSafeServiceFactory.asOpenAIDecisions(service)

      val decision = await(
        decisions.createDecision(
          DecisionInput.Text("My invoice is wrong and I need it fixed today."),
          Seq(
            DecisionQuestion.Choice(
              "Which team should handle this",
              Seq(
                DecisionChoice("billing", "Payment or subscription issues"),
                DecisionChoice("technical", "Bugs or integration problems"),
                DecisionChoice("sales", "Pricing or account questions")
              ),
              Some("department")
            ),
            DecisionQuestion.Predicate(
              "The message conveys urgency or time-sensitivity",
              Some("is_urgent")
            )
          )
        )
      )

      received.get.path shouldBe "/v1/systemone"
      val body = Json.parse(received.get.body)
      (body \ "model").as[String] shouldBe "jev-latest"
      (body \ "questions").as[JsObject] shouldBe Json.toJson(questions)
      decision.model shouldBe "jev-1.12"
      decision.answers.map(_.name) shouldBe Seq(Some("department"), Some("is_urgent"))
      decision.answer("is_urgent") shouldBe Some(
        DecisionAnswer.Predicate(Some("is_urgent"), 0.999)
      )
      decision.usage.map(_.totalTokens) shouldBe Some(360)

      respond(
        401,
        """{"detail":{"error_type":"authentication_error","message":"Invalid API key"}}"""
      )
      val error = Await.result(
        decisions
          .createDecision(DecisionInput.Text("x"), Seq(DecisionQuestion.Predicate("?")))
          .failed,
        20.seconds
      )
      error shouldBe an[io.cequence.openaiscala.OpenAIScalaUnauthorizedException]
      error.getCause shouldBe a[TypeSafeScalaUnauthorizedException]
    }

    "send an image of OpenAI's Decisions interface to Jev as an asset, opted in by header" in {
      respond(
        200,
        """{"model":"jev-1.14.0","answers":{"red":{"type":"noul","noul":0.99}},"usage":{"input_tokens":1033,"output_tokens":20}}"""
      )
      val decisions = TypeSafeServiceFactory.asOpenAIDecisions(service)
      val png = pngDataUrl(512, 512)

      val decision = await(
        decisions.createDecision(
          DecisionInput.Messages(
            Seq(
              DecisionMessage(
                Seq(DecisionContent.InputText("A photo."), DecisionContent.InputImage(png))
              )
            )
          ),
          Seq(DecisionQuestion.Predicate("Is the shape in the photo red?", Some("red"))),
          CreateDecisionSettings(Some(DecisionModelId.jev_1_14_0))
        )
      )

      received.get.headers("typesafe-danger-zone-images") shouldBe "true"
      val body = Json.parse(received.get.body)
      val state = (body \ "state").as[Seq[String]]
      state.head shouldBe "A photo."
      (body \ "assets" \ state(1) \ "data_base64").as[String] shouldBe
        png.stripPrefix("data:image/png;base64,")
      decision.answer("red") shouldBe Some(DecisionAnswer.Predicate(Some("red"), 0.99))

      // long context through the same interface - the preset's header
      val longContext = TypeSafeServiceFactory.asOpenAIDecisions(
        TypeSafeServiceFactory.withEngine(
          engine,
          DecisionProviderSettings.typeSafeLongContext.copy(baseUrl = baseUrl),
          "k"
        )
      )
      respond(200, quickStartResponse)
      await(
        longContext.createDecision(
          DecisionInput.Text("a long contract"),
          Seq(DecisionQuestion.Predicate("Does it have a termination clause?"))
        )
      )
      received.get.headers("typesafe-danger-zone-long-context") shouldBe "true"
      longContext.close()
    }

    "check the images of a Decisions input before sending - the format, a host's cap" in {
      // the checks systemOne makes, on createDecision too (until 2026-10-09 it skipped them)
      respond(200, "{}")
      val capped = TypeSafeServiceFactory.asOpenAIDecisions(
        TypeSafeServiceFactory.withEngine(
          engine,
          DecisionProviderSettings.openAI.copy(baseUrl = baseUrl, maxImageTiles = Some(2048)),
          "sk-test"
        )
      )
      val asked = Seq(
        DecisionQuestion
          .Choice("Is it blue?", Seq(DecisionChoice(true), DecisionChoice(false)))
      )

      received = None
      val oversized = await(
        capped
          .createDecision(
            DecisionInput.of(DecisionContent.InputImage(pngDataUrl(4032, 3024))),
            asked
          )
          .failed
      )
      oversized shouldBe an[io.cequence.openaiscala.OpenAIScalaClientException]
      oversized.getMessage should (include("4032 x 3024") and include("2048"))
      received shouldBe None

      await(
        capped
          .createDecision(
            DecisionInput.of(DecisionContent.InputImage("https://example.com/cat.png")),
            asked
          )
          .failed
      ).getMessage should include("never fetches")
      received shouldBe None
      capped.close()
    }

    "answer OpenAI's Decisions API interface on OpenAI's host as it is" in {
      // recorded from https://api.openai.com/v1/decisions on 2026-10-07
      val lunaReply =
        """{"model":"gpt-6-luna","answers":[{"type":"choice","name":null,"choice":true,"probabilities":[{"value":true,"probability":1.0},{"value":false,"probability":0.0}],"confidence":1.0}],"usage":{"input_tokens":118,"input_tokens_details":{"cached_tokens":0,"cache_write_tokens":0},"output_tokens":0,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":118}}"""
      respond(200, lunaReply, Map("x-request-id" -> "req_luna_1"))
      val decisions = TypeSafeServiceFactory.asOpenAIDecisions(
        TypeSafeServiceFactory.withEngine(
          engine,
          DecisionProviderSettings.openAI.copy(baseUrl = baseUrl),
          "sk-test"
        )
      )
      val input = DecisionInput.of(
        DecisionContent.InputText("Is the photo blue?"),
        DecisionContent.InputImage(pngDataUrl(64, 64), Some(DecisionImageDetail.low))
      )
      val asked = Seq(
        DecisionQuestion
          .Choice("Is it blue?", Seq(DecisionChoice(true), DecisionChoice(false)))
      )

      val decision = await(
        decisions
          .createDecision(input, asked, CreateDecisionSettings(safetyIdentifier = Some("u1")))
      )

      // nothing translated: the detail, the boolean values, the unnamed question, the safety id
      received.get.path shouldBe "/v1/decisions"
      Json.parse(received.get.body) shouldBe createDecisionBody(
        input,
        asked,
        CreateDecisionSettings(Some("gpt-6-luna"), Some("u1"))
      )
      decision.answers.head shouldBe DecisionAnswer.Choice(
        None,
        DecisionValue.Bool(true),
        Seq(
          ChoiceProbability(DecisionValue.Bool(true), 1.0),
          ChoiceProbability(DecisionValue.Bool(false), 0.0)
        ),
        1.0
      )
      decision.requestId shouldBe Some("req_luna_1")

      // the retry adapter keeps the native path - the body is the same, with the safety id
      {
        import io.cequence.openaiscala.RetryHelpers.RetrySettings
        implicit val retrySettings: RetrySettings =
          RetrySettings(maxRetries = 2, delayOffset = 10.millis)
        implicit val system: akka.actor.ActorSystem = akka.actor.ActorSystem("decisions-retry")
        implicit val scheduler: akka.actor.Scheduler = system.scheduler
        val retried = TypeSafeServiceFactory.asOpenAIDecisions(
          TypeSafeServiceAdapters.retry(
            TypeSafeServiceFactory.withEngine(
              engine,
              DecisionProviderSettings.openAI.copy(baseUrl = baseUrl),
              "sk-test"
            )
          )
        )

        try {
          var calls = 0
          onRequest = () => {
            calls += 1
            if (calls < 2)
              (529, """{"error":{"message":"overloaded"}}""", Map.empty[String, String])
            else (200, lunaReply, Map("x-request-id" -> "req_luna_2"))
          }
          val retriedDecision = await(
            retried.createDecision(
              input,
              asked,
              CreateDecisionSettings(safetyIdentifier = Some("u1"))
            )
          )
          onRequest = null
          calls shouldBe 2
          Json.parse(received.get.body) shouldBe createDecisionBody(
            input,
            asked,
            CreateDecisionSettings(Some("gpt-6-luna"), Some("u1"))
          )
          retriedDecision.requestId shouldBe Some("req_luna_2")
        } finally {
          onRequest = null
          retried.close()
          await(system.terminate())
        }
      }

      respond(
        400,
        """{"error":{"message":"Decision input exceeds the token limit.","type":"invalid_request_error","param":null,"code":null}}"""
      )
      Await.result(decisions.createDecision(input, asked).failed, 20.seconds) shouldBe
        an[io.cequence.openaiscala.OpenAIScalaTokenCountExceededException]
      decisions.close()
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

      // a host that needs no key gets none
      provider.copy(apiKeyRequired = false).apiKeyFromEnv shouldBe ""
    }
  }

  "listModels" should {

    "list only a llama.cpp router's decision models, with what they read" in {
      // recorded from llama-server b11476 in router mode on 2026-10-07, trimmed, plus a chat
      // model next to them
      respond(
        200,
        """{"data":[
          |  {"id":"Julia-1-Q8_0","aliases":[],"tags":[],"object":"model","owned_by":"llamacpp","created":1791394484,"status":{"value":"unloaded"},"architecture":{"input_modalities":["text"],"output_modalities":["decisions"]},"source":"models_dir","can_remove":false},
          |  {"id":"gemma-3-4b-it-Q4_K_M","object":"model","owned_by":"llamacpp","created":1791394484,"architecture":{"input_modalities":["text","image"],"output_modalities":["text"]}},
          |  {"id":"old-server-model","object":"model","owned_by":"llamacpp","created":1791394484}
          |]}""".stripMargin
      )
      val llamaCpp = TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.llamaCpp.copy(baseUrl = baseUrl),
        ""
      )

      await(llamaCpp.listModels) shouldBe Seq(
        ModelMetadata("Julia-1-Q8_0", "", "2026-10-07", Some(Seq("text"))),
        // an older server says nothing about it - kept
        ModelMetadata("old-server-model", "", "2026-10-07")
      )
      received.get.path shouldBe "/v1/models"
      received.get.query shouldBe None
      llamaCpp.close()
    }

    "read what Liquid's models take" in {
      // recorded from https://api.liquid.ai/decisions/v1/models on 2026-10-07
      respond(
        200,
        """{"models":[{"name":"d1","description":"","release_date":"2026-09-29","input_modalities":["text","image"]},{"name":"d1:free","description":"","release_date":"2026-09-22","input_modalities":["text"]}]}"""
      )
      val liquid =
        TypeSafeServiceFactory.liquidWithEngine(engine, "liquid_k", baseUrl = baseUrl)

      await(liquid.listModels).map(m => m.name -> m.input_modalities) shouldBe Seq(
        "d1" -> Some(Seq("text", "image")),
        "d1:free" -> Some(Seq("text"))
      )
      liquid.close()
    }

    "return Perplexity's decision models without a request" in {
      respond(200, "{}")
      val perplexity =
        TypeSafeServiceFactory.perplexityWithEngine(engine, "pplx_k", baseUrl = baseUrl)

      await(perplexity.listModels).map(_.name) shouldBe Seq(
        DecisionModelId.pplx_decider_v1_2,
        DecisionModelId.pplx_decider_flash_v1_2,
        DecisionModelId.pplx_decider_v1_1_27b,
        DecisionModelId.pplx_decider_v1_27b
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
