package io.cequence.openaiscala.examples.cerebras

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

// requires `openai-scala-client-stream` as a dependency and `CEREBRAS_API_KEY` environment variable to be set
object CerebrasCreateChatCompletionStreamed
    extends ExampleBase[OpenAIChatCompletionStreamedServiceExtra] {

  override val service: OpenAIChatCompletionStreamedServiceExtra =
    OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.cerebras)

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.llama3_1_70b

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
        Sink.foreach { completion =>
          val content = completion.choices.headOption.flatMap(_.delta.content)
          print(content.getOrElse(""))
        }
      )
}
