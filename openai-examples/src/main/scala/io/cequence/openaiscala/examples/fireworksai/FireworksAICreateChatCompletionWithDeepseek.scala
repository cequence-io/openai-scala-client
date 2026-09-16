package io.cequence.openaiscala.examples.fireworksai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Reasoning output from a DeepSeek model on Fireworks.
 *
 * DeepSeek-R1-era builds inlined the chain of thought as a `<think>` block in the content,
 * which the deprecated `MessageConversions.filterOutToThinkEnd` adapter had to cut away.
 * Current builds return it in a separate `reasoning_content` field instead, so the content is
 * already just the answer and no output adapter is needed.
 *
 * Note that the non-streamed domain message carries no reasoning field, so that field is
 * dropped here; see [[FireworksAICreateChatCompletionStreamedWithDeepseek]] for the streamed
 * path, which does surface it.
 *
 * Requires `FIREWORKS_API_KEY` environment variable to be set.
 *
 * Check out [[ChatCompletionInputAdapterForFireworksAI]] for a more complex example with an
 * input adapter.
 */
object FireworksAICreateChatCompletionWithDeepseek
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.fireworks

  private val fireworksModelPrefix = "accounts/fireworks/models/"

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.deepseek_v4p1_flash

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
