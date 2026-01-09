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
import scala.concurrent.duration._

/**
 * Example demonstrating round-robin load balancing between two Groq services:
 *   - One with `service_tier = flex` (added via adapter)
 *   - One without flex tier (standard)
 *
 * This allows distributing load between flex and standard tiers.
 *
 * Requires `GROQ_API_KEY` environment variable to be set.
 */
object GroqRoundRobinWithFlexTier extends ExampleBase[OpenAIChatCompletionService] {

  private val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  private val adapters = OpenAIServiceAdapters.forChatCompletionService

  // Round-robin between flex and standard services, with retry on both
  override val service: OpenAIChatCompletionService = {

    // Service with flex tier - uses adapter to add service_tier setting
    val groqFlexService = adapters.chatCompletionInput(
      adaptMessages = identity,
      adaptSettings = settings => settings.copy(service_tier = Some(flex))
    )(
      adapters.log(
        ChatCompletionProvider.groq,
        "groq-flex",
        msg => logger.info(msg)
      )
    )

    // Service without flex tier - standard
    val groqStandardService = adapters.log(
      ChatCompletionProvider.groq,
      "groq-standard",
      msg => logger.info(msg)
    )

    implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 1).constantInterval(0.millis)

    adapters.retry(
      adapters.roundRobin(groqFlexService, groqStandardService),
      Some(msg => logger.warn(msg))
    )
  }

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.openai_gpt_oss_20b

  override protected def run: Future[_] =
    for {
      _ <- runChatCompletionAux("Call 1")

      _ <- runChatCompletionAux("Call 2")

      _ <- runChatCompletionAux("Call 3")

      _ <- runChatCompletionAux("Call 4")
    } yield ()

  private def runChatCompletionAux(label: String): Future[Unit] = {
    logger.info(s"$label: Running chat completion with model '$modelId'")

    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(1000)
        )
      )
      .map { response =>
        logger.info(s"$label: Completed\n")
      }
  }
}