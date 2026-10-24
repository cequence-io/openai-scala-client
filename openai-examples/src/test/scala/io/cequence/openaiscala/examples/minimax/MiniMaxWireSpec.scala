package io.cequence.openaiscala.examples.minimax

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.anthropic.domain.{Message => AnthropicMessage}
import io.cequence.openaiscala.anthropic.domain.settings._
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import java.net.{InetSocketAddress, URI}
import java.nio.charset.StandardCharsets
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Real SDK serialization and HTTP transport, with synthetic responses and no API keys. */
class MiniMaxWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("minimax-wire")
  private implicit val materializer: Materializer = Materializer(system)
  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  @volatile private var lastBody: JsObject = Json.obj()
  @volatile private var lastPath = ""

  private val chatResponse =
    """{"id":"chat-1","object":"chat.completion","created":1700000000,"model":"MiniMax-M3.1-Flash","choices":[{"index":0,"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
  private val messageResponse =
    """{"id":"msg-1","type":"message","role":"assistant","model":"MiniMax-M3.1-Flash","content":[{"type":"text","text":"Hi"}],"stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}"""
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
          val response =
            if (streamed) chatStream
            else if (lastPath.endsWith("/messages")) messageResponse
            else chatResponse
          val bytes = response.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add(
            "Content-Type",
            if (streamed) "text/event-stream" else "application/json"
          )
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
  private def localUrl(
    region: String,
    path: String
  ): String =
    s"http://localhost:${server.getAddress.getPort}/$region$path"
  private val regions = Seq(
    "global" -> ChatProviderSettings.minimax,
    "china" -> ChatProviderSettings.minimaxChina
  )
  private val messages = Seq(UserMessage("Hello!"))

  "MiniMax direct chat requests" should {
    "send the example's Flash default through both regional paths without incompatible defaults" in {
      regions.foreach { case (region, provider) =>
        val endpoint = new URI(provider.coreUrl)
        endpoint.getHost shouldBe (if (region == "global") "api.minimax.io"
                                   else "api.minimaxi.com")
        endpoint.getPath shouldBe "/v1/"
        val service = OpenAIChatCompletionServiceFactory(localUrl(region, endpoint.getPath))
        try {
          await(
            service.createChatCompletion(
              messages,
              CreateMiniMaxChatCompletion.settings(Array.empty)
            )
          )
          lastPath shouldBe s"/$region/v1/chat/completions"
          (lastBody \ "model").as[String] shouldBe "MiniMax-M3.1-Flash"
          Seq("thinking", "reasoning_effort", "service_tier", "reasoning_split").foreach {
            key =>
              (lastBody \ key).isDefined shouldBe false
          }
          (lastBody \ "messages")(0)
            .as[JsObject] shouldBe Json.obj("role" -> "user", "content" -> "Hello!")
        } finally service.close()
      }
    }

    "preserve every supported Flash effort, explicit M3 thinking, and saved/custom model IDs" in {
      regions.foreach { case (region, _) =>
        val service = OpenAIChatCompletionServiceFactory(localUrl(region, "/v1/"))
        try {
          Seq(
            ReasoningEffort.low,
            ReasoningEffort.medium,
            ReasoningEffort.high,
            ReasoningEffort.xhigh,
            ReasoningEffort.max
          ).foreach { effort =>
            val settings =
              CreateMiniMaxChatCompletion.settings(Array.empty).withReasoningEffort(effort)
            await(service.createChatCompletion(messages, settings))
            (lastBody \ "reasoning_effort").as[String] shouldBe effort.toString
          }
          Seq(
            NonOpenAIModelId.minimax_m3 -> "MiniMax-M3",
            NonOpenAIModelId.minimax_m2_7 -> "MiniMax-M2.7",
            "saved-custom-model" -> "saved-custom-model"
          ).foreach { case (model, expectedModel) =>
            await(
              service.createChatCompletion(
                messages,
                CreateMiniMaxChatCompletion.settings(Array(model))
              )
            )
            (lastBody \ "model").as[String] shouldBe expectedModel
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
    }

    "keep image content and the selected model on the streaming transport" in {
      val service =
        OpenAIChatCompletionServiceFactory.withStreaming(localUrl("global", "/v1/"))
      try {
        val media = Seq(
          UserSeqMessage(
            Seq(TextContent("Describe"), ImageURLContent("https://example.com/image.png"))
          )
        )
        await(
          service
            .createChatCompletionStreamed(
              media,
              CreateMiniMaxChatCompletion.settings(Array.empty)
            )
            .runWith(Sink.seq)
        )
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

  "MiniMax native Messages requests" should {
    "preserve Flash defaults and effort plus M3 thinking on both regional paths" in {
      regions.foreach { case (region, _) =>
        val service =
          AnthropicServiceFactory.customInstance(localUrl(region, "/anthropic/v1/"))
        val messages = Seq(AnthropicMessage.UserMessage("Hello!"))
        val settings = AnthropicCreateMessageSettings(
          NonOpenAIModelId.minimax_m3_1_flash,
          max_tokens = 4096
        )
        try {
          await(service.createMessage(messages, settings))
          lastPath shouldBe s"/$region/anthropic/v1/messages"
          (lastBody \ "model").as[String] shouldBe "MiniMax-M3.1-Flash"
          Seq("thinking", "output_config", "service_tier").foreach { key =>
            (lastBody \ key).isDefined shouldBe false
          }
          OutputEffort.values.foreach { effort =>
            await(
              service.createMessage(
                messages,
                settings.copy(
                  thinking = Some(ThinkingSettings.adaptive),
                  output_config = Some(OutputConfig(effort = Some(effort)))
                )
              )
            )
            (lastBody \ "thinking" \ "type").as[String] shouldBe "adaptive"
            (lastBody \ "output_config" \ "effort").as[String] shouldBe effort.toString
            (lastBody \ "thinking" \ "budget_tokens").isDefined shouldBe false
          }
          Seq(NonOpenAIModelId.minimax_m3, "saved-custom-model").foreach { model =>
            await(service.createMessage(messages, settings.copy(model = model)))
            (lastBody \ "model").as[String] shouldBe model
            (lastBody \ "thinking").isDefined shouldBe false
          }
          Seq(ThinkingType.adaptive, ThinkingType.disabled).foreach { mode =>
            await(
              service.createMessage(
                messages,
                settings.copy(
                  model = NonOpenAIModelId.minimax_m3,
                  thinking = Some(ThinkingSettings(`type` = mode))
                )
              )
            )
            (lastBody \ "thinking" \ "type").as[String] shouldBe mode.toString
          }
        } finally service.close()
      }
    }
  }
}
