package io.cequence.openaiscala.examples.novita

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits.ChatCompletionStreamFactoryExt
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory,
  OpenAIChatCompletionStreamedServiceExtra
}

import scala.concurrent.Future

// requires `openai-scala-client-stream` as a dependency and `NOVITA_API_KEY` environment variable to be set
object NovitaCreateChatCompletionStreamed
    extends ExampleBase[OpenAIChatCompletionStreamedServiceExtra] {

  override val service: OpenAIChatCompletionStreamedServiceExtra =
    OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.novita)

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.novita_deepseek_r1

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.01),
          max_tokens = Some(512)
        )
      )
      .runWith(
        Sink.foreach { chunk =>
          print(chunk.contentHead.getOrElse(""))
        }
      )
}
