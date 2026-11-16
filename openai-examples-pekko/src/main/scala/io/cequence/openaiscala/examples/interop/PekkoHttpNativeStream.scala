package io.cequence.openaiscala.examples.interop

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, NonOpenAIModelId, UserMessage}
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionStreamedServiceExtra,
  OpenAIServiceFactory
}
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.wsclient.service.ws.PekkoHttpWSClientEngine
import io.cequence.wsclient.stream.SourceInterop._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
 * pekko-http without the Play WS layer: one engine on the application's own `ActorSystem` and
 * its `Http()` connection pools, shared by two providers. `.toSource` hands each service
 * stream back as the engine's own Pekko `Source` - no bridge through `Flow.Publisher`.
 *
 * Needs `OPENAI_SCALA_CLIENT_API_KEY` and `ANTHROPIC_API_KEY`.
 */
object PekkoHttpNativeStream {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("pekko-http-native-stream")
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    // closing the engine leaves your actor system and its Http() pools alone
    val engine = PekkoHttpWSClientEngine()
    val openAI = OpenAIServiceFactory.withStreaming.withEngine(engine)
    val anthropic =
      AnthropicServiceFactory.asOpenAI(AnthropicServiceFactory.withEngine(engine))

    val question = Seq(UserMessage("Name three Norwegian fjords."))

    // the typed stream's text deltas, run as a Pekko Source
    def printAnswer(
      label: String,
      service: OpenAIChatCompletionStreamedServiceExtra,
      settings: CreateChatCompletionSettings
    ): Unit = {
      print(s"$label: ")
      val done = service
        .createChatCompletionStreamedTyped(question, settings)
        .texts
        .toSource
        .runWith(Sink.foreach(print))
      Await.result(done, 2.minutes)
      println()
    }

    try {
      printAnswer("OpenAI", openAI, CreateChatCompletionSettings(ModelId.gpt_5_4_mini))
      printAnswer(
        "Anthropic",
        anthropic,
        CreateChatCompletionSettings(NonOpenAIModelId.claude_haiku_5_5, max_tokens = Some(200))
      )
    } finally {
      openAI.close()
      anthropic.close()
      engine.close()
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
