package io.cequence.openaiscala.service

import play.api.libs.json.{JsObject, JsValue, Json}
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.{FunMessageSpec, FunctionSpec}

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
    messages: Seq[FunMessageSpec],
    functions: Seq[FunctionSpec],
    responseFunctionName: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatFunCompletionResponse] = {
    val coreParams =
      createBodyParamsForChatCompletion(messages, settings, stream = false)

    val extraParams = jsonBodyParams(
      Param.functions -> Some(Json.toJson(functions)),
      Param.function_call -> responseFunctionName.map(name =>
        Map("name" -> name)
      ) // otherwise "auto" is used by default
    )

    execPOST(
      EndPoint.chat_completions,
      bodyParams = coreParams ++ extraParams
    ).map(
      _.asSafe[ChatFunCompletionResponse]
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
        Param.prompt -> Some(prompt),
        Param.n -> settings.n,
        Param.size -> settings.size.map(_.toString),
        Param.response_format -> settings.response_format.map(_.toString),
        Param.user -> settings.user
      )
    ).map(
      _.asSafe[ImageInfo]
    )

  override def createImageEdit(
    prompt: String,
    image: File,
    mask: Option[File] = None,
    settings: CreateImageSettings
  ): Future[ImageInfo] =
    execPOSTMultipart(
      EndPoint.images_edits,
      fileParams = Seq((Param.image, image, None)) ++ mask.map((Param.mask, _, None)),
      bodyParams = Seq(
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
    settings: CreateImageSettings
  ): Future[ImageInfo] =
    execPOSTMultipart(
      EndPoint.images_variations,
      fileParams = Seq((Param.image, image, None)),
      bodyParams = Seq(
        Param.n -> settings.n,
        Param.size -> settings.size.map(_.toString),
        Param.response_format -> settings.response_format.map(_.toString),
        Param.user -> settings.user
      )
    ).map(
      _.asSafe[ImageInfo]
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
        Param.hyperparameters -> settings.n_epochs.map(epochs =>
          Map(Param.n_epochs.toString -> epochs)
        )
      )
    ).map(
      _.asSafe[FineTuneJob]
    )

  override def listFineTunes: Future[Seq[FineTuneJob]] =
    execGET(EndPoint.fine_tunes).map { response =>
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
    fineTuneId: String
  ): Future[Option[Seq[FineTuneEvent]]] =
    execGETWithStatus(
      EndPoint.fine_tunes,
      endPointParam = Some(s"$fineTuneId/events"),
      params = Seq(
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
}
