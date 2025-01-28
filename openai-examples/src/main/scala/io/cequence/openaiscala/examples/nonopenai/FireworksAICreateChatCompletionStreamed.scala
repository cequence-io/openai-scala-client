package io.cequence.openaiscala.examples.nonopenai

import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionIOConversionAdapter
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.service.adapter.MessageConversions

import scala.concurrent.Future

// requires `openai-scala-client-stream` as a dependency and `FIREWORKS_API_KEY` environment variable to be set
object FireworksAICreateChatCompletionStreamed
    extends ExampleBase[OpenAIChatCompletionStreamedService] {

  // thinking process ends with </think>
  private val omitThinkingOutput = true

  override val service: OpenAIChatCompletionStreamedService = {
    val vanillaService = ChatCompletionProvider.fireworks

    if (omitThinkingOutput)
      OpenAIChatCompletionIOConversionAdapter(
        vanillaService,
        outputChunkMessageConversion = Some(MessageConversions.filterOutToThinkEndFlow)
      )
    else
      vanillaService
  }

  private val fireworksModelPrefix = "accounts/fireworks/models/"

  private val messages = Seq(
    SystemMessage("You are a helpful assistant. Be short."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.deepseek_r1 // drbx_instruct

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
          val content = completion.choices.headOption.flatMap(_.delta.content)
          print(content.getOrElse(""))
        }
      )
}
