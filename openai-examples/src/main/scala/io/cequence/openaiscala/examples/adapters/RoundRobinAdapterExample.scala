package io.cequence.openaiscala.examples.adapters

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters

import scala.concurrent.Future

object RoundRobinAdapterExample extends ExampleBase[OpenAIService] {

  private val adapters = OpenAIServiceAdapters.forFullService

  // OpenAI
  private val openAIService1 = adapters.log(
    OpenAIServiceFactory(),
    "openAIService1",
    println(_) // simple logging
  )

  private val openAIService2 = adapters.log(
    OpenAIServiceFactory(), // normally we would pass here e.g. a different access token
    "openAIService2",
    println(_) // simple logging
  )

  override val service: OpenAIService = adapters.roundRobin(openAIService1, openAIService2)

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    for {
      // runs on the first service
      _ <- runChatCompletionAux(ModelId.gpt_3_5_turbo)

      // runs on the second service
      _ <- runChatCompletionAux(ModelId.gpt_3_5_turbo)
    } yield ()

  private def runChatCompletionAux(model: String) = {
    println(s"Running chat completion with the model '$model'\n")

    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(model)
      )
      .map(printMessageContent)
  }
}
