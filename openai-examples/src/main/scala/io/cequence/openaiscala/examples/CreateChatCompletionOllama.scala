package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionServiceFactory
}

import scala.concurrent.Future

object CreateChatCompletionOllama extends ExampleBase[OpenAIChatCompletionService] {

  override val service = OpenAIChatCompletionServiceFactory(
    coreUrl = "http://localhost:11434/v1/"
  )

  val messages = scala.collection.immutable.Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = "llama2",
          temperature = Some(0.1),
          max_tokens = Some(512),
          top_p = Some(0.9),
          presence_penalty = Some(0)
        )
      )
      .map(printMessageContent)
}
