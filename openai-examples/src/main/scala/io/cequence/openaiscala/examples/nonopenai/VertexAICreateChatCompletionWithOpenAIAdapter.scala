package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory

import scala.concurrent.Future

// requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and `VERTEXAI_PROJECT_ID` environments variable to be set
object VertexAICreateChatCompletionWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = VertexAIServiceFactory.asOpenAI()

  private val model = NonOpenAIModelId.gemini_1_5_flash_001

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
      .map { content =>
        println(content.choices.headOption.map(_.message.content).getOrElse("N/A"))
      }
}
