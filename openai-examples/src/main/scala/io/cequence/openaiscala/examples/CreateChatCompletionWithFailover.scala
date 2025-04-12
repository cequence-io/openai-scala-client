package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

import scala.concurrent.Future

object CreateChatCompletionWithFailover extends Example {

  private val messages = Seq(
    SystemMessage("You are a helpful weather assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionWithFailover(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = ModelId.o3_mini + "x" // initentionally to trigger a failure
        ),
        failoverModels = Seq(ModelId.gpt_4_5_preview, ModelId.gpt_4o),
        retryOnAnyError = true, // if this is what you want
        failureMessage = "Weather assistant failed to provide a response"
      )
      .map { response =>
        println(response.usage.get)
        printMessageContent(response)
      }
}
