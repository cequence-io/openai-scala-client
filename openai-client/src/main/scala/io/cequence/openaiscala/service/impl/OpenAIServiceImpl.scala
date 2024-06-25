package io.cequence.openaiscala.service.impl

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.Batch.BatchRow.buildBatchRows
import io.cequence.openaiscala.domain.Batch._
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.{HandleOpenAIErrorCodes, OpenAIService}
import io.cequence.wsclient.JsonUtil.JsonOps
import play.api.libs.json.{JsObject, JsValue, Json, Reads}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Private impl. of [[OpenAIService]].
 *
 * @since Jan
 *   2023
 */
private[service] trait OpenAIServiceImpl
    extends OpenAICoreServiceImpl
    with OpenAIService
    with HandleOpenAIErrorCodes { // TODO: should HandleOpenAIErrorCodes be here?

  override def retrieveModel(
    modelId: String
  ): Future[Option[ModelInfo]] =
    execGETWithStatus(
      EndPoint.models,
      Some(modelId)
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[ModelInfo])
    }

  override def createChatFunCompletion(
    messages: Seq[BaseMessage],
    functions: Seq[FunctionSpec],
    responseFunctionName: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatFunCompletionResponse] = {
    val coreParams =
      createBodyParamsForChatCompletion(messages, settings, stream = false)

    val extraParams = jsonBodyParams(
      Param.functions -> Some(functions.map(Json.toJson(_)(functionSpecFormat))),
      Param.function_call -> responseFunctionName.map(name =>
        Map("name" -> name)
      ) // otherwise "auto" is used by default (if functions are present)
    )

    execPOST(
      EndPoint.chat_completions,
      bodyParams = coreParams ++ extraParams
    ).map(
      _.asSafe[ChatFunCompletionResponse]
    )
  }

  override def createRun(
    threadId: String,
    assistantId: AssistantId,
    instructions: Option[String],
    additionalInstructions: Option[String],
    additionalMessages: Seq[BaseMessage],
    tools: Seq[ForcableTool],
    responseToolChoice: Option[RequiredAction] = None,
    settings: CreateRunSettings = DefaultSettings.CreateRun,
    stream: Boolean
  ): Future[Run] = {
    val coreParams = createBodyParamsForRun(settings, stream)

    val toolParam = toolParams(tools, responseToolChoice)

    val messageJsons = additionalMessages.map(Json.toJson(_)(messageWrites))

    val runParams = jsonBodyParams(
      Param.assistant_id -> Some(assistantId.id),
      Param.additional_instructions -> instructions,
      Param.additional_messages ->
        (if (messageJsons.nonEmpty) Some(messageJsons) else None)
    )

    (coreParams ++ toolParam ++ runParams).foreach((x: (Param, Option[JsValue])) =>
      println(x._1.toString + " -> " + x._2.toString)
    )

    execPOST(
      EndPoint.threads,
      Some(s"$threadId/runs"),
      bodyParams = coreParams ++ toolParam ++ runParams
    ).map(
      _.asSafe[Run]
    )
  }

  override def retrieveRun(
    threadId: String,
    runId: String
  ): Future[Option[Run]] =
    execGETWithStatus(
      EndPoint.threads,
      Some(s"$threadId/runs/$runId")
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[Run])
    }

  override def listRunSteps(
    threadId: String,
    runId: String,
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[RunStep]] =
    execGET(
      EndPoint.threads,
      Some(s"$threadId/runs/$runId/steps"),
      params = paginationParams(pagination) :+ Param.order -> order
    ).map { response =>
      readAttribute(response, "data").asSafeArray[RunStep]
    }

  private def toolParams(
    tools: Seq[ForcableTool],
    maybeResponseToolChoice: Option[RequiredAction]
  ): Seq[(Param, Option[JsValue])] = {
    val toolJsons = tools.map {
      case CodeInterpreterSpec => Map("type" -> "code_interpreter")
      case FileSearchSpec      => Map("type" -> "file_search")
      case tool: FunctionSpec  => Map("type" -> "function", "function" -> Json.toJson(tool))
    }

    val maybeToolChoiceParam = maybeResponseToolChoice.map {
      case RequiredAction.None                         => "none"
      case RequiredAction.Auto                         => "auto"
      case RequiredAction.Required                     => "required"
      case RequiredAction.EnforcedTool(FileSearchSpec) => Map("type" -> "file_search")
      case RequiredAction.EnforcedTool(CodeInterpreterSpec) =>
        Map("type" -> "code_interpreter")
      case RequiredAction.EnforcedTool(FunctionSpec(name, _, _)) =>
        Map("type" -> "function", "function" -> Map("name" -> name))
    }

    val extraParams = jsonBodyParams(
      Param.tools -> Some(toolJsons),
      Param.tool_choice -> maybeToolChoiceParam
    )

    extraParams
  }

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ToolSpec],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatFunCompletion
  ): Future[ChatToolCompletionResponse] = {
    val coreParams =
      createBodyParamsForChatCompletion(messages, settings, stream = false)

    val toolJsons: Seq[Map[String, Object]] = tools.map { case tool: FunctionSpec =>
      Map("type" -> "function", "function" -> Json.toJson(tool))
    }

    val extraParams = jsonBodyParams(
      Param.tools -> Some(toolJsons),
      Param.tool_choice -> responseToolChoice.map(name =>
        Map(
          "type" -> "function",
          "function" -> Map("name" -> name)
        )
      ) // otherwise "auto" is used by default (if tools are present)
    )

    execPOST(
      EndPoint.chat_completions,
      bodyParams = coreParams ++ extraParams
    ).map(
      _.asSafe[ChatToolCompletionResponse]
    )
  }

  override def createEdit(
    input: String,
    instruction: String,
    settings: CreateEditSettings
  ): Future[TextEditResponse] =
    execPOST(
      EndPoint.edits,
      bodyParams = jsonBodyParams(
        Param.model -> Some(settings.model),
        Param.input -> Some(input),
        Param.instruction -> Some(instruction),
        Param.n -> settings.n,
        Param.temperature -> settings.temperature,
        Param.top_p -> settings.top_p
      )
    ).map(
      _.asSafe[TextEditResponse]
    )

  override def createImage(
    prompt: String,
    settings: CreateImageSettings
  ): Future[ImageInfo] =
    execPOST(
      EndPoint.images_generations,
      bodyParams = jsonBodyParams(
        Param.model -> settings.model,
        Param.prompt -> Some(prompt),
        Param.n -> settings.n,
        Param.size -> settings.size.map(_.toString),
        Param.response_format -> settings.response_format.map(_.toString),
        Param.quality -> settings.quality.map(_.toString),
        Param.style -> settings.style.map(_.toString),
        Param.user -> settings.user
      )
    ).map(
      _.asSafe[ImageInfo]
    )

  override def createImageEdit(
    prompt: String,
    image: File,
    mask: Option[File] = None,
    settings: CreateImageEditSettings
  ): Future[ImageInfo] =
    execPOSTMultipart(
      EndPoint.images_edits,
      fileParams = Seq((Param.image, image, None)) ++ mask.map((Param.mask, _, None)),
      bodyParams = Seq(
        Param.model -> settings.model,
        Param.prompt -> Some(prompt),
        Param.n -> settings.n,
        Param.size -> settings.size.map(_.toString),
        Param.response_format -> settings.response_format.map(_.toString),
        Param.user -> settings.user
      )
    ).map(
      _.asSafe[ImageInfo]
    )

  override def createImageVariation(
    image: File,
    settings: CreateImageEditSettings
  ): Future[ImageInfo] =
    execPOSTMultipart(
      EndPoint.images_variations,
      fileParams = Seq((Param.image, image, None)),
      bodyParams = Seq(
        Param.model -> settings.model,
        Param.n -> settings.n,
        Param.size -> settings.size.map(_.toString),
        Param.response_format -> settings.response_format.map(_.toString),
        Param.user -> settings.user
      )
    ).map(
      _.asSafe[ImageInfo]
    )

  def createAudioSpeech(
    input: String,
    settings: CreateSpeechSettings = DefaultSettings.CreateSpeech
  ): Future[Source[ByteString, _]] =
    execPOSTSource(
      EndPoint.audio_speech,
      bodyParams = jsonBodyParams(
        Param.input -> Some(input),
        Param.model -> Some(settings.model),
        Param.voice -> Some(settings.voice.toString),
        Param.speed -> settings.speed,
        Param.response_format -> settings.response_format.map(_.toString)
      )
    )

  override def createAudioTranscription(
    file: File,
    prompt: Option[String],
    settings: CreateTranscriptionSettings
  ): Future[TranscriptResponse] =
    execPOSTMultipartWithStatusString(
      EndPoint.audio_transcriptions,
      fileParams = Seq((Param.file, file, None)),
      bodyParams = Seq(
        Param.prompt -> prompt,
        Param.model -> Some(settings.model),
        Param.response_format -> settings.response_format.map(_.toString),
        Param.temperature -> settings.temperature,
        Param.language -> settings.language
      )
    ).map(processAudioTranscriptResponse(settings.response_format))

  override def createAudioTranslation(
    file: File,
    prompt: Option[String],
    settings: CreateTranslationSettings
  ): Future[TranscriptResponse] =
    execPOSTMultipartWithStatusString(
      EndPoint.audio_translations,
      fileParams = Seq((Param.file, file, None)),
      bodyParams = Seq(
        Param.prompt -> prompt,
        Param.model -> Some(settings.model),
        Param.response_format -> settings.response_format.map(_.toString),
        Param.temperature -> settings.temperature
      )
    ).map(processAudioTranscriptResponse(settings.response_format))

  private def processAudioTranscriptResponse(
    responseFormat: Option[TranscriptResponseFormatType]
  )(
    stringRichResponse: RichStringResponse
  ) = {
    val stringResponse = handleErrorResponse(stringRichResponse)

    def textFromJsonString(json: JsValue) = readAttribute(json, "text").asSafe[String]

    val FormatType = TranscriptResponseFormatType

    responseFormat.getOrElse(FormatType.json) match {
      case FormatType.json =>
        val json = Json.parse(stringResponse)
        TranscriptResponse(textFromJsonString(json))

      case FormatType.verbose_json =>
        val json = Json.parse(stringResponse)
        TranscriptResponse(
          text = textFromJsonString(json),
          verboseJson = Some(Json.prettyPrint(json))
        )

      case FormatType.text | FormatType.srt | FormatType.vtt =>
        TranscriptResponse(stringResponse)
    }
  }

  override def listFiles: Future[Seq[FileInfo]] =
    execGET(EndPoint.files).map { response =>
      readAttribute(response, "data").asSafeArray[FileInfo]
    }

  override def uploadFile(
    file: File,
    displayFileName: Option[String],
    settings: UploadFileSettings
  ): Future[FileInfo] =
    execPOSTMultipart(
      EndPoint.files,
      fileParams = Seq((Param.file, file, displayFileName)),
      bodyParams = Seq(
        Param.purpose -> Some(settings.purpose)
      )
    ).map(
      _.asSafe[FileInfo]
    )

  private def readFile(file: File): Seq[String] = {
    val source = scala.io.Source.fromFile(file)
    val content = Try(source.mkString.split("\n"))
    content match {
      case Success(value) =>
        source.close()
        value
      case Failure(exception) =>
        source.close()
        throw new OpenAIScalaClientException(
          s"Error reading file ${file.getName}: ${exception.getMessage}"
        )
    }
  }

  override def uploadBatchFile(
    file: File,
    displayFileName: Option[String]
  ): Future[FileInfo] = {
    readFile(file)
    // parse the fileContent as Seq[BatchRow] solely for the purpose of validating its structure, OpenAIScalaClientException is thrown if the parsing fails

//    fileRows.map { row =>
//      Json.parse(row).asSafeArray[BatchRow]
//    }

    uploadFile(file, displayFileName, DefaultSettings.UploadBatchFile)
  }

  override def buildAndUploadBatchFile(
    model: String,
    requests: Seq[BatchRowBase],
    displayFileName: Option[String]
  ): Future[FileInfo] = {
    val fileContent = buildBatchRows(model, requests)
    val json = Json.toJson(fileContent).toString()
    val tempPath = Files.createTempFile("openai-batch-", ".jsonl")
    Files.write(tempPath, json.getBytes(StandardCharsets.UTF_8))
    val file = tempPath.toFile
    uploadBatchFile(file, displayFileName)
  }

  override def buildBatchFileContent(
    model: String,
    requests: Seq[BatchRowBase]
  ): Future[Seq[BatchRow]] =
    Future.successful(buildBatchRows(model, requests))

  // TODO: Azure returns http code 204
  override def deleteFile(
    fileId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.files,
      endPointParam = Some(fileId)
    ).map(handleDeleteEndpointResponse)

  override def retrieveFile(
    fileId: String
  ): Future[Option[FileInfo]] =
    execGETWithStatus(
      EndPoint.files,
      endPointParam = Some(fileId)
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[FileInfo])
    }

  // because the output type here is string we need to do bit of a manual request building and calling
  override def retrieveFileContent(
    fileId: String
  ): Future[Option[String]] =
    execGETWithStatusAux(
      responseConverter = ResponseConverters.string,
      endPoint = EndPoint.files,
      endPointParam = Some(s"${fileId}/content")
    ).map(response => handleNotFoundAndError(response))

  override def retrieveFileContentAsSource(
    fileId: String
  ): Future[Option[Source[ByteString, _]]] =
    execGETWithStatusAux(
      responseConverter = ResponseConverters.source,
      endPoint = EndPoint.files,
      endPointParam = Some(s"${fileId}/content")
    ).map(response => handleNotFoundAndError(response))

  override def createVectorStore(
    fileIds: Seq[String],
    name: Option[String],
    metadata: Map[String, Any]
  ): Future[VectorStore] =
    execPOST(
      EndPoint.vector_stores,
      bodyParams = jsonBodyParams(
        Param.file_ids -> (if (fileIds.nonEmpty) Some(fileIds) else None),
        Param.name -> name,
        Param.metadata -> (if (metadata.nonEmpty) Some(metadata) else None)
      )
    ).map(
      _.asSafe[VectorStore]
    )

  override def listVectorStores(
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[VectorStore]] =
    execGET(
      EndPoint.vector_stores,
      params = paginationParams(pagination) :+ Param.order -> order
    ).map { response =>
      readAttribute(response, "data").asSafeArray[VectorStore]
    }

  override def deleteVectorStore(
    vectorStoreId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.vector_stores,
      endPointParam = Some(vectorStoreId)
    ).map(handleDeleteEndpointResponse)

  override def createVectorStoreFile(
    vectorStoreId: String,
    fileId: String,
    chunkingStrategy: ChunkingStrategy = ChunkingStrategy.AutoChunkingStrategy
  ): Future[VectorStoreFile] =
    execPOST(
      EndPoint.vector_stores,
      endPointParam = Some(s"$vectorStoreId/files"),
      bodyParams = jsonBodyParams(
        Param.file_id -> Some(fileId),
        Param.chunking_strategy -> Some(Json.toJson(chunkingStrategy))
      )
    ).map(
      _.asSafe[VectorStoreFile]
    )

  override def listVectorStoreFiles(
    vectorStoreId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None,
    filter: Option[VectorStoreFileStatus] = None
  ): Future[Seq[VectorStoreFile]] =
    execGET(
      EndPoint.vector_stores,
      endPointParam = Some(s"$vectorStoreId/files"),
      params = paginationParams(pagination) :+
        Param.order -> order :+
        Param.filter -> filter
    ).map { response =>
      readAttribute(response, "data").asSafeArray[VectorStoreFile]
    }

  override def deleteVectorStoreFile(
    vectorStoreId: String,
    fileId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.vector_stores,
      endPointParam = Some(s"$vectorStoreId/files/$fileId")
    ).map(handleDeleteEndpointResponse)

  override def createFineTune(
    training_file: String,
    validation_file: Option[String] = None,
    settings: CreateFineTuneSettings
  ): Future[FineTuneJob] =
    execPOST(
      EndPoint.fine_tunes,
      bodyParams = jsonBodyParams(
        Param.training_file -> Some(training_file),
        Param.validation_file -> validation_file,
        Param.model -> Some(settings.model),
        Param.suffix -> settings.suffix,
        Param.hyperparameters -> {
          if (
            Seq(settings.batch_size, settings.learning_rate_multiplier, settings.n_epochs)
              .exists(_.isDefined)
          ) {
            // all three params have "auto" as default value which we pass explicitly if not defined
            Some(
              Map(
                Param.batch_size.toString -> settings.batch_size.getOrElse("auto"),
                Param.learning_rate_multiplier.toString -> settings.learning_rate_multiplier
                  .getOrElse("auto"),
                Param.n_epochs.toString -> settings.n_epochs.getOrElse("auto")
              )
            )
          } else
            None
        },
        Param.integrations -> (if (settings.integrations.nonEmpty) Some(settings.integrations)
                               else None),
        Param.seed -> settings.seed
      )
    ).map(
      _.asSafe[FineTuneJob]
    )

  override def listFineTunes(
    after: Option[String] = None,
    limit: Option[Int] = None
  ): Future[Seq[FineTuneJob]] =
    execGET(
      EndPoint.fine_tunes,
      params = Seq(
        Param.after -> after,
        Param.limit -> limit
      )
    ).map { response =>
      readAttribute(response, "data").asSafeArray[FineTuneJob]
    }

  override def retrieveFineTune(
    fineTuneId: String
  ): Future[Option[FineTuneJob]] =
    execGETWithStatus(
      EndPoint.fine_tunes,
      endPointParam = Some(fineTuneId)
    ).map(response => handleNotFoundAndError(response).map(_.asSafe[FineTuneJob]))

  override def cancelFineTune(
    fineTuneId: String
  ): Future[Option[FineTuneJob]] =
    execPOSTWithStatus(
      EndPoint.fine_tunes,
      endPointParam = Some(s"$fineTuneId/cancel")
    ).map(response => handleNotFoundAndError(response).map(_.asSafe[FineTuneJob]))

  override def listFineTuneEvents(
    fineTuneId: String,
    after: Option[String] = None,
    limit: Option[Int] = None
  ): Future[Option[Seq[FineTuneEvent]]] =
    execGETWithStatus(
      EndPoint.fine_tunes,
      endPointParam = Some(s"$fineTuneId/events"),
      params = Seq(
        Param.after -> after,
        Param.limit -> limit
      )
    ).map { response =>
      handleNotFoundAndError(response).map(jsResponse =>
        readAttribute(jsResponse, "data").asSafeArray[FineTuneEvent]
      )
    }

  override def listFineTuneCheckpoints(
    fineTuneId: String,
    after: Option[String],
    limit: Option[Int]
  ): Future[Option[Seq[FineTuneCheckpoint]]] =
    execGETWithStatus(
      EndPoint.fine_tunes,
      endPointParam = Some(s"$fineTuneId/checkpoints"),
      params = Seq(
        Param.after -> after,
        Param.limit -> limit
      )
    ).map { response =>
      handleNotFoundAndError(response).map(jsResponse =>
        readAttribute(jsResponse, "data").asSafeArray[FineTuneCheckpoint]
      )
    }

  override def deleteFineTuneModel(
    modelId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.models,
      endPointParam = Some(modelId)
    ).map(handleDeleteEndpointResponse)

  override def createModeration(
    input: String,
    settings: CreateModerationSettings
  ): Future[ModerationResponse] =
    execPOST(
      EndPoint.moderations,
      bodyParams = jsonBodyParams(
        Param.input -> Some(input),
        Param.model -> settings.model
      )
    ).map(
      _.asSafe[ModerationResponse]
    )

  override def createThread(
    messages: Seq[ThreadMessage],
    toolResources: Seq[AssistantToolResource] = Nil,
    metadata: Map[String, String]
  ): Future[Thread] = {
    val params = jsonBodyParams(
      Param.messages -> (
        if (messages.nonEmpty)
          Some(messages.map(Json.toJson(_)(threadMessageFormat)))
        else None
      ),
      Param.metadata -> (if (metadata.nonEmpty) Some(metadata) else None),
      Param.tool_resources -> (if (toolResources.nonEmpty)
                                 Some(Json.toJson(toolResources.head))
                               else None)
    )
    params.foreach(println)
    val t = execPOST(
      EndPoint.threads,
      bodyParams = params
    ).map(
      _.asSafe[Thread]
    )
    t
  }

  override def retrieveThread(
    threadId: String
  ): Future[Option[Thread]] =
    execGETWithStatus(
      EndPoint.threads,
      Some(threadId)
    ).map { response =>
      println(s"response: $response")
      handleNotFoundAndError(response).map(_.asSafe[Thread])
    }

  override def modifyThread(
    threadId: String,
    metadata: Map[String, String]
  ): Future[Option[Thread]] =
    execPOSTWithStatus(
      EndPoint.threads,
      endPointParam = Some(threadId),
      bodyParams = jsonBodyParams(
        Param.metadata -> (
          if (metadata.nonEmpty)
            Some(metadata)
          else None
        )
      )
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[Thread])
    }

  override def deleteThread(
    threadId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.threads,
      endPointParam = Some(threadId)
    ).map(handleDeleteEndpointResponse)

  override def createThreadMessage(
    threadId: String,
    content: String,
    role: ChatRole,
    attachments: Seq[Attachment] = Nil,
    metadata: Map[String, String] = Map()
  ): Future[ThreadFullMessage] =
    execPOST(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages"),
      bodyParams = jsonBodyParams(
        Param.role -> Some(role.toString),
        Param.content -> Some(content),
//        Param.attachments -> (
//          if (attachments.nonEmpty)
//            Some(Json.toJson(attachments))
//          else None
//        ),
        Param.metadata -> (
          if (metadata.nonEmpty)
            Some(metadata)
          else None
        )
      )
    ).map(
      _.asSafe[ThreadFullMessage]
    )

  override def retrieveThreadMessage(
    threadId: String,
    messageId: String
  ): Future[Option[ThreadFullMessage]] =
    execGETWithStatus(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages/$messageId")
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[ThreadFullMessage])
    }

  override def modifyThreadMessage(
    threadId: String,
    messageId: String,
    metadata: Map[String, String]
  ): Future[Option[ThreadFullMessage]] =
    execPOSTWithStatus(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages/$messageId"),
      bodyParams = jsonBodyParams(
        Param.metadata -> (
          if (metadata.nonEmpty)
            Some(metadata)
          else None
        )
      )
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[ThreadFullMessage])
    }

  override def listThreadMessages(
    threadId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder]
  ): Future[Seq[ThreadFullMessage]] =
    execGET(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages"),
      params = paginationParams(pagination) :+ Param.order -> order
    ).map { response =>
      readAttribute(response, "data").asSafeArray[ThreadFullMessage]
    }

  override def retrieveThreadMessageFile(
    threadId: String,
    messageId: String,
    fileId: String
  ): Future[Option[ThreadMessageFile]] =
    execGETWithStatus(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages/$messageId/files/$fileId")
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[ThreadMessageFile])
    }

  override def listThreadMessageFiles(
    threadId: String,
    messageId: String,
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[ThreadMessageFile]] =
    execGET(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages/$messageId/files"),
      params = paginationParams(pagination) :+ Param.order -> order
    ).map { response =>
      readAttribute(response, "data").asSafeArray[ThreadMessageFile]
    }

  override def createAssistant(
    model: String,
    name: Option[String],
    description: Option[String],
    instructions: Option[String],
    tools: Seq[AssistantTool],
    toolResources: Seq[AssistantToolResource] = Seq.empty[AssistantToolResource],
    metadata: Map[String, String]
  ): Future[Assistant] = {
    val toolResourcesJson =
      toolResources.map(Json.toJson(_).as[JsObject]).foldLeft(Json.obj()) { case (acc, json) =>
        acc.deepMerge(json)
      }

    execPOST(
      EndPoint.assistants,
      bodyParams = jsonBodyParams(
        Param.model -> Some(model),
        Param.name -> Some(name),
        Param.description -> Some(description),
        Param.instructions -> Some(instructions),
        Param.tools -> Some(Json.toJson(tools)),
        Param.tool_resources -> (if (toolResources.nonEmpty) Some(toolResourcesJson)
                                 else None),
        Param.metadata -> (if (metadata.nonEmpty) Some(metadata) else None)
      )
    ).map(
      _.asSafe[Assistant]
    )
  }

  override def listAssistants(
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder]
  ): Future[Seq[Assistant]] = {
    execGET(
      EndPoint.assistants,
      params = paginationParams(pagination) :+ Param.order -> order
    ).map { response =>
      readAttribute(response, "data").asSafeArray[Assistant]
    }
  }

  override def retrieveAssistant(assistantId: String): Future[Option[Assistant]] =
    execGETWithStatus(
      EndPoint.assistants,
      Some(assistantId)
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[Assistant])
    }

  override def modifyAssistant(
    assistantId: String,
    model: Option[String],
    name: Option[String],
    description: Option[String],
    instructions: Option[String],
    tools: Seq[AssistantTool],
    fileIds: Seq[String],
    metadata: Map[String, String]
  ): Future[Option[Assistant]] =
    execPOSTWithStatus(
      EndPoint.assistants,
      endPointParam = Some(assistantId),
      bodyParams = jsonBodyParams(
        Param.model -> model,
        Param.name -> name,
        Param.description -> description,
        Param.instructions -> instructions,
        Param.tools -> Some(Json.toJson(tools)),
        Param.file_ids -> (if (fileIds.nonEmpty) Some(fileIds) else None),
        Param.metadata -> (if (metadata.nonEmpty) Some(metadata) else None)
      )
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[Assistant])
    }

  override def deleteAssistant(assistantId: String): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.assistants,
      endPointParam = Some(assistantId)
    ).map(handleDeleteEndpointResponse)

  override def deleteAssistantFile(
    assistantId: String,
    fileId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.assistants,
      endPointParam = Some(s"$assistantId/files/$fileId")
    ).map(handleDeleteEndpointResponse)

  private def handleDeleteEndpointResponse(response: RichJsResponse): DeleteResponse = {
    handleNotFoundAndError(response)
      .map(jsResponse =>
        if (readAttribute(jsResponse, "deleted").asSafe[Boolean]) {
          DeleteResponse.Deleted
        } else {
          DeleteResponse.NotDeleted
        }
      )
      .getOrElse(
        // we got a not-found http code (404)
        DeleteResponse.NotFound
      )
  }

  private def readAttribute(
    json: JsValue,
    attribute: String
  ): JsValue =
    (json.asSafe[JsObject] \ attribute).toOption.getOrElse(
      throw new OpenAIScalaClientException(
        s"The attribute '$attribute' is not present in the response: ${json.toString()}."
      )
    )

  private def paginationParams(pagination: Pagination) =
    Seq(
      Param.limit -> pagination.limit,
      Param.after -> pagination.after,
      Param.before -> pagination.before
    )

  override def createBatch(
    inputFileId: String,
    endpoint: BatchEndpoint,
    completionWindow: CompletionWindow,
    metadata: Map[String, String]
  ): Future[Batch] =
    execPOST(
      EndPoint.batches,
      endPointParam = None,
      bodyParams = jsonBodyParams(
        Param.input_file_id -> Some(inputFileId),
        Param.endpoint -> Some(Json.toJson(endpoint.toString)),
        Param.completion_window -> Some(Json.toJson(completionWindow)),
        Param.metadata -> Some(metadata)
      )
    ).map(_.asSafe[Batch])

  override def retrieveBatch(batchId: String): Future[Option[Batch]] =
    asSafeJsonIfFound[Batch](
      execGETWithStatus(
        EndPoint.batches,
        Some(batchId)
      )
    )

  override def retrieveBatchFile(batchId: String): Future[Option[FileInfo]] =
    for {
      maybeBatch <- retrieveBatch(batchId)
      output_file_id <-
        (for {
          batch <- maybeBatch
          output_file_id <- batch.output_file_id
        } yield retrieveFile(output_file_id)).getOrElse(Future.successful(None))
    } yield output_file_id

  def retrieveBatchFileContent(batchId: String): Future[Option[String]] = // TODO: Content?
    for {
      maybeFileInfo <- retrieveBatchFile(batchId)
      maybeFile <- maybeFileInfo.map { fileInfo =>
        retrieveFileContent(fileInfo.id)
      }.getOrElse(Future.successful(None))
    } yield maybeFile

  override def retrieveBatchResponses(batchId: String): Future[Option[CreateBatchResponses]] =
    for {
      maybeFileInfo <- retrieveBatchFile(batchId)
      maybeFile <- maybeFileInfo.map { fileInfo =>
        retrieveFileContent(fileInfo.id)
      }.getOrElse(Future.successful(None))
    } yield maybeFile.map { file =>
      CreateBatchResponses(
        file.split("\n").map(Json.parse(_).as[CreateBatchResponse]).toSeq
      )
    }

  override def cancelBatch(batchId: String): Future[Option[Batch]] =
    asSafeJsonIfFound[Batch](
      execPOSTWithStatus(
        EndPoint.batches,
        endPointParam = Some(s"$batchId/cancel")
      )
    )

  override def listBatches(
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder]
  ): Future[Seq[Batch]] = {
    execGET(
      EndPoint.batches,
      params = paginationParams(pagination) :+ Param.order -> order
    ).map { response =>
      readAttribute(response, "data").asSafeArray[Batch]
    }
  }

  private def asSafeJsonIfFound[T: Reads](response: Future[RichJsResponse])
    : Future[Option[T]] =
    response.map { response =>
      handleNotFoundAndError(response).map(_.asSafe[T])
    }
}
