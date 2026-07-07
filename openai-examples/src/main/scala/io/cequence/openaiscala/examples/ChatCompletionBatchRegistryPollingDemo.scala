package io.cequence.openaiscala.examples

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  NonOpenAIModelId,
  UserMessage
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.adapter.OpenAIChatCompletionServiceRouter.BatchChatService
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Demonstrates the library's model-based batch router
 * (`OpenAIServiceAdapters.chatCompletionBatchRouter`) - the batch-aware sibling of
 * `chatCompletionRouter`, routing the provider-agnostic batch endpoints across providers by
 * model. That's exactly what a central batch registry needs: submit once through the router
 * (dispatched by model, like any other routed call), persist `(model, batchId)`, and later - a
 * different call, a different process, a different day - rebuild the same router and pull
 * status/results by passing that same pair back in.
 *
 * Batch status/results/cancel/delete calls carry no `CreateChatCompletionSettings` (only a
 * batch id), so `model` rides as an explicit second argument instead - see
 * [[io.cequence.openaiscala.service.OpenAIChatCompletionBatchService]]. The router dispatches
 * on it exactly like it dispatches `createChatCompletion` on `settings.model`: a deterministic
 * map lookup, not a guess - a batch id alone is an opaque, provider-specific string and is
 * not, by itself, a routing key. Every registered service (and the default) must be
 * batch-capable, which is why the router returns a `OpenAIChatCompletionService with
 * OpenAIChatCompletionBatchService`.
 *
 * Requires `GOOGLE_API_KEY` and `ANTHROPIC_API_KEY` - only Gemini is used for the live batch;
 * Anthropic is registered alongside it purely to prove the router really dispatches to the
 * right service out of more than one candidate, not just the only one available.
 */
object ChatCompletionBatchRegistryPollingDemo {

  private val model = NonOpenAIModelId.gemini_2_5_flash

  // the router config is a fixed, static thing - built identically by the submitting step and
  // the poller, exactly like any other use of chatCompletionBatchRouter
  private def buildRouter(
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): BatchChatService = {
    val geminiService = GeminiServiceFactory.asOpenAI()
    val anthropicService = AnthropicServiceFactory.asOpenAI()

    OpenAIServiceAdapters.forChatCompletionService.chatCompletionBatchRouter(
      serviceModels = Map(geminiService -> Seq(model)),
      anthropicService // default - just to prove the router picks the right service among several
    )
  }

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val system: ActorSystem = ActorSystem("batch-registry-demo")
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: Scheduler = system.scheduler

    val flow = for {
      registryRow <- submit()
      (modelId, batchId) = registryRow
      _ <- poll(modelId, batchId)
    } yield ()

    try Await.result(flow, 30.minutes)
    finally {
      system.terminate()
      ()
    }
  }

  // --- Phase 1: submission - e.g. a web request handler. Only (model, batchId) survives. ---
  private def submit(
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): Future[(String, String)] = {
    val router = buildRouter()

    router
      .createChatCompletionBatch(
        Seq(
          ChatCompletionBatchRequest(
            "norway",
            Seq(UserMessage("What is the capital of Norway? Reply in one word."))
          ),
          ChatCompletionBatchRequest(
            "sweden",
            Seq(UserMessage("What is the capital of Sweden? Reply in one word."))
          )
        ),
        CreateChatCompletionSettings(model)
      )
      .map { batch =>
        // the router (and everything behind it) is gone from here on - only these two strings
        // would be persisted as a registry row
        router.close()
        println(s"submitted: model=$model batchId=${batch.id}")
        (model, batch.id)
      }
  }

  // --- Phase 2: independent poller - run later/elsewhere, holding only (model, batchId).
  // Rebuilds the identical router config. ---
  private def poll(
    model: String,
    batchId: String
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer,
    scheduler: Scheduler
  ): Future[Unit] = {
    val router = buildRouter()

    def loop(): Future[Unit] =
      router.getChatCompletionBatch(batchId, model).flatMap { info =>
        println(s"poller: status=${info.status} (provider status: ${info.providerStatus})")

        if (info.isDone)
          router.retrieveChatCompletionBatchResults(batchId, model).map { results =>
            results.foreach(item => println(s"${item.customId}: ${item.result}"))
            router.close()
          }
        else
          akka.pattern.after(10.seconds, scheduler)(loop())
      }

    loop()
  }
}
