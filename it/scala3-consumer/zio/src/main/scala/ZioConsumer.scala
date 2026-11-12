package consumer

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{OpenAIServiceFactory, OpenAIStreamedServiceZIO}
import io.cequence.wsclient.stream.StreamDiagnostics
import zio.{Console, Runtime, Task, Unsafe, ZIO}

import scala.concurrent.ExecutionContext.Implicits.global

// docs/migration-2.0.md, "ZIO" - the "after" code (compiled, not run: it calls OpenAI)
object ZioSnippets {

  val program: Task[Unit] =
    ZIO
      .serviceWithZIO[OpenAIStreamedServiceZIO](
        _.createChatCompletionStreamedTyped(
          Seq(UserMessage("Name three Norwegian fjords.")),
          CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
        ).runForeach(chunk => Console.printLine(chunk))
      )
      .provide(OpenAIStreamedServiceZIO.layer(() => OpenAIServiceFactory.withStreaming()))
}

object ZioRoundTrip {

  def main(args: Array[String]): Unit = {
    val server = LocalChatServer.start()
    val service = OpenAIStreamedServiceZIO(
      OpenAIServiceFactory.withStreaming.customInstance(server.coreUrl, server.requestContext)
    )

    try {
      val (stream, bridges) = StreamDiagnostics.counted(
        service.createChatCompletionStreamed(
          Seq(UserMessage("hi")),
          CreateChatCompletionSettings("local")
        )
      )
      val chunks = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(stream.runCollect).getOrThrowFiberFailure()
      }
      // zio-http has no Scala 3 build: on the jdk engine an HTTP response crosses one bridge
      LocalChatServer.check(
        "zio (Scala 3.3)",
        chunks.flatMap(_.contentHead).mkString,
        bridges.get,
        expectedBridges = 1
      )
    } finally {
      service.asFuture.close()
      server.stop()
    }
  }
}
