package io.cequence.openaiscala.vertexai.service.impl

import akka.NotUsed
import akka.stream.scaladsl.{Source, StreamConverters}
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.GenerateContentResponse
import com.google.cloud.vertexai.generativeai.GenerativeModel
import com.google.auth.oauth2.GoogleCredentials
import com.google.protobuf.util.JsonFormat
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  AssistantToolMessage,
  BaseMessage,
  ChatCompletionBatchError,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionBatchStatus,
  ChatCompletionTool,
  ChatRole,
  FunctionCallSpec,
  JsonSchema
}
import io.cequence.openaiscala.domain.response.{PromptTokensDetails, UsageInfo}
import io.cequence.openaiscala.vertexai.domain.{
  BatchJobInput,
  BatchJobOutput,
  BatchPredictionJob,
  CreateBatchPredictionJobSettings,
  JobState
}
import io.cequence.openaiscala.vertexai.service.VertexAIBatchSupport
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse,
  ChunkMessageSpec
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}
import io.cequence.openaiscala.vertexai.domain.{
  FunctionDeclaration => VertexAIFunctionDeclaration,
  Schema => VertexAISchema,
  SchemaType => VertexAISchemaType,
  Tool => VertexAITool
}
import io.cequence.openaiscala.vertexai.domain.settings.{
  FunctionCallingMode,
  ToolConfig,
  CreateChatCompletionSettingsOps
}
import CreateChatCompletionSettingsOps._

import java.util.concurrent.CompletableFuture
import scala.collection.convert.ImplicitConversions.`list asScalaBuffer`
import scala.collection.convert.ImplicitConversions.`seq AsJavaList`
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}

