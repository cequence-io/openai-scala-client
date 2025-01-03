package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}

import scala.concurrent.Future

object CreateChatCompletionWithO1 extends Example {

  private val messages = Seq(
    // system message still works for O1 models but moving forward DeveloperMessage should be used instead
    SystemMessage("You are a helpful weather assistant who likes to make jokes."),
    UserMessage("What is the weather like in Norway per major cities? Answer in json format.")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.o1,
          temperature = Some(0.1),
          response_format_type = Some(ChatCompletionResponseFormatType.json_object),
          max_tokens = Some(4000)
        )
      )
      .map { content =>
        printMessageContent(content)
      }
}
