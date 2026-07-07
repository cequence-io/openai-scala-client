package io.cequence.openaiscala.examples.googlevertexai

import akka.pattern.after
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.vertexai.domain.{
  BatchJobInput,
  BatchJobOutput,
  BatchPredictionJob,
  CreateBatchPredictionJobSettings
}
import io.cequence.openaiscala.vertexai.service.{
  VertexAIBatchPredictionService,
  VertexAIServiceFactory
}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Live end-to-end check of the Vertex AI batch prediction API (50% of standard cost): create a
 * batch job from a Cloud Storage JSONL file, list jobs, poll until a terminal state, and print
 * the output location.
 *
 * The input file contains one request per line, e.g.:
 * {{{
 * {"request": {"contents": [{"role": "user", "parts": [{"text": "Capital of Norway? One word."}]}]}}
 * }}}
 *
 * Requires `VERTEXAI_PROJECT_ID` and `VERTEXAI_LOCATION` env. variables, Google Application
 * Default Credentials (`gcloud auth application-default login`), plus:
 *   - `VERTEXAI_BATCH_INPUT_URI` - a `gs://` URI of the input JSONL file
 *   - `VERTEXAI_BATCH_OUTPUT_URI` - a `gs://` output prefix the predictions are written under
 */
object GoogleVertexAIBatchPredictionLive extends ExampleBase[VertexAIBatchPredictionService] {

  override protected val service: VertexAIBatchPredictionService =
    VertexAIServiceFactory.batchPrediction()

  private val inputUri = sys.env("VERTEXAI_BATCH_INPUT_URI")
  private val outputUri = sys.env("VERTEXAI_BATCH_OUTPUT_URI")

  override protected def run: Future[_] =
    for {
      job <- service.createBatchPredictionJob(
        CreateBatchPredictionJobSettings(
          displayName = "openai-scala-client example batch",
          model = NonOpenAIModelId.gemini_2_5_flash_lite,
          input = BatchJobInput.Gcs(inputUri),
          output = BatchJobOutput.Gcs(outputUri)
        )
      )
      _ = println(s"created: name=${job.name} state=${job.state}")

      jobs <- service.listBatchPredictionJobs(pageSize = Some(5))
      _ = println(s"list: count=${jobs.batchPredictionJobs.size}")

      finished <- pollUntilTerminated(job.name)
      _ = println(
        s"finished: state=${finished.state} error=${finished.error} stats=${finished.completionStats}"
      )
      _ = finished.outputInfo.foreach(info =>
        println(s"predictions at: ${info.gcsOutputDirectory}")
      )
    } yield ()

  private def pollUntilTerminated(jobId: String): Future[BatchPredictionJob] =
    service.getBatchPredictionJob(jobId).flatMap { job =>
      if (job.isTerminated)
        Future.successful(job)
      else {
        println(s"polling: state=${job.state}...")
        after(30.seconds, scheduler)(pollUntilTerminated(jobId))
      }
    }
}
