package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.ChatCompletionTool.{MCPServerTool, SkillTool}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{JsonSchema, ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Where an OpenAI tool completion goes, on the wire, against a local server: a
 * provider-neutral MCPServerTool / SkillTool (or a GPT-6 function tool) is posted to
 * `/responses` by the full service - plain and streamed - through the Responses-routing hooks,
 * ordinary function tools still go to `/chat/completions`, and a chat-only service fails fast
 * without touching the network.
 */
class ResponsesRoutingWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("responses-routing-wire")
  private implicit val materializer: Materializer = Materializer(system)

  private case class Received(
    path: String,
    body: JsObject
  )

  @volatile private var received: Option[Received] = None

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private lazy val full =
    OpenAIServiceFactory.customEngineInstance(engine, coreUrl, context)
  private lazy val fullStreamed =
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)
  private lazy val chatOnly =
    OpenAIChatCompletionServiceFactory.withEngine(
      engine,
      coreUrl = coreUrl,
      requestContext = context
    )
  private lazy val chatOnlyStreamed =
    OpenAIChatCompletionServiceFactory.withStreaming.withEngine(engine, coreUrl, context)

  private val responsesBody =
    """{"id":"resp_1","object":"response","created_at":1700000000,"status":"completed","model":"gpt-5.4",
      |"output":[{"type":"message","id":"msg_1","status":"completed","role":"assistant","content":[{"type":"output_text","text":"hi","annotations":[]}]}],
      |"parallel_tool_calls":true,"text":{"format":{"type":"text"}},
      |"usage":{"input_tokens":10,"input_tokens_details":{"cached_tokens":0},"output_tokens":2,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":12}}""".stripMargin

  private val responsesStream =
    "data: {\"type\":\"response.created\",\"sequence_number\":0,\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\",\"model\":\"gpt-5.4\",\"output\":[]}}\n\n" +
      "data: {\"type\":\"response.output_text.delta\",\"sequence_number\":1,\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"hi\"}\n\n" +
      "data: {\"type\":\"response.completed\",\"sequence_number\":2,\"response\":{\"id\":\"resp_1\",\"status\":\"completed\",\"output\":[],\"usage\":{\"input_tokens\":10,\"input_tokens_details\":{\"cached_tokens\":0},\"output_tokens\":2,\"output_tokens_details\":{\"reasoning_tokens\":0},\"total_tokens\":12}}}\n\n"

  private val chatBody =
    """{"id":"chatcmpl_1","object":"chat.completion","created":1700000000,"model":"gpt-5.4",
      |"choices":[{"index":0,"message":{"role":"assistant","content":"hi"},"finish_reason":"stop"}],
      |"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}""".stripMargin

  private val chatStream =
    "data: {\"id\":\"chatcmpl_1\",\"object\":\"chat.completion.chunk\",\"created\":1700000000,\"model\":\"gpt-5.4\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}\n\n" +
      "data: [DONE]\n\n"

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          val body = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          val json = Json.parse(body).as[JsObject]
          val path = exchange.getRequestURI.getPath
          received = Some(Received(path, json))

          val streamed = (json \ "stream").asOpt[Boolean].contains(true)
          val (contentType, response) = (path.endsWith("/responses"), streamed) match {
            case (true, false)  => ("application/json", responsesBody)
            case (true, true)   => ("text/event-stream", responsesStream)
            case (false, false) => ("application/json", chatBody)
            case (false, true)  => ("text/event-stream", chatStream)
          }
          exchange.getResponseHeaders.add("Content-Type", contentType)
          val bytes = response.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    full.close()
    fullStreamed.close()
    chatOnly.close()
    chatOnlyStreamed.close()
    engine.close()
    server.stop(0)
    system.terminate()
    ()
  }

  private def await[T](f: Future[T]): T = Await.result(f, 20.seconds)

  private val weather = FunctionTool(
    "get_weather",
    parameters = JsonSchema.Object(properties = Seq("location" -> JsonSchema.String()))
  )
  private val deepwiki = MCPServerTool("deepwiki", "https://mcp.deepwiki.com/mcp")
  private val messages = Seq(UserMessage("hi"))
  private val gpt54 = CreateChatCompletionSettings(ModelId.gpt_5_4)

  "the full service (plain)" should {

    "post an MCPServerTool request to /responses as an mcp tool" in {
      received = None
      val out =
        await(full.createChatToolCompletion(messages, Seq(deepwiki, weather), None, gpt54))

      received.get.path should endWith("/responses")
      val tools = (received.get.body \ "tools").as[Seq[JsObject]]
      tools.map(t => (t \ "type").as[String]) shouldBe Seq("function", "mcp")
      (tools(1) \ "server_label").as[String] shouldBe "deepwiki"
      out.choices.head.message.content shouldBe Some("hi")
    }

    "post a SkillTool request to /responses as a hosted shell tool" in {
      received = None
      await(full.createChatToolCompletion(messages, Seq(SkillTool("sk_1")), None, gpt54))

      received.get.path should endWith("/responses")
      val tools = (received.get.body \ "tools").as[Seq[JsObject]]
      tools.map(t => (t \ "type").as[String]) shouldBe Seq("shell")
      (tools.head \ "environment" \ "skills" \ 0 \ "skill_id").as[String] shouldBe "sk_1"
    }

    "keep plain function tools on /chat/completions" in {
      received = None
      await(full.createChatToolCompletion(messages, Seq(weather), None, gpt54))

      received.get.path should endWith("/chat/completions")
    }
  }

  "the full streamed service (typed stream)" should {

    "stream an MCPServerTool request from /responses" in {
      received = None
      val chunks = await(
        fullStreamed
          .createChatToolCompletionStreamed(messages, Seq(deepwiki), None, gpt54)
          .runWith(Sink.seq)
      )

      received.get.path should endWith("/responses")
      (received.get.body \ "stream").as[Boolean] shouldBe true
      chunks.collect { case ChatChunk.Text(t) => t } shouldBe Seq("hi")
      chunks.collect { case f: ChatChunk.Finish => f.providerReason } shouldBe Seq(
        Some("completed")
      )
    }

    "keep plain function tools on the /chat/completions stream" in {
      received = None
      val chunks = await(
        fullStreamed
          .createChatToolCompletionStreamed(messages, Seq(weather), None, gpt54)
          .runWith(Sink.seq)
      )

      received.get.path should endWith("/chat/completions")
      chunks.collect { case ChatChunk.Text(t) => t } shouldBe Seq("hi")
    }
  }

  "a chat-only service" should {

    "fail fast on a neutral tool, plain and streamed, without calling the API" in {
      received = None
      val plain =
        await(chatOnly.createChatToolCompletion(messages, Seq(deepwiki), None, gpt54).failed)
      plain shouldBe an[OpenAIScalaClientException]
      plain.getMessage should include("MCPServerTool(deepwiki)")

      val streamed = await(
        chatOnlyStreamed
          .createChatToolCompletionStreamed(messages, Seq(SkillTool("sk_1")), None, gpt54)
          .runWith(Sink.seq)
          .failed
      )
      streamed shouldBe an[OpenAIScalaClientException]
      streamed.getMessage should include("SkillTool(sk_1)")

      received shouldBe None
    }
  }
}
