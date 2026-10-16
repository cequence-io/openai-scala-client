package io.cequence.openaiscala.service

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.{JsonFormats, OpenAIScalaClientException}
import io.cequence.openaiscala.domain.response.ModelInfo
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{Format, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.{util => ju}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.io.Source

/**
 * `listModels` / `retrieveModel` against a local server replaying the listings of the
 * OpenAI-compatible hosts (recorded 2026-10-15, two models each, in
 * `resources/model-listings/`): they agree on nothing but the `id` - no `created` from
 * Gemini's OpenAI endpoint, DeepSeek, SambaNova and Cohere, no `owned_by` from OpenRouter and
 * Together, a bare array from Together, a `created_at` date string from Anthropic. Every one
 * of them failed to parse until then.
 */
class ModelListingWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  @volatile private var reply: String = "{}"

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val service = OpenAIServiceFactory.customEngineInstance(
    engine,
    s"http://localhost:${server.getAddress.getPort}/v1/",
    WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))
  )

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val bytes = reply.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(200, bytes.length.toLong)
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

  private def listing(name: String): String = {
    val source = Source.fromResource(s"model-listings/$name.json")(scala.io.Codec.UTF8)
    try source.mkString
    finally source.close()
  }

  private def listModels(json: String): Seq[ModelInfo] = {
    reply = json
    Await.result(service.listModels, 20.seconds)
  }

  private def seconds(epochSeconds: Long) = Some(new ju.Date(epochSeconds * 1000))

  "listModels" should {

    "read OpenAI's own listing" in {
      listModels(listing("openai")).map(m => (m.id, m.created, m.owned_by)) shouldBe Seq(
        ("tts-1", seconds(1681940951L), Some("openai-internal")),
        ("gpt-5-mini", seconds(1754425928L), Some("system"))
      )
    }

    "read the listings without a creation date" in {
      Seq(
        "gemini-openai" -> Seq(
          "models/gemini-2.5-flash" -> "google",
          "models/gemini-2.5-pro" -> "google"
        ),
        "deepseek" -> Seq("deepseek-flash" -> "deepseek", "deepseek-v4-pro" -> "deepseek"),
        "sambanova" -> Seq(
          "DeepSeek-V3.1" -> "no-reply@sambanova.ai",
          "DeepSeek-V3.2" -> "no-reply@sambanova.ai"
        ),
        "cohere" -> Seq("c4ai-aya-expanse-32b" -> "cohere", "c4ai-aya-vision-32b" -> "cohere")
      ).foreach { case (host, models) =>
        withClue(host) {
          listModels(listing(host)).map(m => (m.id, m.created, m.owned_by)) shouldBe
            models.map { case (id, owner) => (id, None, Some(owner)) }
        }
      }
    }

    "read OpenRouter's listing, which names no owner" in {
      listModels(listing("openrouter")).map(m => (m.id, m.created, m.owned_by)) shouldBe Seq(
        ("~unbiased/pareto-latest", seconds(1791933050L), None),
        ("unbiased/pareto-26.9", seconds(1791930537L), None)
      )
    }

    "read Together's listing - a bare array, no owner" in {
      listModels(listing("together")).map(m => (m.id, m.created, m.owned_by)) shouldBe Seq(
        ("deepseek-ai/DeepSeek-V4.1-Flash", seconds(1789197495L), None),
        ("zai-org/GLM-5.3", seconds(1787957543L), None)
      )
    }

    "read Anthropic's listing - created_at as a date string" in {
      listModels(listing("anthropic")).map(m => (m.id, m.created, m.owned_by)) shouldBe Seq(
        ("claude-haiku-5-5", Some(ju.Date.from(Instant.parse("2026-10-07T18:00:00Z"))), None),
        ("claude-sonnet-5-5", Some(ju.Date.from(Instant.parse("2026-09-28T00:00:00Z"))), None)
      )
    }

    "fail clearly on a response that lists no models" in {
      val failure =
        the[OpenAIScalaClientException] thrownBy listModels("""{"object":"list"}""")
      failure.getMessage should include("Neither a 'data' attribute nor an array of models")
    }
  }

  "retrieveModel" should {

    "read a model without a creation date or an owner" in {
      reply =
        """{"id":"models/gemini-2.5-flash","object":"model","display_name":"Gemini 2.5 Flash"}"""
      Await.result(service.retrieveModel("gemini-2.5-flash"), 20.seconds) shouldBe Some(
        ModelInfo("models/gemini-2.5-flash")
      )
    }
  }

  "The model format" should {
    implicit val format: Format[ModelInfo] = JsonFormats.modelSpecFormat

    "read a numeric string and a date with an offset, and drop a field of an unexpected type" in {
      Json
        .parse("""{"id":"a","created":"1681940951","owned_by":{"org":"x"}}""")
        .as[ModelInfo] shouldBe
        ModelInfo("a", seconds(1681940951L))
      Json
        .parse("""{"id":"b","created_at":"2026-10-07T20:00:00+02:00"}""")
        .as[ModelInfo] shouldBe
        ModelInfo("b", Some(ju.Date.from(Instant.parse("2026-10-07T18:00:00Z"))))
      Json.parse("""{"id":"c","created":true,"created_at":"soon"}""").as[ModelInfo] shouldBe
        ModelInfo("c")
    }

    "write what it reads" in {
      val model = ModelInfo("gpt-5-mini", seconds(1754425928L), Some("system"))
      Json.toJson(model).as[ModelInfo] shouldBe model
      Json.toJson(ModelInfo("x")) shouldBe Json.obj("id" -> "x", "permission" -> Json.arr())
    }
  }
}
