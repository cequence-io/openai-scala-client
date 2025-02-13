package io.cequence.openaiscala.examples.adapter

import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{ModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.{OpenAIScalaClientException, OpenAIScalaClientTimeoutException}

import scala.concurrent.Future

object RetryAdapterExample extends ExampleBase[OpenAIService] {

  // adapters to use (round-robin, retry, etc.)
  private val adapters = OpenAIServiceAdapters.forFullService

  // implicit retry settings and scheduler
  private implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 4)

  // regular OpenAI service
  private val regularService = OpenAIServiceFactory()

  // to demonstrate the retry mechanism we introduce a service that always times out
  private val failingService = adapters.preAction(
    OpenAIServiceFactory(),
    () => Future(throw new OpenAIScalaClientTimeoutException("Fake timeout"))
  )

  // we then map the failing service to a specific model - gpt-3.5-turbo-1106
  // for all other models we use the regular service
  private val mergedService = adapters.chatCompletionRouter(
    serviceModels = Map(failingService -> Seq(ModelId.gpt_3_5_turbo_1106)),
    regularService
  )

  // and finally we apply the retry mechanism to the merged service
  override val service: OpenAIService = adapters.retry(
    mergedService,
    Some(println(_)) // simple logging
  )

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What is the weather like in Norway?")
  )

  override protected def run: Future[_] =
    for {
      // this invokes the failing service, which triggers the retry mechanism
      _ <- runChatCompletionAux(ModelId.gpt_3_5_turbo_1106).recover {
        case e: OpenAIScalaClientException =>
          println(s"Too many retries, giving up on '${e.getMessage}'")
      }

      // should complete without retry
      _ <- runChatCompletionAux(ModelId.gpt_3_5_turbo_0125)
    } yield ()

  private def runChatCompletionAux(model: String) = {
    println(s"Running chat completion with the model '$model'\n")

    service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(model)
      )
      .map { response =>
        printMessageContent(response)
        println("--------")
      }
  }
}
