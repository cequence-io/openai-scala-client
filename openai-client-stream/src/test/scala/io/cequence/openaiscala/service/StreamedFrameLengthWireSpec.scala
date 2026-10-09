package io.cequence.openaiscala.service

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.responsesapi.{Inputs, ResponseStreamEvent}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.domain.{CequenceWSException, SiteBinding, WsRequestContext}
import io.cequence.wsclient.service.spi.{StreamedEngineRegistry, TransportSettings}
import io.cequence.wsclient.service.{WSClientEngine, WSClientOutputStreamExtraAkka}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.JsValue

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Large streamed frames against a local server: every stream is framed with the shared cap
 * (`StreamingConsts.maxFrameLength`, 64 MiB unless configured) - 1.4.0 failed any frame over 1
 * MiB, e.g. an image in base64 - and a frame over the cap fails the stream with an exception
 * naming the setting.
 */
class StreamedFrameLengthWireSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("streamed-frame-length-wire")
  private implicit val materializer: Materializer = Materializer(system)

  @volatile private var sse: String = ""

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private val engine = StreamedEngineRegistry.outputStreamed(TransportSettings())
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private lazy val fullStreamed =
    OpenAIServiceFactory.withStreaming.customEngineInstance(engine, coreUrl, context)

  // a streamed service on the shared base, to drive its framing with a cap of its own
  private class FramedStreamService(
    url: String
  )(
    override implicit val ec: ExecutionContext
  ) extends ClassifiedStreamingWSClient {
    override protected type PEP = String
    override protected type PT = String

    override protected val engine: WSClientEngine with WSClientOutputStreamExtraAkka =
      StreamedFrameLengthWireSpec.this.engine

    override protected def ownsEngine: Boolean = false

    override protected val site: SiteBinding = SiteBinding(url, WsRequestContext())

    def stream(maxFrameLength: Option[Int]): Source[JsValue, NotUsed] =
      execJsonStream("stream", "POST", maxFrameLength = maxFrameLength)
  }

  private lazy val framed = new FramedStreamService(coreUrl)(ec)

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val bytes = sse.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.add("Content-Type", "text/event-stream")
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          exchange.getResponseBody.write(bytes)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    fullStreamed.close()
    framed.close()
    engine.close()
    server.stop(0)
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def run[T](stream: Source[T, _]): Seq[T] =
    Await.result(stream.runWith(Sink.seq), 30.seconds)

  private val MiB = 1024 * 1024

  private def chatChunk(content: String) =
    s"""{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"$content"},"finish_reason":null}]}"""

  "A streamed chat completion" should {

    "read a 3 MiB chunk whole, OpenAI-shaped and typed" in {
      val content = "a" * (3 * MiB)
      sse = s"data: ${chatChunk(content)}\n\ndata: [DONE]\n\n"

      val messages = Seq(UserMessage("hi"))
      val settings = CreateChatCompletionSettings("gpt-5.4")

      run(fullStreamed.createChatCompletionStreamed(messages, settings))
        .flatMap(_.choices.flatMap(_.delta.content))
        .map(_.length) shouldBe Seq(content.length)

      run(fullStreamed.createChatCompletionStreamedTyped(messages, settings)).collect {
        case ChatChunk.Text(text) => text.length
      }.sum shouldBe content.length
    }
  }

  "A streamed Responses API call" should {

    "read a partial image of 5 MiB in base64" in {
      val image = "A" * (5 * MiB)
      sse =
        s"""data: {"type":"response.image_generation_call.partial_image","item_id":"ig_1","output_index":0,"partial_image_index":0,"partial_image_b64":"$image","sequence_number":1}\n\n"""

      run(fullStreamed.createModelResponseStreamed(Inputs.Text("draw a cat"))).collect {
        case e: ResponseStreamEvent.ImageGenerationPartialImage => e.partialImageB64.length
      } shouldBe Seq(image.length)
    }
  }

  "A frame over the cap" should {

    "fail the stream with an exception naming the setting" in {
      sse = s"data: ${chatChunk("a" * (100 * 1024))}\n\n"

      val failure = intercept[OpenAIScalaClientException](
        run(framed.stream(maxFrameLength = Some(64 * 1024)))
      )

      failure.getMessage should include("exceeds the limit of 64 KiB")
      failure.getMessage should include(StreamingConsts.MaxFrameLengthConfigKey)
      failure.getMessage should include(StreamingConsts.MaxFrameLengthEnvVariable)
      // ws-client's "Stream framing problem occurred" failure
      failure.getCause shouldBe a[CequenceWSException]
    }

    "be the shared one when a call passes none" in {
      sse = s"data: ${chatChunk("a" * (2 * MiB))}\n\n"

      run(framed.stream(maxFrameLength = None)) should have size 1
    }
  }
}
