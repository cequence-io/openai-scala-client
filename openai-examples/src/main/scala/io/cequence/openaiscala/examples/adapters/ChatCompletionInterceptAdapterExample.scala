package io.cequence.openaiscala.examples.adapters

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters

import scala.concurrent.Future

object ChatCompletionInterceptAdapterExample extends ExampleBase[OpenAIService] {

  private val adapters = OpenAIServiceAdapters.forFullService

  override val service: OpenAIService =
    adapters.chatCompletionIntercept(data =>
      Future {
        println(
          s"Chat completion succeeded in ${data.execTimeMs} ms " +
            s"(model: ${data.settings.model}, " +
            s"messages: ${data.messages.size}, " +
            s"response tokens: ${data.response.usage.map(_.completion_tokens).getOrElse("N/A")})"
        )
      }
    )(
      OpenAIServiceFactory()
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
}
