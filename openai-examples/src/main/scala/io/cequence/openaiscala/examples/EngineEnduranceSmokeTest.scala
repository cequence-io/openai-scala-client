package io.cequence.openaiscala.examples

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import io.cequence.openaiscala.domain.settings.{
  CreateChatCompletionSettings,
  FileUploadPurpose
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.{ModelId, NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionStreamedServiceExtra,
  OpenAIServiceFactory
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}

import java.io.{BufferedOutputStream, File, FileOutputStream}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Endurance checks of the HTTP engine the classpath selects (`jdk` by default, or the one
 * `-Dws-client.engine` names): a stream longer than five minutes, 50 parallel streams on one
 * engine, a ~100 MB multipart upload (the `jdk` engine builds it in memory - the peak heap is
 * reported).
 *
 * `local` runs them against a local server (free, deterministic - the long stream sends an
 * event every 20 s for 5.5 min, past the default two-minute request timeout); `live` runs the
 * parallel streams and the upload against OpenAI (`OPENAI_SCALA_CLIENT_API_KEY`; a cent - the
 * streams follow one warm-up call, the uploaded file is deleted again) and the long stream
 * against Claude Haiku 5.5 writing a long manual (`ANTHROPIC_API_KEY`; up to 120k output
 * tokens, ~$0.06 - it fails if the model ends before five minutes; on 2026-11-12 it streamed
 * 16,353 chunks over 346 s on the default timeouts). Both by default; `only=` picks checks
 * (`long,parallel,upload`). Exits 0 when every check passed.
 */
object EngineEnduranceSmokeTest {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val LongStreamDuration = 330.seconds
  private val ParallelStreams = 50
  private val UploadBytes = 100L * 1024 * 1024

  private final case class Outcome(
    check: String,
    passed: Boolean,
    detail: String
  )

  def main(args: Array[String]): Unit = {
    val modes = args.filter(a => a == "local" || a == "live").toSet
    val only = args.collectFirst {
      case a if a.startsWith("only=") => a.drop(5).split(',').toSet
    }.getOrElse(Set("long", "parallel", "upload"))
    val runLocal = modes.isEmpty || modes("local")
    val runLive = modes.isEmpty || modes("live")

    val probe = WSClientEngineRegistry.outputStreamed()
    println(s"Engine: ${probe.getClass.getSimpleName}")
    probe.close()

    val outcomes =
      (if (runLocal)
         local(only)
       else
         Nil) ++
        (if (runLive)
           live(only)
         else
           Nil)

    outcomes.foreach { o =>
      println(s"${if (o.passed) "PASS" else "FAIL"}  ${o.check}: ${o.detail}")
    }
    System.exit(
      if (outcomes.nonEmpty && outcomes.forall(_.passed))
        0
      else
        1
    )
  }

  // --------------------------------------------------------------------------------- checks

  // a stream that must outlive five minutes: the local server's "slow" one (an event every 20 s
  // for 5.5 min), or Claude Haiku 5.5 writing a long text (~200 tokens/s)
  private def longStream(
    label: String,
    service: OpenAIChatCompletionStreamedServiceExtra,
    model: String,
    prompt: String,
    maxTokens: Option[Int]
  ): Outcome = {
    val start = System.nanoTime()
    val chunks = new AtomicLong(0)
    val run = service
      .createChatCompletionStreamed(
        Seq(UserMessage(prompt)),
        CreateChatCompletionSettings(model, max_tokens = maxTokens)
      )
      .runForeach(_ => chunks.incrementAndGet(): Unit)

    outcome(s"$label long stream", Try(Await.result(run, LongStreamDuration + 10.minutes))) {
      _ =>
        val took = (System.nanoTime() - start).nanos
        (
          took > 5.minutes,
          s"${chunks.get} chunks over ${took.toSeconds} s"
        )
    }
  }

  private def parallelStreams(
    label: String,
    service: OpenAIStreamedService,
    model: String
  ): Outcome = {
    // one call first: on a fresh client java.net.http opens a connection per concurrent
    // request (it learns that the host speaks HTTP/2 only from the first one), and where new
    // TLS connections are slow, the later ones pass the connect timeout; warm, the 50 streams
    // share one HTTP/2 connection
    Try(
      Await.result(
        service.createChatCompletion(
          Seq(UserMessage("Say OK.")),
          CreateChatCompletionSettings(model, max_tokens = Some(20))
        ),
        1.minute
      )
    )

    val start = System.nanoTime()
    val runs = (1 to ParallelStreams).map { i =>
      service
        .createChatCompletionStreamed(
          Seq(UserMessage(s"Count from 1 to 10, comma-separated. Run $i.")),
          CreateChatCompletionSettings(model, max_tokens = Some(100))
        )
        .runFold(0)(
          (
            count,
            _
          ) => count + 1
        )
    }

    outcome(
      s"$label $ParallelStreams parallel streams",
      Try(Await.result(Future.sequence(runs), 5.minutes))
    ) { counts =>
      val took = (System.nanoTime() - start).nanos
      (
        counts.forall(_ > 0),
        s"all completed in ${took.toMillis} ms, chunks per stream ${counts.min}..${counts.max}"
      )
    }
  }

  private def upload(
    label: String,
    service: OpenAIStreamedService,
    cleanUp: String => Future[_]
  ): Outcome = {
    val file = File.createTempFile("endurance-upload", ".txt")
    file.deleteOnExit()
    writeText(file, UploadBytes)

    val peak = new AtomicLong(0)
    val sampling = new AtomicBoolean(true)
    val sampler = new Thread(() =>
      while (sampling.get) {
        val runtime = Runtime.getRuntime
        peak.accumulateAndGet(
          runtime.totalMemory() - runtime.freeMemory(),
          (
            a: Long,
            b: Long
          ) => math.max(a, b)
        )
        Thread.sleep(20)
      }
    )
    sampler.setDaemon(true)
    System.gc()
    val before = Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory()
    sampler.start()

    val start = System.nanoTime()
    val result = Try(
      Await.result(
        service.uploadFile(file, Some(file.getName), FileUploadPurpose.assistants),
        10.minutes
      )
    )
    val took = (System.nanoTime() - start).nanos
    sampling.set(false)
    file.delete()
    result.foreach(info => Try(Await.result(cleanUp(info.id), 1.minute)))

    // the local server reports the whole multipart body, OpenAI the file
    outcome(s"$label upload of ${UploadBytes / 1024 / 1024} MiB", result) { info =>
      (
        info.bytes >= UploadBytes && info.bytes < UploadBytes + 64 * 1024,
        s"${info.bytes} bytes in ${took.toMillis} ms; heap ${mib(before)} MiB before, peak ${mib(
            peak.get
          )} MiB (max ${mib(Runtime.getRuntime.maxMemory())} MiB)"
      )
    }
  }

  // ----------------------------------------------------------------------------- local / live

  private def local(only: Set[String]): Seq[Outcome] = {
    val server = LocalServer.start()
    val engine = WSClientEngineRegistry.outputStreamed(TransportSettings())
    val service = OpenAIServiceFactory.withStreaming.customEngineInstance(
      engine,
      server.url,
      WsRequestContext(authHeaders = Seq("Authorization" -> "Bearer local"))
    )

    try
      Seq(
        when(only("long"))(
          longStream("local", service, "local-model", "slow", None)
        ),
        when(only("parallel"))(parallelStreams("local", service, "local-model")),
        when(only("upload"))(
          upload("local", service, _ => Future.successful(()))
        )
      ).flatten
    finally {
      service.close()
      engine.close()
      server.stop()
    }
  }

  private def live(only: Set[String]): Seq[Outcome] = {
    val service = OpenAIServiceFactory.withStreaming()
    val model = ModelId.gpt_5_4_mini

    try
      Seq(
        when(only("parallel"))(parallelStreams("live", service, model)),
        when(only("long"))(liveLongStream()),
        when(only("upload"))(upload("live", service, id => service.deleteFile(id)))
      ).flatten
    finally service.close()
  }

  // Claude follows a length instruction; GPT-5.4-mini ended "count to 30,000" and "write 40,000
  // words" within seconds (2026-11-12)
  private def liveLongStream(): Outcome = {
    val claude = AnthropicServiceFactory.asOpenAI()
    try
      longStream(
        "live (Claude Haiku 5.5)",
        claude,
        NonOpenAIModelId.claude_haiku_5_5,
        "Write a complete, very detailed technical manual for a fictional airport baggage-" +
          "handling system - at least 40,000 words: every subsystem, every failure mode, every " +
          "maintenance procedure, in numbered sections. Write the whole manual now, without " +
          "asking questions and without stopping early.",
        Some(120000)
      )
    finally claude.close()
  }

  // ---------------------------------------------------------------------------------- helpers

  private def outcome[A](
    check: String,
    result: Try[A]
  )(
    judge: A => (Boolean, String)
  ): Outcome =
    result match {
      case Success(value) =>
        val (passed, detail) = judge(value)
        Outcome(check, passed, detail)

      case Failure(e) =>
        Outcome(check, passed = false, s"${e.getClass.getSimpleName}: ${e.getMessage}")
    }

  private def writeText(
    file: File,
    bytes: Long
  ): Unit = {
    val line = ("The quick brown fox jumps over the lazy dog. " * 20 + "\n")
      .getBytes(StandardCharsets.UTF_8)
    val out = new BufferedOutputStream(new FileOutputStream(file), 1 << 20)
    try {
      var written = 0L
      while (written < bytes) {
        val n = math.min(line.length.toLong, bytes - written).toInt
        out.write(line, 0, n)
        written += n
      }
    } finally out.close()
  }

  private def mib(bytes: Long): Long = bytes / 1024 / 1024

  private def when[A](
    condition: Boolean
  )(
    check: => A
  ): Option[A] =
    if (condition)
      Some(check)
    else
      None

  // A local OpenAI-shaped server: chat streams (`slow` = an event every 20 s for 5.5 min, else
  // ten quick events) and file uploads (the whole multipart body counted and reported back).
  private final class LocalServer(server: HttpServer) {
    def url: String = s"http://localhost:${server.getAddress.getPort}/v1/"
    def stop(): Unit = server.stop(0)
  }

  private object LocalServer {

    private def chunk(text: String) =
      s"""data: {"id":"c1","object":"chat.completion.chunk","created":1700000000,"model":"local-model","choices":[{"index":0,"delta":{"content":"$text"},"finish_reason":null}]}\n\n"""

    def start(): LocalServer = {
      val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
      server.setExecutor(Executors.newCachedThreadPool())

      server.createContext(
        "/v1/chat/completions",
        new HttpHandler {
          override def handle(exchange: HttpExchange): Unit = {
            val body =
              new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
            val slow = body.contains("\"slow\"")
            exchange.getResponseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            val out = exchange.getResponseBody
            try {
              val (events, pause) =
                if (slow)
                  (math.ceil(LongStreamDuration / 20.seconds).toInt + 1, 20.seconds)
                else
                  (10, 20.millis)
              (1 to events).foreach { i =>
                out.write(chunk(s"$i ").getBytes(StandardCharsets.UTF_8))
                out.flush()
                if (i < events)
                  Thread.sleep(pause.toMillis)
              }
              out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8))
              out.flush()
            } finally exchange.close()
          }
        }
      )

      server.createContext(
        "/v1/files",
        new HttpHandler {
          override def handle(exchange: HttpExchange): Unit = {
            val in = exchange.getRequestBody
            val buffer = new Array[Byte](1 << 16)
            var total = 0L
            var read = in.read(buffer)
            while (read != -1) {
              total += read
              read = in.read(buffer)
            }
            val json =
              s"""{"id":"file-local","object":"file","bytes":$total,"created_at":1700000000,"filename":"upload.txt","purpose":"assistants","status":"processed"}"""
            val bytes = json.getBytes(StandardCharsets.UTF_8)
            exchange.getResponseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.length.toLong)
            exchange.getResponseBody.write(bytes)
            exchange.close()
          }
        }
      )

      server.start()
      new LocalServer(server)
    }
  }
}
