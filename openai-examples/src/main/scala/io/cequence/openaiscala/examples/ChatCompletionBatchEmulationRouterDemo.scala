package io.cequence.openaiscala.examples

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  NonOpenAIModelId,
  UserMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.perplexity.service.SonarServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.adapter.OpenAIChatCompletionServiceRouter.BatchChatService
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Demonstrates `OpenAIServiceAdapters.chatCompletionBatchEmulated` - the fallback adapter that
 * lets a provider '''without''' native batch support join a
 * `OpenAIServiceAdapters.chatCompletionBatchRouter` anyway. Gemini has native batch, so it is
 * registered as-is; Perplexity Sonar has no batch API, so it is wrapped with
 * `chatCompletionBatchEmulated` first - the wrapper logs a warning and, under the hood, just
 * runs the batch's requests as ordinary synchronous chat completions (no ~50% batch discount,
 * no async/24h processing - standard cost, immediate execution). Both models are then
 * submitted through the '''same''' router, exactly like any other routed batch call: the
 * router neither knows nor cares that one of its members is emulated.
 *
 * This is the pattern to reach for whenever a batch router needs to include a provider that
 * doesn't (yet) support batch natively, without special-casing it at every call site.
 *
 * Requires `GOOGLE_API_KEY` (for the native Gemini batch) and `SONAR_API_KEY` (for the
 * emulated Sonar fallback).
 */
object ChatCompletionBatchEmulationRouterDemo {

  private val geminiModel = NonOpenAIModelId.gemini_2_5_flash
  private val sonarModel = NonOpenAIModelId.sonar

  private def buildRouter(
  )(
    implicit ec: ExecutionContext
  ): BatchChatService = {
    val geminiService = GeminiServiceFactory.asOpenAI() // native batch support

    // no native batch -> wrap with the emulation adapter so it can still join the router
    val sonarService =
      OpenAIServiceAdapters.chatCompletionBatchEmulated(SonarServiceFactory.asOpenAI())

    OpenAIServiceAdapters.forChatCompletionService.chatCompletionBatchRouter(
      serviceModels = Map(sonarService -> Seq(sonarModel)),
      geminiService // default
    )
  }

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val system: ActorSystem = ActorSystem("batch-emulation-router-demo")
    implicit val scheduler: Scheduler = system.scheduler

    val router = buildRouter()

    def runBatch(model: String): Future[Unit] =
      router
        .createChatCompletionBatchAndWaitForResults(
          Seq(
            ChatCompletionBatchRequest(
              "capital",
              Seq(UserMessage("What is the capital of France? Reply in one word."))
            )
          ),
          CreateChatCompletionSettings(model),
          pollingInterval = 5.seconds
        )
        .map { results =>
          println(s"model=$model")
          results.foreach(item => println(s"  ${item.customId}: ${item.result}"))
        }

    // Gemini goes through native batch; Sonar goes through the emulation fallback (a warning
    // is logged for it) - the calling code above looks identical either way.
    val flow = for {
      _ <- runBatch(geminiModel)
      _ <- runBatch(sonarModel)
    } yield ()

    try Await.result(flow, 5.minutes)
    finally {
      router.close()
      system.terminate()
      ()
    }
  }
}
