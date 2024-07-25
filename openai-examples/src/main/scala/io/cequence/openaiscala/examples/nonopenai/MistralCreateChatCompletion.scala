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

// requires `MISTRAL_API_KEY` environment variable to be set
object MistralCreateChatCompletion extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = OpenAIChatCompletionServiceFactory(
    coreUrl = "https://api.mistral.ai/v1/",
    WsRequestContext(authHeaders =
      Seq(("Authorization", s"Bearer ${sys.env("MISTRAL_API_KEY")}"))
    )
  )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  private val modelId = NonOpenAIModelId.open_mistral_nemo

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
