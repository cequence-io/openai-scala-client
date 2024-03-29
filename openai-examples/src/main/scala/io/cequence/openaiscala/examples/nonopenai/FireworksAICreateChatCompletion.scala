package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionServiceFactory
}
import io.cequence.openaiscala.examples.adapter.ChatCompletionInputAdapterForFireworksAI
import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` environment variable to be set.
 *
 * Check out [[ChatCompletionInputAdapterForFireworksAI]] for a more complex example with an
 * input adapter
 */
object FireworksAICreateChatCompletion extends ExampleBase[OpenAIChatCompletionService] {

  private val fireworksModelPrefix = "accounts/fireworks/models/"
  override val service: OpenAIChatCompletionService = OpenAIChatCompletionServiceFactory(
    coreUrl = "https://api.fireworks.ai/inference/v1/",
    authHeaders = Seq(("Authorization", s"Bearer ${sys.env("FIREWORKS_API_KEY")}"))
  )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.llama_v2_13b_chat

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + modelId,
          temperature = Some(0.1),
          max_tokens = Some(512),
          top_p = Some(0.9),
          presence_penalty = Some(0)
        )
      )
      .map(printMessageContent)
}
