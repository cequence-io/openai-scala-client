package io.cequence.openaiscala.anthropic.service.impl

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.settings._
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.NonOpenAIModelId
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * MiniMax's Anthropic-compatible Messages API, reached with
 * `AnthropicServiceFactory.customInstance` (docs/providers.md), on the wire against a local
 * server, no API key (contributed in #131): nothing is added by default, MiniMax-M3.1-Flash
 * takes adaptive thinking with an `output_config` effort (`max` when omitted, per MiniMax's
 * docs) and MiniMax-M3 a thinking switch (off by default on this protocol).
 */
class MiniMaxMessagesWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  @volatile private var lastBody: JsObject = Json.obj()
  @volatile private var lastPath = ""

  private val messageResponse =
    """{"id":"msg-1","type":"message","role":"assistant","model":"MiniMax-M3.1-Flash","content":[{"type":"text","text":"Hi"}],"stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}"""

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          lastBody = Json.parse(exchange.getRequestBody.readAllBytes()).as[JsObject]
          lastPath = exchange.getRequestURI.getPath

          val bytes = messageResponse.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "application/json")
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit =
    server.stop(0)

  private def await[A](future: Future[A]): A = Await.result(future, 20.seconds)

  // the provider's path under a per-region prefix on the local server
  private def localUrl(region: String): String =
    s"http://localhost:${server.getAddress.getPort}/$region/anthropic/v1/"

  "MiniMax's Messages API" should {

    "carry Flash's effort and M3's thinking switch, adding nothing by default, on both regions" in {
      Seq("global", "china").foreach { region =>
        val service = AnthropicServiceFactory.customInstance(localUrl(region))
        val messages = Seq(UserMessage("Hello!"))
        val settings = AnthropicCreateMessageSettings(
          NonOpenAIModelId.minimax_m3_1_flash,
          max_tokens = 4096
        )

        try {
          await(service.createMessage(messages, settings))
          lastPath shouldBe s"/$region/anthropic/v1/messages"
          (lastBody \ "model").as[String] shouldBe "MiniMax-M3.1-Flash"
          Seq("thinking", "output_config", "service_tier").foreach(key =>
            (lastBody \ key).isDefined shouldBe false
          )

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
            (lastBody \ "thinking" \ "budget_tokens").isDefined shouldBe false
            (lastBody \ "output_config" \ "effort").as[String] shouldBe effort.toString
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
