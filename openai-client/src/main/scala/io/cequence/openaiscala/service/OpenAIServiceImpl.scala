package io.cequence.openaiscala.service

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.{
  AssistantTool,
  BaseMessage,
  ChatRole,
  FileId,
  FunctionSpec,
  SortOrder,
  Thread,
  ThreadFullMessage,
  ThreadMessage,
  ThreadMessageFile,
  ToolSpec
}

import java.io.File
import scala.concurrent.Future

/**
 * Private impl. of [[OpenAIService]].
 *
 * @since Jan
 *   2023
 */
private trait OpenAIServiceImpl extends OpenAICoreServiceImpl with OpenAIService {

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

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ToolSpec],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatFunCompletion
  ): Future[ChatToolCompletionResponse] = {
    val coreParams =
      createBodyParamsForChatCompletion(messages, settings, stream = false)

    val toolJsons = tools.map(
      _ match {
        case tool: FunctionSpec =>
          Map("type" -> "function", "function" -> Json.toJson(tool))
      }
    )

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

    def textFromJsonString(json: JsValue) =
      (json.asSafe[JsObject] \ "text").toOption
        .map(_.asSafe[String])
        .getOrElse(
          throw new OpenAIScalaClientException(
            s"The attribute 'text' is not present in the response: ${stringResponse}."
          )
        )

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
      (response.asSafe[JsObject] \ "data").toOption
        .map(_.asSafeArray[FileInfo])
        .getOrElse(
          throw new OpenAIScalaClientException(
            s"The attribute 'data' is not present in the response: ${response.toString()}."
          )
        )
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

  // TODO: Azure returns http code 204
  override def deleteFile(
    fileId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.files,
      endPointParam = Some(fileId)
    ).map(response =>
      handleNotFoundAndError(response)
        .map(jsResponse =>
          (jsResponse \ "deleted").toOption.map {
            _.asSafe[Boolean] match {
              case true  => DeleteResponse.Deleted
              case false => DeleteResponse.NotDeleted
            }
          }.getOrElse(
            throw new OpenAIScalaClientException(
              s"The attribute 'deleted' is not present in the response: ${response.toString()}."
            )
          )
        )
        .getOrElse(
          // we got a not-found http code (404)
          DeleteResponse.NotFound
        )
    )

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
  ): Future[Option[String]] = {
    val endPoint = EndPoint.files
    val endPointParam = Some(s"${fileId}/content")

    val request = getWSRequestOptional(Some(endPoint), endPointParam)

    execGETStringAux(request, Some(endPoint)).map(response => handleNotFoundAndError(response))
  }

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
        }
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
      (response.asSafe[JsObject] \ "data").toOption
        .map(_.asSafeArray[FineTuneJob])
        .getOrElse(
          throw new OpenAIScalaClientException(
            s"The attribute 'data' is not present in the response: ${response.toString()}."
          )
        )
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
        Param.limit -> limit,
        Param.stream -> Some(false) // TODO: is streaming still supported?
      )
    ).map { response =>
      handleNotFoundAndError(response).map(jsResponse =>
        (jsResponse.asSafe[JsObject] \ "data").toOption
          .map(_.asSafeArray[FineTuneEvent])
          .getOrElse(
            throw new OpenAIScalaClientException(
              s"The attribute 'data' is not present in the response: ${response.toString()}."
            )
          )
      )
    }

  override def deleteFineTuneModel(
    modelId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      EndPoint.models,
      endPointParam = Some(modelId)
    ).map(response =>
      handleNotFoundAndError(response)
        .map(jsResponse =>
          (jsResponse \ "deleted").toOption.map {
            _.asSafe[Boolean] match {
              case true  => DeleteResponse.Deleted
              case false => DeleteResponse.NotDeleted
            }
          }.getOrElse(
            throw new OpenAIScalaClientException(
              s"The attribute 'deleted' is not present in the response: ${response.toString()}."
            )
          )
        )
        .getOrElse(
          // we got a not-found http code (404)
          DeleteResponse.NotFound
        )
    )

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
    metadata: Map[String, String]
  ): Future[Thread] =
    execPOST(
      EndPoint.threads,
      bodyParams = jsonBodyParams(
        Param.messages -> (
          if (messages.nonEmpty)
            Some(messages.map(Json.toJson(_)(threadMessageFormat)))
          else None
        ),
        Param.metadata -> (
          if (metadata.nonEmpty)
            Some(metadata)
          else None
        )
      )
    ).map(
      _.asSafe[Thread]
    )

  override def retrieveThread(
    threadId: String
  ): Future[Option[Thread]] =
    execGETWithStatus(
      EndPoint.threads,
      Some(threadId)
    ).map { response =>
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
    ).map(response =>
      handleNotFoundAndError(response)
        .map(jsResponse =>
          (jsResponse \ "deleted").toOption.map {
            _.asSafe[Boolean] match {
              case true  => DeleteResponse.Deleted
              case false => DeleteResponse.NotDeleted
            }
          }.getOrElse(
            throw new OpenAIScalaClientException(
              s"The attribute 'deleted' is not present in the response: ${response.toString()}."
            )
          )
        )
        .getOrElse(
          // we got a not-found http code (404)
          DeleteResponse.NotFound
        )
    )

  override def createThreadMessage(
    threadId: String,
    content: String,
    role: ChatRole,
    fileIds: Seq[String] = Nil,
    metadata: Map[String, String] = Map()
  ): Future[ThreadFullMessage] =
    execPOST(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages"),
      bodyParams = jsonBodyParams(
        Param.role -> Some(role.toString),
        Param.content -> Some(content),
        Param.file_ids -> (
          if (fileIds.nonEmpty)
            Some(fileIds)
          else None
        ),
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
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    before: Option[String]
  ): Future[Seq[ThreadFullMessage]] =
    execGET(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages"),
      params = Seq(
        Param.limit -> limit,
        Param.order -> order,
        Param.after -> after,
        Param.before -> before
      )
    ).map { response =>
      (response.asSafe[JsObject] \ "data").toOption
        .map(_.asSafeArray[ThreadFullMessage])
        .getOrElse(
          throw new OpenAIScalaClientException(
            s"The attribute 'data' is not present in the response: ${response.toString()}."
          )
        )
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
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    before: Option[String]
  ): Future[Seq[ThreadMessageFile]] =
    execGET(
      EndPoint.threads,
      endPointParam = Some(s"$threadId/messages/$messageId/files"),
      params = Seq(
        Param.limit -> limit,
        Param.order -> order,
        Param.after -> after,
        Param.before -> before
      )
    ).map { response =>
      (response.asSafe[JsObject] \ "data").toOption
        .map(_.asSafeArray[ThreadMessageFile])
        .getOrElse(
          throw new OpenAIScalaClientException(
            s"The attribute 'data' is not present in the response: ${response.toString()}."
          )
        )
    }

  override def createAssistant(
    model: String,
    name: Option[String],
    description: Option[String],
    instructions: Option[String],
    tools: Seq[AssistantTool],
    fileIds: Seq[String],
    metadata: Map[String, String]
  ): Future[Assistant] = {
    execPOST(
      EndPoint.assistants,
      bodyParams = jsonBodyParams(
        Param.model -> Some(model),
        Param.name -> Some(name),
        Param.description -> Some(description),
        Param.instructions -> Some(instructions),
        Param.tools -> Some(Json.toJson(tools)),
        Param.file_ids -> (if (fileIds.nonEmpty) Some(fileIds) else None),
        Param.metadata -> (if (metadata.nonEmpty) Some(metadata) else None)
      )
    ).map(_.asSafe[Assistant])
  }

  override def createAssistantFile(
    assistantId: String,
    fileId: String
  ): Future[AssistantFile] = {
    execPOST(
      EndPoint.assistants,
      endPointParam = Some(s"$assistantId/files"),
      bodyParams = jsonBodyParams(
        Param.file_id -> Some(fileId)
      )
    ).map(_.asSafe[AssistantFile])
  }

  override def listAssistants(
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    before: Option[String]
  ): Future[Seq[Assistant]] = {
    execGET(
      EndPoint.assistants,
      params = Seq(
        Param.limit -> limit,
        Param.order -> order,
        Param.after -> after,
        Param.before -> before
      )
    ).map { response =>
      (response.asSafe[JsObject] \ "data").toOption
        .map(_.asSafeArray[Assistant])
        .getOrElse(
          throw new OpenAIScalaClientException(
            s"The attribute 'data' is not present in the response: ${response.toString()}."
          )
        )
    }
  }

  override def listAssistantFiles(
    assistantId: String,
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    before: Option[String]
  ): Future[Seq[AssistantFile]] =
    execGET(
      EndPoint.assistants,
      endPointParam = Some(s"$assistantId/files"),
      params = Seq(
        Param.limit -> limit,
        Param.order -> order,
        Param.after -> after,
        Param.before -> before
      )
    ).map { response =>
      (response.asSafe[JsObject] \ "data").toOption
        .map(_.asSafeArray[AssistantFile])
        .getOrElse(
          throw new OpenAIScalaClientException(
            s"The attribute 'data' is not present in the response: ${response.toString()}."
          )
        )
    }

  override def retrieveAssistant(assistantId: String): Future[Option[Assistant]] =
    execGETWithStatus(
      EndPoint.assistants,
      Some(assistantId)
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[Assistant])
    }

  /**
   * Retrieves an AssistantFile.
   *
   * @param assistantId
   * The ID of the assistant who the file belongs to.
   * @param fileId
   * The ID of the file we're getting.
   */
  override def retrieveAssistantFile(assistantId: String, fileId: String): Future[Option[AssistantFile]] =
    execGETWithStatus(
      EndPoint.assistants,
      Some(s"$assistantId/files/$fileId")
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[AssistantFile])
    }

}
