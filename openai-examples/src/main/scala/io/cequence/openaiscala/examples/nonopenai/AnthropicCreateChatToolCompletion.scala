package io.cequence.openaiscala.examples.nonopenai

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.{
  FunctionSpec,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatToolCompletionService,
  OpenAICoreService,
  OpenAIService
}

import scala.concurrent.Future

object AnthropicCreateChatToolCompletion extends ExampleBase[OpenAIChatToolCompletionService] {

  override protected val service: OpenAIChatToolCompletionService =
    AnthropicServiceFactory.asOpenAIChatToolCompletionService()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What's the S&P 500 as of today?")
  )

  override protected def run: Future[_] =
    service.createChatToolCompletion(
      messages = messages,
      settings = CreateChatCompletionSettings(NonOpenAIModelId.claude_3_haiku_20240307),
      tools = Seq(
        FunctionSpec(
          name = "get_stock_price",
          description = Some("Get the current stock price of a given company"),
          parameters = Map(
            "type" -> "object",
            "properties" -> Map(
              "company" -> Map(
                "type" -> "string",
                "description" -> "The company name, e.g. Apple Inc."
              )
            ),
            "required" -> Seq("company")
          )
        )
      )
    )
}
