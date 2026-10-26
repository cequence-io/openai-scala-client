package io.cequence.openaiscala.gemini.service.impl

import akka.actor.ActorSystem
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ChatCompletionBatchRequest, SystemMessage, UserMessage}
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.service._
import io.cequence.wsclient.domain.{SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsValue, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Gemini batch + system-message caching, against a local server: a cache created from a system
 * message (`enableCacheSystemMessage`) may replace the items' own system messages only when
 * EVERY request carries exactly that message. Otherwise each item keeps its own system
 * instruction and no cache is created (it used to take the first request's message and run the
 * whole batch under it).
 */
class GeminiBatchSystemCacheSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("gemini-batch-system-cache")

  // (path, request body)
  @volatile private var calls = Vector.empty[(String, JsValue)]

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val baseUrl = s"http://localhost:${server.getAddress.getPort}/v1beta/"
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())

  private lazy val service: GeminiService =
    new GeminiServiceImpl("k", None, Some(engine)) {
      override protected val site: SiteBinding = SiteBinding(
        baseUrl,
        WsRequestContext(authHeaders = Seq("x-goog-api-key" -> "k")),
        label = Some("gemini")
      )
    }
  private lazy val adapter = GeminiServiceFactory.asOpenAI(service)

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val requestBody =
            new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          val path = exchange.getRequestURI.getPath
          synchronized {
            calls = calls :+ ((path, Json.parse(requestBody)))
          }

          val reply =
            if (path.endsWith("/cachedContents"))
              """{"name":"cachedContents/c1","model":"models/gemini-3.5-flash","ttl":"300s"}"""
            else
              """{"name":"batches/b1","metadata":{"name":"batches/b1","state":"BATCH_STATE_PENDING"}}"""

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
    adapter.close()
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private val model = "gemini-3.5-flash"

  private val cachingSettings =
    CreateChatCompletionSettings(model).enableCacheSystemMessage(true)

  private def request(
    id: String,
    systemPrompt: String
  ) = ChatCompletionBatchRequest(
    id,
    Seq(SystemMessage(systemPrompt), UserMessage(s"question $id"))
  )

  // runs the batch and returns the cachedContents create calls and the batch items' bodies
  private def runBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): (Seq[JsValue], Seq[(String, JsObject)]) = {
    calls = Vector.empty
    Await.result(adapter.createChatCompletionBatch(requests, settings), 20.seconds)

    val recorded = calls
    val cacheCalls = recorded.collect {
      case (path, body) if path.endsWith("/cachedContents") => body
    }

    val batchBody = recorded.collectFirst {
      case (path, body) if path.endsWith(":batchGenerateContent") => body
    }.getOrElse(fail("no batchGenerateContent call"))

    val items =
      (batchBody \ "batch" \ "input_config" \ "requests" \ "requests").as[Seq[JsObject]].map {
        item =>
          (item \ "metadata" \ "key").as[String] -> (item \ "request").as[JsObject]
      }

    (cacheCalls, items)
  }

  private def systemInstructionText(item: JsObject): Option[String] =
    (item \ "system_instruction" \ "parts" \ 0 \ "text").asOpt[String]

  private def cachedContent(item: JsObject): Option[String] =
    (item \ "cached_content").asOpt[String]

  "a Gemini batch with enableCacheSystemMessage" should {

    "keep every request's own system message and create no cache when they differ" in {
      val (cacheCalls, items) = runBatch(
        Seq(request("r1", "You are a poet."), request("r2", "You are a tax advisor.")),
        cachingSettings
      )

      cacheCalls shouldBe empty
      items.map(_._1) shouldBe Seq("r1", "r2")
      items.map { case (_, item) => systemInstructionText(item) } shouldBe
        Seq(Some("You are a poet."), Some("You are a tax advisor."))
      items.map { case (_, item) => cachedContent(item) } shouldBe Seq(None, None)
    }

    "keep the own system message of a request when only some of the others have one" in {
      val (cacheCalls, items) = runBatch(
        Seq(
          request("r1", "You are a poet."),
          ChatCompletionBatchRequest("r2", Seq(UserMessage("question r2")))
        ),
        cachingSettings
      )

      cacheCalls shouldBe empty
      items.map { case (_, item) => systemInstructionText(item) } shouldBe
        Seq(Some("You are a poet."), None)
      items.map { case (_, item) => cachedContent(item) } shouldBe Seq(None, None)
    }

    "create one cache and reference it from every item when the system messages are identical" in {
      val (cacheCalls, items) = runBatch(
        Seq(request("r1", "You are a poet."), request("r2", "You are a poet.")),
        cachingSettings
      )

      cacheCalls should have size 1
      (cacheCalls.head \ "systemInstruction" \ "parts" \ 0 \ "text")
        .as[String] shouldBe "You are a poet."

      items.map { case (_, item) => cachedContent(item) } shouldBe
        Seq(Some("cachedContents/c1"), Some("cachedContents/c1"))
      items.map { case (_, item) => systemInstructionText(item) } shouldBe Seq(None, None)
    }

    "keep the per-request system messages when caching is not enabled" in {
      val (cacheCalls, items) = runBatch(
        Seq(request("r1", "You are a poet."), request("r2", "You are a tax advisor.")),
        CreateChatCompletionSettings(model)
      )

      cacheCalls shouldBe empty
      items.map { case (_, item) => systemInstructionText(item) } shouldBe
        Seq(Some("You are a poet."), Some("You are a tax advisor."))
    }

    "let an explicit cache name replace the items' system messages" in {
      val (cacheCalls, items) = runBatch(
        Seq(request("r1", "You are a poet."), request("r2", "You are a tax advisor.")),
        CreateChatCompletionSettings(model).setSystemCacheName("cachedContents/prebuilt")
      )

      cacheCalls shouldBe empty
      items.map { case (_, item) => cachedContent(item) } shouldBe
        Seq(Some("cachedContents/prebuilt"), Some("cachedContents/prebuilt"))
      items.map { case (_, item) => systemInstructionText(item) } shouldBe Seq(None, None)
    }
  }
}
