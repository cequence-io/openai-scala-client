package io.cequence.openaiscala.examples.interop

import cats.effect.{IO, IOApp, Resource}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.stream.Fs2Interop._

import scala.concurrent.ExecutionContext

/**
 * A service stream as an `fs2.Stream[IO, _]` on the default jdk engine. The stream is cold:
 * every run of it is a new request.
 *
 * Needs `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object Fs2Stream extends IOApp.Simple {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val service =
    Resource.make(IO(OpenAIServiceFactory.withStreaming()))(service => IO(service.close()))

  override def run: IO[Unit] =
    service.use { service =>
      service
        .createChatCompletionStreamed(
          messages = Seq(
            SystemMessage("You are a helpful assistant."),
            UserMessage("Name three Norwegian fjords.")
          ),
          settings = CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
        )
        .toFs2[IO]
        .map(_.contentHead.getOrElse(""))
        .foreach(text => IO(print(text)))
        .compile
        .drain >> IO.println("")
    }
}
