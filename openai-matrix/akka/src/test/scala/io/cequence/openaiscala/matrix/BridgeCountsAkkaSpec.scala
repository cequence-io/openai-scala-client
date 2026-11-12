package io.cequence.openaiscala.matrix

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import com.typesafe.config.ConfigFactory
import io.cequence.openaiscala.claudeagent.domain.{ClaudeAgentEvent, ClaudeAgentSettings}
import io.cequence.openaiscala.claudeagent.service.ClaudeAgentServiceFactory
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.response.{
  ChatChunk,
  ChatCompletionChunkResponse,
  ChunkMessageSpec
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionStreamedOutputConversionAdapter,
  OpenAIServiceFactory
}
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.stream.SourceInterop._
import io.cequence.wsclient.stream.{AsyncStream, Bytes, StreamDiagnostics}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Promise}

/**
 * Bridges (hops through `Flow.Publisher`, counted by `StreamDiagnostics.counted`) of an Akka
 * consumer (`.toSource`) per engine, each engine picked by its id (whatever
 * `-Dws-client.engine` says):
 *   - play-akka-stream: none - a chat stream, the typed stream, a download (its preflight
 *     included) and a native Akka `Flow` given to the output conversion adapter all stay Akka
 *   - jdk: one per HTTP response (the engine's publisher)
 *   - the Claude agent's events: one - the session's broadcast hub, a run of the neutral
 *     interpreter
 */
class BridgeCountsAkkaSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val timeout = 30.seconds

  // daemonic: the forked test JVM exits even if a run is left hanging
  private val system = ActorSystem(
    "bridge-counts-akka",
    ConfigFactory.parseString("akka.daemonic = on").withFallback(ConfigFactory.load())
  )
  private implicit val materializer: Materializer = Materializer(system)

  private val chatRequests = new AtomicInteger(0)

  // every byte value - in a body no text decoding may touch
  private val allBytes: Array[Byte] = (0 until 256).map(_.toByte).toArray

  private val chatChunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}"""
  private val finishChunk =
    """{"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-5.4","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""

  private val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
  private lazy val coreUrl = s"http://localhost:${server.getAddress.getPort}/"
  private val context = WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer k"))

  private val akkaEngine =
    WSClientEngineRegistry.outputStreamed(TransportSettings(), Some("play-akka-stream"))
  private val jdkEngine =
    WSClientEngineRegistry.outputStreamed(TransportSettings(), Some("jdk"))

  private lazy val akkaService =
    OpenAIServiceFactory.withStreaming.customEngineInstance(akkaEngine, coreUrl, context)
  private lazy val jdkService =
    OpenAIServiceFactory.withStreaming.customEngineInstance(jdkEngine, coreUrl, context)

  // a stand-in for the `claude` CLI: the init handshake, then a result for every user turn
  private val fakeCliScript =
    """#!/bin/sh
      |printf '%s\n' '{"type":"system","subtype":"init","apiKeySource":"none","model":"fake-model","tools":[],"permissionMode":"default","session_id":"session-1","cwd":"/tmp","claude_code_version":"test"}'
      |while IFS= read -r line; do
      |  case "$line" in
      |    *'"type":"user"'*)
      |      printf '%s\n' '{"type":"result","subtype":"success","result":"ok","total_cost_usd":0.0,"num_turns":1,"duration_ms":1,"is_error":false,"session_id":"session-1"}'
      |      ;;
      |  esac
      |done
      |""".stripMargin

  private lazy val fakeCli: Path = {
    val file = Files.createTempFile("fake-claude-cli", ".sh")
    Files.write(file, fakeCliScript.getBytes(StandardCharsets.UTF_8))
    file.toFile.setExecutable(true)
    file
  }

  override protected def beforeAll(): Unit = {
    server.createContext(
      "/",
      new HttpHandler {
        override def handle(exchange: HttpExchange): Unit = {
          exchange.getRequestBody.readAllBytes()
          val path = exchange.getRequestURI.getPath

          val (status, contentType, body) =
            if (path.endsWith("/chat/completions")) {
              chatRequests.incrementAndGet()
              (
                200,
                "text/event-stream",
                s"data: $chatChunk\n\ndata: $finishChunk\n\ndata: [DONE]\n\n"
                  .getBytes(StandardCharsets.UTF_8)
              )
            } else if (path.endsWith("/files/file-1/content"))
              (200, "application/octet-stream", allBytes)
            else
              (404, "application/json", """{"error":{"message":"Not found"}}""".getBytes)

          exchange.getResponseHeaders.add("Content-Type", contentType)
          exchange.sendResponseHeaders(status, body.length.toLong)
          exchange.getResponseBody.write(body)
          exchange.close()
        }
      }
    )
    server.start()
  }

  override protected def afterAll(): Unit = {
    akkaService.close()
    jdkService.close()
    akkaEngine.close()
    jdkEngine.close()
    server.stop(0)
    Files.deleteIfExists(fakeCli)
    Await.result(system.terminate(), timeout)
    ()
  }

  private val messages = Seq(UserMessage("hi"))
  private val settings = CreateChatCompletionSettings("gpt-5.4")

  // compiled into an Akka Source inside the counter's scope, run, counted once the run is over
  private def runCounted[A](stream: AsyncStream[A]): (immutable.Seq[A], Long) = {
    val (source, bridges) = StreamDiagnostics.counted(stream.toSource)
    val elements = Await.result(source.runWith(Sink.seq), timeout)
    (elements, bridges.get)
  }

  private def contents(chunks: Seq[ChatCompletionChunkResponse]): Seq[String] =
    chunks.flatMap(_.choices.flatMap(_.delta.content))

  private def texts(chunks: Seq[ChatChunk]): Seq[String] =
    chunks.collect { case ChatChunk.Text(text) => text }

  "the play-akka-stream engine with an Akka consumer" should {

    "stream a chat completion with no bridge" in {
      val (chunks, bridges) =
        runCounted(akkaService.createChatCompletionStreamed(messages, settings))

      contents(chunks) shouldBe Seq("Hello")
      bridges shouldBe 0
    }

    "stream the typed chunks with no bridge" in {
      val (chunks, bridges) =
        runCounted(akkaService.createChatCompletionStreamedTyped(messages, settings))

      texts(chunks) shouldBe Seq("Hello")
      bridges shouldBe 0
    }

    "download every byte value with no bridge, its preflight included" in {
      // the download is preflighted when it is called (a 404 is None before any run), natively
      // on the engine's materializer
      val (download, preflightBridges) =
        StreamDiagnostics.counted(akkaService.retrieveFileContentAsSource("file-1"))
      val body = Await.result(download, timeout).getOrElse(fail("The file was not found"))

      val (chunks, bridges) = runCounted(body)

      chunks.foldLeft(Bytes.empty)(_ ++ _).toArray.toSeq shouldBe allBytes.toSeq
      preflightBridges.get shouldBe 0
      bridges shouldBe 0
    }

    "apply native Akka Flows given to the output conversion adapter with no bridge" in {
      val upperDeltas = Flow[Seq[ChunkMessageSpec]].map(
        _.map(delta => delta.copy(content = delta.content.map(_.toUpperCase)))
      )
      val upperTexts = Flow[ChatChunk].map {
        case ChatChunk.Text(text) => ChatChunk.Text(text.toUpperCase)
        case other                => other
      }
      // not closed here: it closes the service it wraps, which afterAll closes
      val adapted = OpenAIChatCompletionStreamedOutputConversionAdapter(
        akkaService,
        messageConversion = upperDeltas.toAsyncPipe,
        chunkConversion = upperTexts.toAsyncPipe
      )

      val (chunks, chunkBridges) =
        runCounted(adapted.createChatCompletionStreamed(messages, settings))
      contents(chunks) shouldBe Seq("HELLO")
      chunkBridges shouldBe 0

      val (typed, typedBridges) =
        runCounted(adapted.createChatCompletionStreamedTyped(messages, settings))
      texts(typed) shouldBe Seq("HELLO")
      typedBridges shouldBe 0
    }
  }

  "the jdk engine with an Akka consumer" should {

    "cross one bridge per HTTP response" in {
      val stream = jdkService.createChatCompletionStreamed(messages, settings)

      val (chunks, bridges) = runCounted(stream)
      contents(chunks) shouldBe Seq("Hello")
      bridges shouldBe 1

      chatRequests.set(0)
      val (twice, twiceBridges) = runCounted(stream.concat(stream))
      contents(twice) shouldBe Seq("Hello", "Hello")
      chatRequests.get shouldBe 2
      twiceBridges shouldBe 2
    }
  }

  "the Claude agent's events with an Akka consumer" should {

    "cross exactly one bridge - the session's broadcast hub" in {
      val agent = ClaudeAgentServiceFactory.startSession(
        ClaudeAgentSettings(executablePath = Some(fakeCli.toString))
      )

      def turnEnded(event: ClaudeAgentEvent): Boolean =
        event match {
          case _: ClaudeAgentEvent.ResultSuccess => true
          case _: ClaudeAgentEvent.ResultError   => true
          case _                                 => false
        }

      try {
        // the stream is hot: the turn is sent only once the run is subscribed, which its first
        // element (the replayed init) shows
        val subscribed = Promise[Unit]()
        val (source, bridges) = StreamDiagnostics.counted(agent.events.toSource)
        val run = source.map { event =>
          subscribed.trySuccess(())
          event
        }.takeWhile(event => !turnEnded(event), inclusive = true).runWith(Sink.seq)

        Await.result(subscribed.future, timeout)
        Await.result(agent.send("hello"), timeout)
        val events = Await.result(run, timeout)

        events.headOption.collect { case init: ClaudeAgentEvent.SystemInit =>
          init.model
        } shouldBe
          Some("fake-model")
        events.lastOption.collect { case result: ClaudeAgentEvent.ResultSuccess =>
          result.result
        } shouldBe Some("ok")
        bridges.get shouldBe 1
      } finally agent.close()
    }
  }
}
