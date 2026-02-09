package io.cequence.openaiscala.examples.googlevertexai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

// requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and `VERTEXAI_PROJECT_ID` environments variable to be set
object GoogleVertexAICreateChatCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionStreamedService = ChatCompletionProvider.vertexAI

  private val model = NonOpenAIModelId.gemini_2_5_flash

  private val messages = Seq(
    SystemMessage("You are a helpful assistant who makes jokes about Google. Use markdown"),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model,
          temperature = Some(0)
        )
      )
      .runWith(
        Sink.foreach { response =>
          print(response.contentHead.getOrElse(""))
        }
      )
}
