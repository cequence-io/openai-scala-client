package io.cequence.openaiscala.examples.fireworksai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService

import scala.concurrent.Future

// requires `openai-scala-client-stream` as a dependency and `FIREWORKS_API_KEY` environment variable to be set
object FireworksAICreateChatCompletionStreamed
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  override val service: OpenAIChatCompletionStreamedService = ChatCompletionProvider.fireworks

  private val fireworksModelPrefix = "accounts/fireworks/models/"

  private val messages = Seq(
    SystemMessage("You are a helpful assistant. Be short."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.llama4_scout_instruct_basic

  override protected def run: Future[_] =
    service
      .createChatCompletionStreamed(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + modelId,
          temperature = Some(0.01),
          max_tokens = Some(2048),
          top_p = Some(0.9),
          presence_penalty = Some(0)
        )
      )
      .runWith(
        Sink.foreach { completion =>
          print(completion.contentHead.getOrElse(""))
        }
      )
}
