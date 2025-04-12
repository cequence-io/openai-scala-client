package io.cequence.openaiscala.examples.cerebras

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionService,
  OpenAIChatCompletionServiceFactory
}

import scala.concurrent.Future

/**
 * Requires `CEREBRAS_API_KEY` environment variable to be set.
 */
object CerebrasCreateChatCompletion extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService =
    OpenAIChatCompletionServiceFactory(ChatProviderSettings.cerebras)

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

//  private val modelId = NonOpenAIModelId.deepseek_r1_distill_llama_70b
  private val modelId = NonOpenAIModelId.cerebras_llama_4_scout_17b_16e_instruct

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(512)
        )
      )
      .map(printMessageContent)
}
