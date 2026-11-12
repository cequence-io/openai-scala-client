package consumer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Flow, Sink, Source}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, UserMessage}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionIOConversionAdapter,
  OpenAIServiceFactory
}
import io.cequence.wsclient.stream.{AsyncStream, StreamDiagnostics}

import java.nio.file.Paths
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

// docs/migration-2.0.md, "Akka" - the "after" code (compiled, not run: it calls OpenAI)
object AkkaSnippets {
  import io.cequence.wsclient.stream.SourceInterop._

  def after(): Future[Any] = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher

    val service = OpenAIServiceFactory.withStreaming() // on play-akka-stream, found on the classpath

    service
      .createChatCompletionStreamed(
        Seq(UserMessage("Name three Norwegian fjords.")),
        CreateChatCompletionSettings(ModelId.gpt_5_4_mini)
      )
      .toSource // the engine's own Source - no bridge
      .runWith(Sink.foreach(chunk => print(chunk.contentHead.getOrElse(""))))

    val withoutThinking: Flow[ChatChunk, ChatChunk, NotUsed] =
      Flow[ChatChunk].filterNot(_.isInstanceOf[ChatChunk.Thinking])

    val adapted = OpenAIChatCompletionIOConversionAdapter(
      service,
      outputChatChunkConversion = Some(withoutThinking.toAsyncPipe) // 1.x: Some(withoutThinking)
    )
    adapted.close()

    service
      .createAudioSpeech("Hello from 2.0.")
      .flatMap(_.toByteStringSource.runWith(FileIO.toPath(Paths.get("hello.mp3"))))
  }
}

// "To keep 1.x code unchanged, import SourceConversions._" - warning-free under -feature -Werror
object AkkaConversionsSnippets {
  import io.cequence.wsclient.stream.SourceConversions._

  def unchanged(stream: AsyncStream[ChatChunk])(implicit system: ActorSystem): Unit = {
    val asSource: Source[ChatChunk, NotUsed] = stream
    asSource.runWith(Sink.ignore)
    stream.runWith(Sink.ignore)
    ()
  }
}

object AkkaRoundTrip {
  import io.cequence.wsclient.stream.SourceInterop._

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
      // play-akka-stream (priority 11) beats jdk: the engine's own Source
      LocalChatServer.check("akka (Scala 3.2.2)", text, bridges.get, expectedBridges = 0)
    } finally {
      service.close()
      server.stop()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
