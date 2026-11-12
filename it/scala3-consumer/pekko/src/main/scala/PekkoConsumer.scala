package consumer

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.stream.SourceInterop._ // from ws-client-core-pekko: the same names, Pekko types
import io.cequence.wsclient.stream.StreamDiagnostics
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

// docs/migration-2.0.md, "Pekko" (compiled, not run: it calls OpenAI)
object PekkoSnippets {

  def after(): Future[Any] = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher

    val service = OpenAIServiceFactory.withStreaming() // on play-pekko-stream (priority 21)

    service
      .createChatCompletionStreamedTyped(
        Seq(UserMessage("Name three Norwegian fjords.")),
        CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
      )
      .texts
      .toSource // the engine's own Pekko Source - no bridge
      .runWith(Sink.foreach[String](print))
  }
}

object PekkoRoundTrip {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("consumer")
    implicit val ec: ExecutionContext = system.dispatcher
    val server = LocalChatServer.start()
    val service =
      OpenAIServiceFactory.withStreaming.customInstance(server.coreUrl, server.requestContext)

    try {
      val (source, bridges) = StreamDiagnostics.counted(
        service
          .createChatCompletionStreamed(Seq(UserMessage("hi")), CreateChatCompletionSettings("local"))
          .toSource
      )
      val text = Await.result(source.runWith(Sink.seq), 30.seconds).flatMap(_.contentHead).mkString
      // play-pekko-stream (priority 21) beats jdk: the engine's own Source
      LocalChatServer.check("pekko (Scala 3.3)", text, bridges.get, expectedBridges = 0)
    } finally {
      service.close()
      server.stop()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
