package io.cequence.openaiscala.examples.fireworksai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.adapter.{MessageConversions, OpenAIServiceAdapters}

import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` environment variable to be set.
 *
 * Check out [[ChatCompletionInputAdapterForFireworksAI]] for a more complex example with an
 * input adapter
 */
object FireworksAICreateChatCompletionWithDeepseek
    extends ExampleBase[OpenAIChatCompletionService] {

  // thinking process ends with </think>
  private val omitThinkingOutput = true

  override val service: OpenAIChatCompletionService = {
    val adapters = OpenAIServiceAdapters.forChatCompletionService
    val vanillaService = ChatCompletionProvider.fireworks

    if (omitThinkingOutput)
      adapters.chatCompletionOutput(MessageConversions.filterOutToThinkEnd)(vanillaService)
    else
      vanillaService
  }

  private val fireworksModelPrefix = "accounts/fireworks/models/"

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.deepseek_r1 // llama_v3p1_405b_instruct

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + modelId,
          temperature = Some(0.1),
          max_tokens = Some(2048),
          top_p = Some(0.9),
          presence_penalty = Some(0)
        )
      )
      .map(printMessageContent)
}
