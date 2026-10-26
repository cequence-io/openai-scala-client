package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  ReasoningEffort,
  ServiceTier
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import java.net.{InetSocketAddress, URI}
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * MiniMax's OpenAI-compatible chat API (`ChatProviderSettings.minimax` / `minimaxChina`) on
 * the wire: the real request serializers and transports against a local server, no API key
 * (contributed in #131). MiniMax-M3.1-Flash always thinks, so `none` / `minimal` go out as
 * `low` and `service_tier` is dropped, per MiniMax's docs (`miniMaxFlash`, pinned rule by rule
 * in `ModelConversionsRoutingSpec`). The Messages API side is `MiniMaxMessagesWireSpec`
 * (anthropic-client).
 */
class MiniMaxWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("minimax-wire")
  private implicit val materializer: Materializer = Materializer(system)

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  @volatile private var lastBody: JsObject = Json.obj()
  @volatile private var lastPath = ""

  private val chatResponse =
    """{"id":"chat-1","object":"chat.completion","created":1700000000,"model":"MiniMax-M3.1-Flash","choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""

  private val chatStream =
    """data: {"id":"chat-1","object":"chat.completion.chunk","created":1700000000,"model":"MiniMax-M3.1-Flash","choices":[{"index":0,"delta":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}]}
      |
      |data: [DONE]
      |
      |""".stripMargin

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          lastBody = Json.parse(exchange.getRequestBody.readAllBytes()).as[JsObject]
          lastPath = exchange.getRequestURI.getPath

          val streamed = (lastBody \ "stream").asOpt[Boolean].contains(true)
          val (response, contentType) =
            if (streamed)
              (chatStream, "text/event-stream")
            else
              (chatResponse, "application/json")

          val bytes = response.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", contentType)
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def await[A](future: Future[A]): A = Await.result(future, 20.seconds)

  // the provider's path under a per-region prefix on the local server
  private def localUrl(
    region: String,
    path: String
  ): String =
    s"http://localhost:${server.getAddress.getPort}/$region$path"

  private val regions = Seq(
    ("global", ChatProviderSettings.minimax, "api.minimax.io"),
    ("china", ChatProviderSettings.minimaxChina, "api.minimaxi.com")
  )

  private val messages = Seq(UserMessage("Hello!"))
  private val flash = CreateChatCompletionSettings(NonOpenAIModelId.minimax_m3_1_flash)

  "MiniMax's chat API" should {

    "get Flash through both regional paths, adding nothing it refuses" in {
      regions.foreach { case (region, provider, host) =>
        val endpoint = new URI(provider.coreUrl)
        endpoint.getHost shouldBe host
        endpoint.getPath shouldBe "/v1/"

        val service = OpenAIChatCompletionServiceFactory(localUrl(region, endpoint.getPath))
        try {
          await(service.createChatCompletion(messages, flash))
          lastPath shouldBe s"/$region/v1/chat/completions"
          (lastBody \ "model").as[String] shouldBe "MiniMax-M3.1-Flash"
          Seq("thinking", "reasoning_effort", "service_tier", "reasoning_split").foreach(key =>
            (lastBody \ key).isDefined shouldBe false
          )
          (lastBody \ "messages")(0).as[JsObject] shouldBe
            Json.obj("role" -> "user", "content" -> "Hello!")
        } finally service.close()
      }
    }

    "send Flash's efforts low..max as they are, none / minimal as low, and no service_tier" in {
      val service = OpenAIChatCompletionServiceFactory(localUrl("global", "/v1/"))
      try {
        def effortSent(effort: ReasoningEffort): String = {
          await(service.createChatCompletion(messages, flash.withReasoningEffort(effort)))
          (lastBody \ "reasoning_effort").as[String]
        }

        Seq(
          ReasoningEffort.low,
          ReasoningEffort.medium,
          ReasoningEffort.high,
          ReasoningEffort.xhigh,
          ReasoningEffort.max
        ).foreach(effort => effortSent(effort) shouldBe effort.toString)
        effortSent(ReasoningEffort.none) shouldBe "low"
        effortSent(ReasoningEffort.minimal) shouldBe "low"

        await(
          service.createChatCompletion(
            messages,
            flash.copy(service_tier = Some(ServiceTier.priority))
          )
        )
        (lastBody \ "service_tier").isDefined shouldBe false
      } finally service.close()
    }

    "keep M3, M2.7 and custom ids, and M3's thinking switch from extra_params" in {
      val service = OpenAIChatCompletionServiceFactory(localUrl("china", "/v1/"))
      try {
        Seq(
          NonOpenAIModelId.minimax_m3,
          NonOpenAIModelId.minimax_m2_7,
          "saved-custom-model"
        ).foreach { model =>
          await(service.createChatCompletion(messages, CreateChatCompletionSettings(model)))
          (lastBody \ "model").as[String] shouldBe model
        }

        Seq("adaptive", "disabled").foreach { mode =>
          val settings = CreateChatCompletionSettings(
            NonOpenAIModelId.minimax_m3,
            extra_params = Map("thinking" -> Map("type" -> mode))
          )
          await(service.createChatCompletion(messages, settings))
          (lastBody \ "thinking" \ "type").as[String] shouldBe mode
        }
      } finally service.close()
    }

    "keep image content and the model on the streaming transport" in {
      val service =
        OpenAIChatCompletionServiceFactory.withStreaming(localUrl("global", "/v1/"))
      try {
        val media = Seq(
          UserSeqMessage(
            Seq(TextContent("Describe"), ImageURLContent("https://example.com/image.png"))
          )
        )
        await(service.createChatCompletionStreamed(media, flash).runWith(Sink.seq))

        (lastBody \ "stream").as[Boolean] shouldBe true
        (lastBody \ "model").as[String] shouldBe "MiniMax-M3.1-Flash"
        (lastBody \ "messages")(0).as[JsObject].value("content") shouldBe Json.arr(
          Json.obj("type" -> "text", "text" -> "Describe"),
          Json.obj(
            "type" -> "image_url",
            "image_url" -> Json.obj("url" -> "https://example.com/image.png")
          )
        )
      } finally service.close()
    }
  }
}
