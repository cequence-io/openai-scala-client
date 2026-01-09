package io.cequence.openaiscala.examples.groq

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ServiceTier.flex
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import org.slf4j.LoggerFactory

import scala.concurrent.Future

/**
 * Requires `GROQ_API_KEY` environment variable to be set.
 */
object GroqCreateChatCompletionServiceTierFlex extends ExampleBase[OpenAIChatCompletionService] {

  private val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  private val adapters = OpenAIServiceAdapters.forChatCompletionService

  override val service: OpenAIChatCompletionService = {
    implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 4)

    adapters.retry(
      ChatCompletionProvider.groq,
      Some(msg => logger.warn(msg))
    )
  }

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.openai_gpt_oss_120b

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(8000),
          service_tier = Some(flex)
        )
      )
      .map(printMessageContent)
}
