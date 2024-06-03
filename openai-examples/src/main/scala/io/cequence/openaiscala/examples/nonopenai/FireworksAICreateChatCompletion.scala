package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionServiceFactory
}
import io.cequence.wsclient.domain.WsRequestContext

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
    WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env("FIREWORKS_API_KEY")}"))
    )
  )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  // note that for e.g. mixtral_8x22b_instruct we need an adapter to convert system messages
  private val modelId = NonOpenAIModelId.llama_v3_8b_instruct

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
