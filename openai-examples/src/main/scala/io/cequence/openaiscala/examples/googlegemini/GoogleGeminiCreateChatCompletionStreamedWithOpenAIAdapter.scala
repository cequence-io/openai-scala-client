package io.cequence.openaiscala.examples.googlegemini

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

/**
 * Requires `GOOGLE_API_KEY` environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionStreamedWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService = GeminiServiceFactory.asOpenAI()

  private val messages = Seq(
    SystemMessage("You are a pirate who likes to joke."),
    UserMessage("Explain AI to a 5-year-old.")
  )

  private val modelId = NonOpenAIModelId.gemini_2_5_flash

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(20),
          max_tokens = Some(1000)
        )
      )
      .runWith(
        Sink.foreach { completion =>
          print(completion.contentHead.getOrElse(""))
        }
      )
}
