package io.cequence.openaiscala.examples.adapters

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.service.{OpenAICoreService, OpenAICoreServiceFactory}
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` environment variable to be set.
 */
object ChatToCompletionAdapterExample extends ExampleBase[OpenAICoreService] {

  private val fireworksModelPrefix = "accounts/fireworks/models/"
  override val service: OpenAICoreService =
    OpenAIServiceAdapters.forCoreService.chatToCompletion(
      OpenAICoreServiceFactory(
        coreUrl = "https://api.fireworks.ai/inference/v1/",
        WsRequestContext(authHeaders =
          Seq(("Authorization", s"Bearer ${sys.env("FIREWORKS_API_KEY")}"))
        )
      )
    )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.mixtral_8x22b

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = fireworksModelPrefix + modelId,
          temperature = Some(0.1),
          max_tokens = Some(512)
        )
      )
      .map(printMessageContent)
}
