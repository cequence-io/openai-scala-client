package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.settings.{ChatCompletionResponseFormatType, CreateChatCompletionSettings}
import io.cequence.openaiscala.domain._

import scala.concurrent.Future

object CreateChatCompletion extends Example {

  private val messages = Seq(
    SystemMessage("You are a helpful weather assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.o1_mini,
          temperature = Some(0),
          max_tokens = Some(4000)
        )
      )
      .map { content =>
        printMessageContent(content)
      }
}
