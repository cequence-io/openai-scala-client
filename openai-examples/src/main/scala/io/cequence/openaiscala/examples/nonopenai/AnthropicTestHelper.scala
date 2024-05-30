package io.cequence.openaiscala.examples.nonopenai

import akka.actor.Scheduler
import io.cequence.openaiscala.OpenAIScalaClientTimeoutException
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters

import scala.concurrent.{ExecutionContext, Future}

object AnthropicTestHelper {

  val FailingModel: String = NonOpenAIModelId.claude_3_opus_20240229
  val WorkingModel: String = NonOpenAIModelId.claude_3_haiku_20240307

  def timoutingService(
    implicit ec: ExecutionContext,
    system: akka.actor.ActorSystem
  ): OpenAIChatCompletionService = {

    // adapters to use (round-robin, retry, etc.)
    val adapters = OpenAIServiceAdapters.forChatCompletionService

    // implicit retry settings and scheduler
    implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 2)
    implicit val scheduler: Scheduler = system.scheduler

    // regular OpenAI service
    val regularService = AnthropicServiceFactory.asOpenAI()

    // to demonstrate the retry mechanism we introduce a service that always times out
    val failingService = adapters.preAction(
      AnthropicServiceFactory.asOpenAI(),
      () => Future(throw new OpenAIScalaClientTimeoutException("Fake timeout"))
    )

    // we then map the failing service to a specific model - gpt-3.5-turbo-1106
    // for all other models we use the regular service
    val mergedService = adapters.chatCompletionRouter(
      serviceModels = Map(failingService -> Seq(FailingModel)),
      regularService
    )

    // and finally we apply the retry mechanism to the merged service
    val service: OpenAIChatCompletionService = adapters.retry(
      mergedService,
      Some(println(_)) // simple logging
    )

    service
  }

}
