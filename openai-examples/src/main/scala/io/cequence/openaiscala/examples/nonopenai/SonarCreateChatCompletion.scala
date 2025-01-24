package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.perplexity.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.perplexity.domain.settings.SonarCreateChatCompletionSettings
import io.cequence.openaiscala.perplexity.service.{SonarService, SonarServiceFactory}

import scala.concurrent.Future

/**
 * Requires `SONAR_API_KEY` environment variable to be set.
 */
object SonarCreateChatCompletion extends ExampleBase[SonarService] {

  override val service: SonarService = SonarServiceFactory()

  private val messages = Seq(
    SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.sonar

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = SonarCreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(512)
        )
      )
      .map { response =>
        println(response.contentHead)
        println
        println("Citations:\n" + response.citations.mkString("\n"))
      }
}
