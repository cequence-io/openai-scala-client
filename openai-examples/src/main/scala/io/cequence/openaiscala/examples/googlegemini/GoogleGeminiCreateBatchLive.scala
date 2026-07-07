package io.cequence.openaiscala.examples.googlegemini

import akka.pattern.after
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.domain.{
  BatchRequestItem,
  ChatRole,
  Content,
  GenerateContentBatch
}
import io.cequence.openaiscala.gemini.service.{GeminiService, GeminiServiceFactory}
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Live end-to-end check of the Gemini Batch API (Batch Mode): create a small inline batch
 * (processed at 50% of standard cost, 24h turnaround target - small batches typically finish
 * in minutes), list, poll until a terminal state, print the inlined results, and delete the
 * batch.
 *
 * Requires `GOOGLE_API_KEY`.
 */
object GoogleGeminiCreateBatchLive extends ExampleBase[GeminiService] {

  override protected val service: GeminiService = GeminiServiceFactory()

  override protected def run: Future[_] =
    for {
      batch <- service.createBatchGenerateContent(
        displayName = "openai-scala-client example batch",
        requests = Seq(
          BatchRequestItem(
            key = "capital-norway",
            contents = Seq(
              Content.textPart("What is the capital of Norway? One word.", ChatRole.User)
            )
          ),
          BatchRequestItem(
            key = "capital-sweden",
            contents = Seq(
              Content.textPart("What is the capital of Sweden? One word.", ChatRole.User)
            )
          )
        ),
        settings = GenerateContentSettings(model = NonOpenAIModelId.gemini_2_5_flash_lite)
      )
      _ = println(s"created: name=${batch.name} state=${batch.state}")

      listed <- service.listBatches(pageSize = Some(5))
      _ = println(s"list: count=${listed.batches.size}")

      finished <- pollUntilTerminated(batch.name)
      _ = println(s"finished: state=${finished.state} stats=${finished.batchStats}")

      _ = finished.output.foreach { output =>
        output.inlinedResponses.foreach { response =>
          val text =
            response.response.map(_.contentHeadText).getOrElse(s"ERROR: ${response.error}")
          println(s"${response.key.getOrElse("?")}: $text")
        }
      }

      _ <- service.deleteBatch(batch.name)
      _ = println("deleted")
    } yield ()

  private def pollUntilTerminated(name: String): Future[GenerateContentBatch] =
    service.getBatch(name).flatMap { batch =>
      if (batch.isTerminated)
        Future.successful(batch)
      else {
        println(s"polling: state=${batch.state}...")
        after(15.seconds, scheduler)(pollUntilTerminated(name))
      }
    }
}
