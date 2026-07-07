package io.cequence.openaiscala.examples.googlevertexai

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}
import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Runs a provider-agnostic chat-completion batch against Vertex AI batch prediction (50% of
 * standard cost) through the OpenAI adapter: the requests are staged as a JSONL file in the
 * given Cloud Storage bucket, a batch prediction job is created and polled, and the results
 * are read back from the job's output files.
 *
 * Note: custom ids ride as GCP request labels and must therefore match `[a-z0-9_-]{1,63}`.
 *
 * Requires `VERTEXAI_PROJECT_ID` and `VERTEXAI_LOCATION` env. variables, Google Application
 * Default Credentials (`gcloud auth application-default login`), and
 * `VERTEXAI_BATCH_GCS_BUCKET` - a Cloud Storage bucket the credentials can read/write.
 */
object GoogleVertexAICreateChatCompletionBatchWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService with OpenAIChatCompletionBatchService] {

  override val service: OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
    VertexAIServiceFactory.asOpenAIWithBatchSupport(
      gcsBucket = sys.env("VERTEXAI_BATCH_GCS_BUCKET")
    )

  private val requests = Seq(
    ChatCompletionBatchRequest(
      customId = "capital-norway",
      messages = Seq(
        SystemMessage("You are a concise assistant."),
        UserMessage("What is the capital of Norway? Reply in one word.")
      )
    ),
    ChatCompletionBatchRequest(
      customId = "capital-sweden",
      messages = Seq(
        SystemMessage("You are a concise assistant."),
        UserMessage("What is the capital of Sweden? Reply in one word.")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatCompletionBatchAndWaitForResults(
        requests,
        settings = CreateChatCompletionSettings(NonOpenAIModelId.gemini_2_5_flash_lite),
        pollingInterval = 30.seconds,
        deleteBatchAfterUse = true
      )
      .map(_.foreach { item =>
        item.result match {
          case Right(response) =>
            println(s"${item.customId}: ${response.contentHead}")
          case Left(error) =>
            println(s"${item.customId}: ERROR ${error.message}")
        }
      })
}
