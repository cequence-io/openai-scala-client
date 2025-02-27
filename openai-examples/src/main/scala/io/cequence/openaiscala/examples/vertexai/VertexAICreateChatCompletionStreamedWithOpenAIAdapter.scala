package io.cequence.openaiscala.examples.vertexai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

// requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and `VERTEXAI_PROJECT_ID` environments variable to be set
object VertexAICreateChatCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionStreamedService = ChatCompletionProvider.vertexAI

  // 2024-12-18: works only with us-central1
  private val model = NonOpenAIModelId.gemini_2_0_flash_thinking_exp_1219

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
        Sink.foreach { completion =>
          val content = completion.choices.headOption.flatMap(_.delta.content)
          print(content.getOrElse(""))
        }
      )
}