// TODO: convert Google exceptions
//  (e.g. java.util.concurrent.CompletionException (ResourceExhaustedException)) to OpenAI exceptions
private[service] class OpenAIVertexAIChatCompletionService(
  underlying: VertexAI,
  batchSupport: Option[VertexAIBatchSupport] = None
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra
    with OpenAIChatCompletionBatchService {

  private val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  // label riding on every batch request line so results (returned in no particular order)
  // can be matched back to requests
  private val customIdLabel = "custom_id"

  private lazy val gcsStorage = new GcsBatchStorage(
    GoogleCredentials.getApplicationDefault.createScoped(
      "https://www.googleapis.com/auth/cloud-platform"
    )
  )

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    val model = createModel(messages, settings)

    val javaFuture = model.generateContentAsync(
      toNonSystemVertexAI(
        messages.filter(message =>
          message.role != ChatRole.System && message.role != ChatRole.Developer
        )
      )
    )
    val scalaFuture: Future[GenerateContentResponse] =
      toScala(CompletableFuture.supplyAsync(() => javaFuture.get))

    scalaFuture.map { response =>
      toOpenAI(response, settings.model)
    }
  }

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] = {
    val functionDeclarations = tools.collect { case ft: FunctionTool =>
      VertexAIFunctionDeclaration(
        name = ft.name,
        description = ft.description.getOrElse(""),
        parameters = Some(toVertexAISchema(ft.parameters))
      )
    }

    val toolConfig = responseToolChoice match {
      case Some(name) =>
        Some(
          ToolConfig.FunctionCallingConfig(
            mode = Some(FunctionCallingMode.ANY),
            allowedFunctionNames = Some(Seq(name))
          )
        )
      case None =>
        Some(
          ToolConfig.FunctionCallingConfig(
            mode = Some(FunctionCallingMode.AUTO),
            allowedFunctionNames = None
          )
        )
    }

    val settingsWithTools =
      settings.setVertexAITools(Seq(VertexAITool.FunctionDeclarations(functionDeclarations)))

    val settingsWithToolConfig =
      toolConfig.map(settingsWithTools.setVertexAIToolConfig).getOrElse(settingsWithTools)

    val model = createModel(messages, settingsWithToolConfig)

    val javaFuture = model.generateContentAsync(
      toNonSystemVertexAI(
        messages.filter(message =>
          message.role != ChatRole.System && message.role != ChatRole.Developer
        )
      )
    )
    val scalaFuture: Future[GenerateContentResponse] =
      toScala(CompletableFuture.supplyAsync(() => javaFuture.get))

    scalaFuture.map { response =>
      toOpenAIToolResponse(response, settings.model)
    }
  }

  private def toVertexAISchema(jsonSchema: JsonSchema): VertexAISchema =
    jsonSchema match {
      case JsonSchema.String(description, enumVals) =>
        VertexAISchema(
          `type` = VertexAISchemaType.STRING,
          description = description,
          `enum` = if (enumVals.nonEmpty) Some(enumVals) else None
        )

      case JsonSchema.Number(description) =>
        VertexAISchema(`type` = VertexAISchemaType.NUMBER, description = description)

      case JsonSchema.Integer(description) =>
        VertexAISchema(`type` = VertexAISchemaType.INTEGER, description = description)

      case JsonSchema.Boolean(description) =>
        VertexAISchema(`type` = VertexAISchemaType.BOOLEAN, description = description)

      case JsonSchema.Null() =>
        VertexAISchema(`type` = VertexAISchemaType.TYPE_UNSPECIFIED)

      case JsonSchema.Object(properties, required, _, description) =>
        VertexAISchema(
          `type` = VertexAISchemaType.OBJECT,
          description = description,
          properties =
            if (properties.nonEmpty)
              Some(properties.map { case (k, v) => k -> toVertexAISchema(v) }.toMap)
            else None,
          required = if (required.nonEmpty) Some(required) else None
        )

      case JsonSchema.Array(items, description) =>
        VertexAISchema(
          `type` = VertexAISchemaType.ARRAY,
          description = description,
          items = Some(toVertexAISchema(items))
        )

      case _ =>
        VertexAISchema(`type` = VertexAISchemaType.TYPE_UNSPECIFIED)
    }

  private def toOpenAIToolResponse(
    response: GenerateContentResponse,
    model: String
  ): ChatToolCompletionResponse = {
    val candidates = response.getCandidatesList.toSeq

    val choices = candidates.map { candidate =>
      val parts = candidate.getContent.getPartsList.toSeq

      val toolCalls = parts.filter(_.hasFunctionCall).map { part =>
        val fc = part.getFunctionCall
        val argsJson = com.google.protobuf.util.JsonFormat.printer().print(fc.getArgs)
        // Vertex FunctionCall has no server-assigned id, so synthesize a unique one
        // (function name alone collides on parallel calls to the same function).
        val callId = java.util.UUID.randomUUID().toString
        (
          callId,
          FunctionCallSpec(fc.getName, argsJson): io.cequence.openaiscala.domain.ToolCallSpec
        )
      }

      val texts = parts.filter(p => p.hasText && p.getText.nonEmpty).map(_.getText)

      val message = AssistantToolMessage(
        content = if (texts.nonEmpty) Some(texts.mkString("\n")) else None,
        name = None,
        tool_calls = toolCalls
      )

      ChatToolCompletionChoiceInfo(
        message = message,
        index = candidate.getIndex,
        finish_reason = Option(candidate.getFinishReason).map(_.toString)
      )
    }

    ChatToolCompletionResponse(
      id = "vertexai",
      created = new java.util.Date(),
      model = model,
      system_fingerprint = None,
      choices = choices,
      usage = Some(toOpenAI(response.getUsageMetadata))
    )
  }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] = {
    val model = createModel(messages, settings)

    val javaStream = model.generateContentStream(
      toNonSystemVertexAI(
        messages.filter(message =>
          message.role != ChatRole.System && message.role != ChatRole.Developer
        )
      )
    )
    val scalaStream = StreamConverters.fromJavaStream(() => javaStream.stream())

    scalaStream.map { response =>
      val openAIResponse = toOpenAI(response, settings.model)

      ChatCompletionChunkResponse(
        id = openAIResponse.id,
        created = openAIResponse.created,
        model = openAIResponse.model,
        system_fingerprint = openAIResponse.system_fingerprint,
        choices = openAIResponse.choices.map { info =>
          ChatCompletionChoiceChunkInfo(
            delta = ChunkMessageSpec(
              Some(ChatRole.Assistant),
              Some(info.message.content)
            ),
            index = info.index,
            finish_reason = info.finish_reason
          )
        },
        usage = openAIResponse.usage
      )
    }
  }

  private def createModel(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): GenerativeModel = {
    val config = toVertexAI(settings)

    var modelAux = new GenerativeModel(settings.model, underlying).withGenerationConfig(config)

    // Add tools if present
    toVertexAITools(settings).foreach { tools =>
      modelAux = modelAux.withTools(`seq AsJavaList`(tools))
    }

    // Add tool config if present
    toVertexAIToolConfig(settings).foreach { toolConfig =>
      modelAux = modelAux.withToolConfig(toolConfig)
    }

    // TODO: system messages not support e.g. for gemini-1.0-pro-001
    toSystemVertexAI(messages)
      .map(
        modelAux.withSystemInstruction
      )
      .getOrElse(
        modelAux
      )
  }

  // -- Batch processing (provider-agnostic) --

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] =
    withBatchSupport { support =>
      val protoPrinter = JsonFormat.printer().omittingInsignificantWhitespace()
      val batchDirName =
        s"${support.gcsPathPrefix}/batch-${java.util.UUID.randomUUID().toString}"
      val inputObjectName = s"$batchDirName/input.jsonl"

      val generationConfigJson = Json.parse(protoPrinter.print(toVertexAI(settings)))

      val lines = requests.map { request =>
        val systemInstruction = toSystemVertexAI(request.messages)
        val contents = toNonSystemVertexAI(
          request.messages.filter(message =>
            message.role != ChatRole.System && message.role != ChatRole.Developer
          )
        )

        val requestJson = JsObject(
          Seq(
            "contents" -> JsArray(
              contents.map(content => Json.parse(protoPrinter.print(content)))
            ),
            "generationConfig" -> generationConfigJson,
            "labels" -> Json.obj(customIdLabel -> request.customId)
          ) ++ systemInstruction.map(content =>
            "systemInstruction" -> Json.parse(protoPrinter.print(content))
          )
        )

        Json.obj("request" -> requestJson).toString()
      }

      for {
        // validation is run here (inside the Future chain) rather than eagerly at the top of
        // this closure, so a failure surfaces as a failed Future - not a synchronous throw that
        // would escape .recover/retry adapters
        _ <- Future.fromTry(
          scala.util.Try {
            require(requests.nonEmpty, "At least one batch request expected.")
            // custom ids ride as GCP request labels, which are restricted to [a-z0-9_-]
            require(
              requests.forall(_.customId.matches("[a-z0-9_-]{1,63}")),
              "Vertex AI batch custom ids must match [a-z0-9_-]{1,63} (GCP label-value constraints)."
            )
          }
        )

        _ <- gcsStorage.upload(support.gcsBucket, inputObjectName, lines.mkString("\n"))

        job <- support.batchService.createBatchPredictionJob(
          CreateBatchPredictionJobSettings(
            displayName = "openai-scala-client chat-completion batch",
            model = settings.model,
            input = BatchJobInput.Gcs(s"gs://${support.gcsBucket}/$inputObjectName"),
            output = BatchJobOutput.Gcs(s"gs://${support.gcsBucket}/$batchDirName/output")
          )
        )
      } yield toBatchInfo(job)
    }

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    withBatchSupport(_.batchService.getBatchPredictionJob(batchId).map(toBatchInfo))

  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    withBatchSupport { support =>
      for {
        job <- support.batchService.getBatchPredictionJob(batchId)

        outputDirectory = job.outputInfo
          .flatMap(_.gcsOutputDirectory)
          .getOrElse(
            throw new OpenAIScalaClientException(
              s"Batch prediction job '$batchId' has no GCS output directory (state: ${job.state.map(_.toString).getOrElse("unknown")})."
            )
          )

        (bucket, prefix) = parseGsUri(outputDirectory)

        objectNames <- gcsStorage.listObjects(bucket, prefix)

        predictionFiles = objectNames.filter(name =>
          name.endsWith(".jsonl") && name.contains("prediction")
        )

        fileContents <- Future.traverse(predictionFiles)(gcsStorage.download(bucket, _))
      } yield fileContents
        .flatMap(_.split("\n").toSeq.map(_.trim).filter(_.nonEmpty))
        .map(Json.parse)
        .zipWithIndex
        .map((toBatchResultItem _).tupled)
    }

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    withBatchSupport { support =>
      for {
        _ <- support.batchService.cancelBatchPredictionJob(batchId)
        job <- support.batchService.getBatchPredictionJob(batchId)
      } yield toBatchInfo(job)
    }

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] =
    withBatchSupport { support =>
      for {
        job <- support.batchService.getBatchPredictionJob(batchId)

        // a running job still owns its staged input/output objects - deleting them out from
        // under it would corrupt the job, so refuse unless it has reached a terminal state
        _ <-
          if (job.isTerminated)
            Future.successful(())
          else {
            val state = job.state.map(_.toString).getOrElse("unknown")
            Future.failed(
              new OpenAIScalaClientException(
                s"Batch prediction job '$batchId' is still running ('$state') - cancel it before deleting its files."
              )
            )
          }

        // the staged input file plus everything the job wrote under its output directory
        inputObjects = job.input.toSeq.collect { case BatchJobInput.Gcs(uris) => uris }.flatten

        outputObjects <- job.outputInfo.flatMap(_.gcsOutputDirectory) match {
          case Some(outputDirectory) =>
            val (bucket, prefix) = parseGsUri(outputDirectory)
            gcsStorage.listObjects(bucket, prefix).map(_.map(name => s"gs://$bucket/$name"))
          case None =>
            Future.successful(Nil)
        }

        // best-effort cleanup of the staged objects before deleting the job itself
        _ <- Future.traverse(inputObjects ++ outputObjects) { uri =>
          val (bucket, objectName) = parseGsUri(uri)
          gcsStorage.delete(bucket, objectName).recover { case e =>
            logger.warn(s"Failed to delete the staged batch object '$uri': ${e.getMessage}")
          }
        }

        _ <- support.batchService.deleteBatchPredictionJob(batchId)
      } yield ()
    }

  private def withBatchSupport[T](
    fun: VertexAIBatchSupport => Future[T]
  ): Future[T] =
    batchSupport
      .map(fun)
      .getOrElse(
        Future.failed(
          new OpenAIScalaClientException(
            "Vertex AI batch processing requires a Cloud Storage staging bucket - create the service via VertexAIServiceFactory.asOpenAIWithBatchSupport(...)."
          )
        )
      )

  private def toBatchInfo(job: BatchPredictionJob): ChatCompletionBatchInfo = {
    val status = job.state match {
      case Some(JobState.JOB_STATE_SUCCEEDED) | Some(JobState.JOB_STATE_PARTIALLY_SUCCEEDED) =>
        ChatCompletionBatchStatus.Completed
      case Some(JobState.JOB_STATE_FAILED)    => ChatCompletionBatchStatus.Failed
      case Some(JobState.JOB_STATE_CANCELLED) => ChatCompletionBatchStatus.Cancelled
      case Some(JobState.JOB_STATE_EXPIRED)   => ChatCompletionBatchStatus.Expired
      // queued, pending, running, cancelling, paused, updating, unspecified, or absent
      case _ => ChatCompletionBatchStatus.InProgress
    }

    ChatCompletionBatchInfo(
      job.name,
      status,
      job.state.map(_.toString).getOrElse("unknown")
    )
  }

  private def parseGsUri(uri: String): (String, String) = {
    val withoutScheme = uri.stripPrefix("gs://")
    val slashIndex = withoutScheme.indexOf('/')
    if (slashIndex < 0)
      (withoutScheme, "")
    else
      (withoutScheme.take(slashIndex), withoutScheme.drop(slashIndex + 1))
  }

  private def toBatchResultItem(
    json: JsValue,
    index: Int
  ): ChatCompletionBatchResultItem = {
    val customId =
      (json \ "request" \ "labels" \ customIdLabel).asOpt[String].getOrElse {
        logger.warn(
          s"Batch prediction result line carries no '$customIdLabel' label - falling back to the line index for correlation."
        )
        index.toString
      }

    val statusError = (json \ "status").asOpt[String].filter(_.nonEmpty)

    val result = statusError match {
      case Some(error) =>
        Left(ChatCompletionBatchError(error))

      case None =>
        (json \ "response")
          .asOpt[JsObject]
          .map(response => Right(toOpenAIBatchResponse(response)))
          .getOrElse(
            Left(
              ChatCompletionBatchError(
                s"The batch prediction result line carries no response and no error status: $json"
              )
            )
          )
    }

    ChatCompletionBatchResultItem(customId, result)
  }

  // the response of a batch-prediction line is a REST-JSON GenerateContentResponse
  private def toOpenAIBatchResponse(responseJson: JsObject): ChatCompletionResponse = {
    val choices =
      (responseJson \ "candidates").asOpt[Seq[JsValue]].getOrElse(Nil).zipWithIndex.map {
        case (candidate, index) =>
          val text = (candidate \ "content" \ "parts")
            .asOpt[Seq[JsValue]]
            .getOrElse(Nil)
            .flatMap(part => (part \ "text").asOpt[String])
            .mkString("")

          ChatCompletionChoiceInfo(
            message = AssistantMessage(text),
            index = (candidate \ "index").asOpt[Int].getOrElse(index),
            finish_reason = (candidate \ "finishReason").asOpt[String],
            logprobs = None
          )
      }

    val usage = (responseJson \ "usageMetadata").asOpt[JsObject].map { usageJson =>
      val promptTokens = (usageJson \ "promptTokenCount").asOpt[Int].getOrElse(0)
      val completionTokens = (usageJson \ "candidatesTokenCount").asOpt[Int]
      UsageInfo(
        prompt_tokens = promptTokens,
        total_tokens = (usageJson \ "totalTokenCount")
          .asOpt[Int]
          .getOrElse(promptTokens + completionTokens.getOrElse(0)),
        completion_tokens = completionTokens,
        prompt_tokens_details = Some(
          PromptTokensDetails(
            cached_tokens = (usageJson \ "cachedContentTokenCount").asOpt[Int].getOrElse(0),
            audio_tokens = None
          )
        )
      )
    }

    ChatCompletionResponse(
      id = "vertexai-batch",
      created = new java.util.Date(),
      model = (responseJson \ "modelVersion").asOpt[String].getOrElse(""),
      system_fingerprint = None,
      choices = choices,
      usage = usage,
      originalResponse = None
    )
  }

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = {
    underlying.close()
    batchSupport.foreach(_.batchService.close())
  }
}
