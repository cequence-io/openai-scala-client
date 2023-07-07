package io.cequence.openaiscala.service

import akka.stream.Materializer
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.json.{JsArray, JsNull, JsObject, JsValue, Json}
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.{
  BaseMessageSpec,
  FunMessageSpec,
  FunctionSpec,
  MessageSpec
}
import io.cequence.openaiscala.service.ws.{Timeouts, WSRequestHelper}

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

/**
 * Private impl. class of [[OpenAIService]].
 *
 * @param apiKey
 * @param orgId
 * @param ec
 * @param materializer
 *
 * @since Jan
 *   2023
 */
private class OpenAIServiceImpl(
  apiKey: String,
  orgId: Option[String] = None,
  explTimeouts: Option[Timeouts] = None
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer
) extends OpenAIService
    with WSRequestHelper {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override protected def timeouts: Timeouts =
    explTimeouts.getOrElse(
      Timeouts(
        requestTimeout = Some(defaultRequestTimeout),
        readTimeout = Some(defaultReadoutTimeout)
      )
    )

  override def listModels: Future[Seq[ModelInfo]] =
    execGET(EndPoint.models).map { response =>
      (response.asSafe[JsObject] \ "data").toOption.map {
        _.asSafeArray[ModelInfo]
      }.getOrElse(
        throw new OpenAIScalaClientException(
          s"The attribute 'data' is not present in the response: ${response.toString()}."
        )
      )
    }

  override def retrieveModel(
    modelId: String
  ): Future[Option[ModelInfo]] =
    execGETWithStatus(
      EndPoint.models,
      Some(modelId)
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[ModelInfo])
    }

  override def createCompletion(
    prompt: String,
    settings: CreateCompletionSettings
  ): Future[TextCompletionResponse] =
    execPOST(
      EndPoint.completions,
      bodyParams = createBodyParamsForCompletion(prompt, settings, stream = false)
    ).map(
      _.asSafe[TextCompletionResponse]
    )

  protected def createBodyParamsForCompletion(
    prompt: String,
    settings: CreateCompletionSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] =
    jsonBodyParams(
      Param.prompt -> Some(prompt),
      Param.model -> Some(settings.model),
      Param.suffix -> settings.suffix,
      Param.max_tokens -> settings.max_tokens,
      Param.temperature -> settings.temperature,
      Param.top_p -> settings.top_p,
      Param.n -> settings.n,
      Param.stream -> Some(stream),
      Param.logprobs -> settings.logprobs,
      Param.echo -> settings.echo,
      Param.stop -> {
        settings.stop.size match {
          case 0 => None
          case 1 => Some(settings.stop.head)
          case _ => Some(settings.stop)
        }
      },
      Param.presence_penalty -> settings.presence_penalty,
      Param.frequency_penalty -> settings.frequency_penalty,
      Param.best_of -> settings.best_of,
      Param.logit_bias -> {
        if (settings.logit_bias.isEmpty) None else Some(settings.logit_bias)
      },
      Param.user -> settings.user
    )

  override def createChatCompletion(
    messages: Seq[MessageSpec],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    execPOST(
      EndPoint.chat_completions,
      bodyParams = createBodyParamsForChatCompletion(messages, settings, stream = false)
    ).map(
      _.asSafe[ChatCompletionResponse]
    )

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

  protected def createBodyParamsForChatCompletion(
    messages: Seq[BaseMessageSpec],
    settings: CreateChatCompletionSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messages.nonEmpty, "At least one message expected.")
    val messageJsons = messages.map(_ match {
      case m: MessageSpec =>
        Json.toJson(m)(messageSpecFormat)
      case m: FunMessageSpec =>
        val json = Json.toJson(m)(funMessageSpecFormat)
        // if the content is empty, add a null value (expected by the API)
        m.content
          .map(_ => json)
          .getOrElse(
            json.as[JsObject].+("content" -> JsNull)
          )
    })

    jsonBodyParams(
      Param.messages -> Some(JsArray(messageJsons)),
      Param.model -> Some(settings.model),
      Param.temperature -> settings.temperature,
      Param.top_p -> settings.top_p,
      Param.n -> settings.n,
      Param.stream -> Some(stream),
      Param.stop -> {
        settings.stop.size match {
          case 0 => None
          case 1 => Some(settings.stop.head)
          case _ => Some(settings.stop)
        }
      },
      Param.max_tokens -> settings.max_tokens,
      Param.presence_penalty -> settings.presence_penalty,
      Param.frequency_penalty -> settings.frequency_penalty,
      Param.logit_bias -> {
        if (settings.logit_bias.isEmpty) None else Some(settings.logit_bias)
      },
      Param.user -> settings.user
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

  override def createEmbeddings(
    input: Seq[String],
    settings: CreateEmbeddingsSettings
  ): Future[EmbeddingResponse] =
    execPOST(
      EndPoint.embeddings,
      bodyParams = jsonBodyParams(
        Param.input -> {
          input.size match {
            case 0 => None
            case 1 => Some(input.head)
            case _ => Some(input)
          }
        },
        Param.model -> Some(settings.model),
        Param.user -> settings.user
      )
    ).map(
      _.asSafe[EmbeddingResponse]
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
        Param.model -> settings.model,
        Param.n_epochs -> settings.n_epochs,
        Param.batch_size -> settings.batch_size,
        Param.learning_rate_multiplier -> settings.learning_rate_multiplier,
        Param.prompt_loss_weight -> settings.prompt_loss_weight,
        Param.compute_classification_metrics -> settings.compute_classification_metrics,
        Param.classification_n_classes -> settings.classification_n_classes,
        Param.classification_positive_class -> settings.classification_positive_class,
        Param.classification_betas -> settings.classification_betas,
        Param.suffix -> settings.suffix
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
        Param.stream -> Some(false)
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

  // aux

  override protected def getWSRequestOptional(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(PT, Option[Any])] = Nil
  ): StandaloneWSRequest#Self =
    addHeaders(super.getWSRequestOptional(endPoint, endPointParam, params))

  override protected def getWSRequest(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(PT, Any)] = Nil
  ): StandaloneWSRequest#Self =
    addHeaders(super.getWSRequest(endPoint, endPointParam, params))

  private def addHeaders(request: StandaloneWSRequest) = {
    val orgIdHeader = orgId.map(("OpenAI-Organization", _))
    val headers = orgIdHeader ++: Seq(("Authorization", s"Bearer $apiKey"))

    request.addHttpHeaders(headers: _*)
  }
}

object OpenAIServiceFactory extends OpenAIServiceFactoryHelper[OpenAIService] {

  override def apply(
    apiKey: String,
    orgId: Option[String] = None,
    timeouts: Option[Timeouts] = None
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIService =
    new OpenAIServiceImpl(apiKey, orgId, timeouts)
}
