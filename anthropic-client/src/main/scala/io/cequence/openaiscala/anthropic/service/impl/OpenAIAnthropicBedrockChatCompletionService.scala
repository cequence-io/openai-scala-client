package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.JsonFormats._
import io.cequence.openaiscala.anthropic.domain.{
  BatchInferenceJob,
  BatchInferenceJobStatus,
  CreateBatchInferenceJobSettings
}
import io.cequence.openaiscala.anthropic.service.{
  AnthropicBedrockBatchSupport,
  AnthropicService
}
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchError,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionBatchStatus
}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

/**
 * Bedrock variant of [[OpenAIAnthropicChatCompletionService]], overriding the
 * provider-agnostic batch endpoints to run on Bedrock's own batch inference API
 * ([[AnthropicBedrockBatchSupport]]) instead of Anthropic's direct Message Batches API (which
 * Bedrock does not expose - the `underlying` Bedrock service fails those calls with
 * `UnsupportedOperationException`).
 *
 * Without a configured [[AnthropicBedrockBatchSupport]], the batch methods behave exactly as
 * the unconfigured Bedrock service does today (fail - see
 * [[AnthropicBedrockServiceImpl.messageBatchesUnsupported]]).
 */
private[service] class OpenAIAnthropicBedrockChatCompletionService(
  underlying: AnthropicService,
  connectionInfo: BedrockConnectionSettings,
  batchSupport: Option[AnthropicBedrockBatchSupport] = None
)(
  implicit executionContext: ExecutionContext
) extends OpenAIAnthropicChatCompletionService(underlying) {

  private lazy val s3Storage = new S3BatchStorage(
    connectionInfo,
    batchSupport.flatMap(_.s3Region).getOrElse(connectionInfo.region)
  )

  private def withInferenceProfile(modelId: String): String =
    connectionInfo.inferenceProfilePrefix match {
      case Some(prefix) if !modelId.startsWith(prefix) => prefix + modelId
      case _                                           => modelId
    }

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] =
    withBatchSupport { support =>
      require(requests.nonEmpty, "At least one batch request expected.")
      require(
        requests.forall(_.customId.matches("[a-zA-Z0-9_-]{1,64}")),
        "Bedrock batch recordIds (custom ids) must match [a-zA-Z0-9_-]{1,64}."
      )

      val anthropicSettings = toAnthropicSettings(settings)
      val batchDir = s"${support.s3PathPrefix}/batch-${java.util.UUID.randomUUID().toString}"
      val inputKey = s"$batchDir/input.jsonl"
      val outputPrefix = s"$batchDir/output/"

      val lines = requests.map { request =>
        val messages =
          toAnthropicSystemMessages(request.messages.filter(_.isSystem), settings) ++
            toAnthropicMessages(request.messages.filter(!_.isSystem), settings)
        val modelInput = BedrockBatchRequestBuilder.modelInput(messages, anthropicSettings)

        Json.stringify(Json.obj("recordId" -> request.customId, "modelInput" -> modelInput))
      }

      for {
        _ <- s3Storage.upload(support.s3Bucket, inputKey, lines.mkString("\n"))

        job <- support.batchService.createBatchInferenceJob(
          CreateBatchInferenceJobSettings(
            jobName = s"openai-scala-client-batch-${java.util.UUID.randomUUID().toString}",
            modelId = withInferenceProfile(anthropicSettings.model),
            roleArn = support.roleArn,
            inputS3Uri = s"s3://${support.s3Bucket}/$inputKey",
            outputS3Uri = s"s3://${support.s3Bucket}/$outputPrefix"
          )
        )
      } yield toBatchInfo(job)
    }

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    withBatchSupport(_.batchService.getBatchInferenceJob(batchId).map(toBatchInfo))

  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    withBatchSupport { support =>
      for {
        job <- support.batchService.getBatchInferenceJob(batchId)

        outputUri = job.outputS3Uri.getOrElse(
          throw new OpenAIScalaClientException(
            s"Batch inference job '$batchId' has no S3 output location (status: ${job.status.map(_.toString).getOrElse("unknown")})."
          )
        )

        (bucket, prefix) = parseS3Uri(outputUri)
        objectKeys <- s3Storage.listObjects(bucket, prefix)
        dataKeys = objectKeys.filterNot(_.endsWith("manifest.json.out"))

        lines <- Future
          .traverse(dataKeys)(key => s3Storage.download(bucket, key))
          .map(_.flatMap(_.split("\n")).map(_.trim).filter(_.nonEmpty))
      } yield lines.map(toBatchResultItem)
    }

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    withBatchSupport { support =>
      for {
        _ <- support.batchService.stopBatchInferenceJob(batchId)
        job <- support.batchService.getBatchInferenceJob(batchId)
      } yield toBatchInfo(job)
    }

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] =
    withBatchSupport { support =>
      for {
        job <- support.batchService.getBatchInferenceJob(batchId)

        _ =
          require(
            job.isTerminated,
            s"Batch inference job '$batchId' is still running (status: ${job.status.map(_.toString).getOrElse("unknown")}) - stop it before deleting its files."
          )

        _ <- job.inputS3Uri match {
          case Some(uri) =>
            val (bucket, key) = parseS3Uri(uri)
            s3Storage.delete(bucket, key)
          case None => Future.unit
        }

        _ <- job.outputS3Uri match {
          case Some(uri) =>
            val (bucket, prefix) = parseS3Uri(uri)
            s3Storage
              .listObjects(bucket, prefix)
              .flatMap(keys => Future.traverse(keys)(key => s3Storage.delete(bucket, key)))
          case None => Future.successful(Nil)
        }
      } yield ()
    }

  private def withBatchSupport[T](
    fun: AnthropicBedrockBatchSupport => Future[T]
  ): Future[T] =
    batchSupport
      .map(fun)
      .getOrElse(
        Future.failed(
          new UnsupportedOperationException(
            "Bedrock batch processing requires an S3 staging bucket and IAM role - create the service via AnthropicServiceFactory.bedrockAsOpenAIWithBatchSupport(...)."
          )
        )
      )

  private def toBatchInfo(job: BatchInferenceJob): ChatCompletionBatchInfo = {
    val status = job.status match {
      case Some(BatchInferenceJobStatus.Completed) | Some(
            BatchInferenceJobStatus.PartiallyCompleted
          ) =>
        ChatCompletionBatchStatus.Completed
      case Some(BatchInferenceJobStatus.Failed)  => ChatCompletionBatchStatus.Failed
      case Some(BatchInferenceJobStatus.Stopped) => ChatCompletionBatchStatus.Cancelled
      case Some(BatchInferenceJobStatus.Expired) => ChatCompletionBatchStatus.Expired
      case _                                     => ChatCompletionBatchStatus.InProgress
    }

    ChatCompletionBatchInfo(
      job.jobArn,
      status,
      job.status.map(_.toString).getOrElse("unknown")
    )
  }

  // one line of a Bedrock batch output file:
  // {"recordId", "modelInput", "modelOutput"} on success, {"recordId", "modelInput", "error"} on failure
  private def toBatchResultItem(line: String): ChatCompletionBatchResultItem = {
    val json = Json.parse(line)
    val recordId = (json \ "recordId").as[String]

    val result: Either[ChatCompletionBatchError, ChatCompletionResponse] =
      (json \ "error").toOption.map { error =>
        val message = (error \ "errorMessage").asOpt[String].getOrElse(error.toString())
        val code = (error \ "errorCode").asOpt[Int].map(_.toString)
        Left(ChatCompletionBatchError(message, code))
      }.getOrElse {
        val message = (json \ "modelOutput")
          .as[io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse]
        Right(toOpenAI(message))
      }

    ChatCompletionBatchResultItem(recordId, result)
  }

  private def parseS3Uri(uri: String): (String, String) = {
    val withoutScheme = uri.stripPrefix("s3://")
    val bucket = withoutScheme.takeWhile(_ != '/')
    val key = withoutScheme.drop(bucket.length + 1)
    (bucket, key)
  }

  override def close(): Unit = {
    super.close()
    batchSupport.foreach(_.batchService.close())
  }
}
