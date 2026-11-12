package consumer

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{BaseMessage, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIStreamedService
import io.cequence.wsclient.stream.Fs2Interop._
import io.cequence.wsclient.stream.StreamDiagnostics

import scala.concurrent.ExecutionContext

// docs/migration-2.0.md, "fs2" (compiled, not run: it calls OpenAI)
object Fs2Snippets {

  def after(
    service: OpenAIStreamedService,
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  )(
    implicit ec: ExecutionContext
  ): fs2.Stream[IO, String] =
    service
      .createChatCompletionStreamedTyped(messages, settings)
      .texts
      .toFs2[IO] // each run of the fs2 stream is one request; interrupting it aborts the request

  def chunks(stream: io.cequence.wsclient.stream.AsyncStream[ChatChunk]): fs2.Stream[IO, ChatChunk] =
    stream.toFs2[IO]
}

object Fs2RoundTrip {

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val server = LocalChatServer.start()
    val service =
      OpenAIServiceFactory.withStreaming.customInstance(server.coreUrl, server.requestContext)

    try {
      val (stream, bridges) = StreamDiagnostics.counted(
        service
          .createChatCompletionStreamed(Seq(UserMessage("hi")), CreateChatCompletionSettings("local"))
          .toFs2[IO]
      )
      val text = stream.compile.toList.unsafeRunSync().flatMap(_.contentHead).mkString
      // on the jdk engine an HTTP response crosses one bridge into fs2
      LocalChatServer.check("fs2 (Scala 3.3)", text, bridges.get, expectedBridges = 1)
    } finally {
      service.close()
      server.stop()
    }
  }
}
