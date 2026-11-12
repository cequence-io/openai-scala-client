package io.cequence.openaiscala.examples.adapters

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters

import scala.concurrent.Future

object RandomOrderAdapterExample extends ExampleBase[OpenAIService] {

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

  override val service: OpenAIService = adapters.randomOrder(openAIService1, openAIService2)

  private val repetitions = 10
  private val modelId = ModelId.gpt_4o

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  // 10 repetitions, one after the other - each goes to the first or the second service at random
  override protected def run: Future[_] =
    (1 to repetitions).foldLeft(Future.successful(())) {
      (
        done,
        _
      ) => done.flatMap(_ => runChatCompletionAux)
    }

  private def runChatCompletionAux =
    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(modelId)
      )
      .map { response =>
        printMessageContent(response)
        println("--------")
      }
}
