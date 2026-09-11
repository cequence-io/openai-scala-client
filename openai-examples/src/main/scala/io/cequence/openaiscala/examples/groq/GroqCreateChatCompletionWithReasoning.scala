package io.cequence.openaiscala.examples.groq

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.GroqCreateChatCompletionSettingsOps._
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Groq's reasoning knobs (`setReasoningFormat` / `setMaxCompletionTokens`) on a current
 * reasoning-capable model. `ReasoningFormat.hidden` drops the thinking trace; `parsed` returns
 * it in a separate `reasoning` field, and `raw` inlines `<think>` tags in the content.
 *
 * Requires `GROQ_API_KEY` environment variable to be set.
 */
object GroqCreateChatCompletionWithReasoning extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.groq

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.groq_qwen3_8_27b
  private val reasoningFormat = ReasoningFormat.hidden

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1)
        ).setReasoningFormat(reasoningFormat).setMaxCompletionTokens(2048)
      )
      .map(printMessageContent)
}
