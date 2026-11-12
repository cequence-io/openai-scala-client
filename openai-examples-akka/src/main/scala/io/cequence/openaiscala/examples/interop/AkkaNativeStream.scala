package io.cequence.openaiscala.examples.interop

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import io.cequence.wsclient.stream.SourceInterop._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * Akka in, Akka out: the Play WS stream engine runs on the application's own `ActorSystem`,
 * and `.toSource` hands a service stream back as that engine's own `Source` - the library's
 * operators run as Akka stages, with no bridge through `Flow.Publisher`.
 *
 * Needs `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object AkkaNativeStream {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("akka-native-stream")
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    // the engine runs on YOUR materializer: closing it never touches the actor system
    val engine = PlayWSStreamClientEngine()
    val service = OpenAIServiceFactory.withStreaming.withEngine(engine)

    val done = service
      .createChatCompletionStreamed(
        messages = Seq(
          SystemMessage("You are a helpful assistant."),
          UserMessage("Name three Norwegian fjords.")
        ),
        settings = CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
      )
      .toSource
      .map(_.contentHead.getOrElse(""))
      .runWith(Sink.foreach(print))

    try {
      Await.result(done, 2.minutes)
      println()
    } finally {
      service.close()
      engine.close()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
