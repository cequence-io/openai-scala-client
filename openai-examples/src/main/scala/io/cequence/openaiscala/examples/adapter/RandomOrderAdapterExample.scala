package io.cequence.openaiscala.examples.adapter

import akka.stream.scaladsl.{Sink, Source}
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
  private val parallelism = 1
  private val modelId = ModelId.gpt_3_5_turbo

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] = {
    // source of 10 repetitions
    val repetitionSource = Source.fromIterator(() => (1 to repetitions).iterator)

    for {
      // run them - we run  the first and second service in random order
      _ <- repetitionSource
        .mapAsync(parallelism)(_ => runChatCompletionAux)
        .runWith(Sink.ignore)
    } yield ()
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
