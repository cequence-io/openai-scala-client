package io.cequence.openaiscala.examples.adapters

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.OpenAIScalaClientTimeoutException

import scala.concurrent.Future

object ChatCompletionErrorInterceptAdapterExample extends ExampleBase[OpenAIService] {

  private val adapters = OpenAIServiceAdapters.forFullService

  // to demonstrate error interception we create a service that always fails
  private val failingService = adapters.preAction(
    OpenAIServiceFactory(),
    () => Future(throw new OpenAIScalaClientTimeoutException("Fake timeout"))
  )

  override val service: OpenAIService =
    adapters.chatCompletionErrorIntercept(data =>
      Future {
        println(
          s"Chat completion FAILED after ${data.execTimeMs} ms " +
            s"(model: ${data.settings.model}, " +
            s"messages: ${data.messages.size}, " +
            s"error: ${data.error.getMessage})"
        )
      }
    )(
      failingService
    )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(ModelId.gpt_5_mini)
      )
      .map { response =>
        printMessageContent(response)
      }
      .recover { case e: Exception =>
        println(s"\nExpected error caught: ${e.getMessage}")
      }
}
