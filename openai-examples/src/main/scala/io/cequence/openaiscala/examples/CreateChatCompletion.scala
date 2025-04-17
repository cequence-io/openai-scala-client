package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ServiceTier}

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
          model = ModelId.o3,
          max_tokens = Some(1000),
          temperature = Some(0.1),
          service_tier = Some(ServiceTier.auto),
          metadata = Map()
        )
      )
      .map { response =>
        println(response.usage.get)
        printMessageContent(response)
      }
}
