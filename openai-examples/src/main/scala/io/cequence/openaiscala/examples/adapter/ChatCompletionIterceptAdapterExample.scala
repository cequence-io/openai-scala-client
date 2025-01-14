package io.cequence.openaiscala.examples.adapter

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.examples.adapter.ChatCompletionRouterAdapterExample.openAIService
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.service.{
  OpenAICoreService,
  OpenAICoreServiceFactory,
  OpenAIServiceFactory
}
import io.cequence.wsclient.domain.WsRequestContext

import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` environment variable to be set.
 */
object ChatCompletionIterceptAdapterExample extends ExampleBase[OpenAICoreService] {

  private val openAIService = OpenAIServiceFactory()

  override val service: OpenAICoreService =
    OpenAIServiceAdapters.forCoreService.chatCompletionIntercept(
      intercept = (data: ChatCompletionInterceptData) => {
        println(
          s"OpenAI request processed in ${data.duration} seconds (request sent at ${data.timeRequestSent}, response received at ${data.timeResponseReceived})"
        )
        Future.successful(())
      }
    )(openAIService)

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
          model = ModelId.gpt_4o_mini,
          temperature = Some(0.1),
          max_tokens = Some(512)
        )
      )
      .map(printMessageContent)
}
