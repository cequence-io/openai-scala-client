package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` environment variable to be set.
 *
 * Check out [[ChatCompletionInputAdapterForFireworksAI]] for a more complex example with an
 * input adapter
 */
object FireworksAICreateChatCompletion extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.fireworks

  private val fireworksModelPrefix = "accounts/fireworks/models/"

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

//  private val modelId = NonOpenAIModelId.deepseek_r1
  private val modelId = NonOpenAIModelId.llama_v3p1_405b_instruct

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + modelId,
          temperature = Some(0.1),
          max_tokens = Some(2048),
          top_p = Some(0.9),
          presence_penalty = Some(0),
          // this is how we can add extra (vendor-specific) parameters
          extra_params = Map("echo" -> true)
        )
      )
      .map(printMessageContent)
}
