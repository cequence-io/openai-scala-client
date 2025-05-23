package io.cequence.openaiscala.examples.googlevertexai

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and `VERTEXAI_PROJECT_ID` environments variable to be set
object GoogleVertexAICreateChatCompletionWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.vertexAI

  private val model = NonOpenAIModelId.gemini_2_0_flash

  private val messages = Seq(
    SystemMessage("You are a helpful assistant who makes jokes about Google."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model,
          temperature = Some(0)
        )
      )
      .map { response =>
        println(response.contentHead)
        println("Finish reason: " + response.choices.head.finish_reason.getOrElse("N/A"))
        println("Usage        : " + response.usage.get)
      }
}
