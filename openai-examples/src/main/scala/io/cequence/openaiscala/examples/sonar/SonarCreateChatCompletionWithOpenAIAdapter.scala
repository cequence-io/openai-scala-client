package io.cequence.openaiscala.examples.sonar

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.perplexity.service.SonarServiceConsts
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Requires `SONAR_API_KEY` environment variable to be set.
 */
object SonarCreateChatCompletionWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService]
    with SonarServiceConsts {

  override val service: OpenAIChatCompletionService =
    ChatCompletionProvider.sonar

  private val messages = Seq(
    SystemMessage("You are a drunk pirate who jokes constantly!"),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.sonar_pro

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = modelId,
          temperature = Some(0.1),
          max_tokens = Some(512),
          extra_params = Map(
            includeCitationsInTextResponseParam -> true
//            aHrefForCitationsParam -> true
          )
        )
      )
      .map(printMessageContent)
}
