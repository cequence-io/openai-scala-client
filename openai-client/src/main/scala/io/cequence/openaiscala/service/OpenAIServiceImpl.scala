package io.cequence.openaiscala.service

import akka.stream.Materializer
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.json.{JsNull, JsObject, JsValue}
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.JsonFormats._
import com.typesafe.config.{Config, ConfigFactory}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.ConfigImplicits._
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
 * @since Jan 2023
 */
private class OpenAIServiceImpl(
  apiKey: String,
  orgId: Option[String] = None,
  explTimeouts: Option[Timeouts] = None)(
  implicit val ec: ExecutionContext, val materializer: Materializer
) extends OpenAIService with WSRequestHelper {

  protected object Command extends Enumeration {
    val models = Value
    val completions = Value
    val edits = Value
    val images_generations = Value("images/generations")
    val images_edits = Value("images/edits")
    val images_variations = Value("images/variations")
    val embeddings = Value
    val files = Value
    val fine_tunes = Value("fine-tunes")
    val moderations = Value

    @Deprecated
    val engines = Value
  }

  protected object Tag extends Enumeration {
    val model, prompt, suffix, max_tokens, temperature, top_p, n, stream, logprobs, echo, stop,
    presence_penalty, frequency_penalty, best_of, logit_bias, user,
    input, image, mask, instruction, size, response_format, file, purpose, file_id,
    training_file, validation_file, n_epochs, batch_size, learning_rate_multiplier, prompt_loss_weight,
    compute_classification_metrics, classification_n_classes, classification_positive_class,
    classification_betas, fine_tune_id = Value
  }

  override protected type PEP = Command.type
  override protected type PT = Tag.type

//  private val logger = LoggerFactory.getLogger("OpenAIService")

  override protected def timeouts: Timeouts =
    explTimeouts.getOrElse(
      Timeouts(
        requestTimeout = Some(defaultRequestTimeout),
        readTimeout = Some(defaultReadoutTimeout)
      )
    )

  override def listModels: Future[Seq[ModelInfo]] =
    execGET(Command.models).map { response =>
      (response.asSafe[JsObject] \ "data").toOption.map {
        _.asSafeArray[ModelInfo]
      }.getOrElse(
        throw new OpenAIScalaClientException(s"The attribute 'data' is not present in the response: ${response.toString()}.")
      )
    }

  override def retrieveModel(
    modelId: String
  ): Future[Option[ModelInfo]] =
    execGETWithStatus(
      Command.models,
      Some(modelId)
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[ModelInfo])
    }

  override def retrieveModelVersions(
    modelId: String
  ): Future[Option[Seq[String]]] =
    execGETWithStatus(
      Command.models,
      Some(s"$modelId/versions")
    ).map { response =>
      handleNotFoundAndError(response).map { json =>
        json.asSafeArray[String]
      }
    }

  override def createCompletion(
    prompt: String,
    settings: CreateCompletionSettings
  ): Future[TextCompletionResponse] =
    execPOST(
      Command.completions,
      bodyParams = createBodyParamsForCompletion(prompt, settings, stream = false)
    ).map(
      _.asSafe[TextCompletionResponse]
    )

  protected def createBodyParamsForCompletion(
    prompt: String,
    settings: CreateCompletionSettings,
    stream: Boolean
  ) =
    jsonBodyParams(
      Tag.prompt -> Some(prompt),
      Tag.model -> Some(settings.model),
      Tag.suffix -> settings.suffix,
      Tag.max_tokens -> settings.max_tokens,
      Tag.temperature -> settings.temperature,
      Tag.top_p -> settings.top_p,
      Tag.n -> settings.n,
      Tag.stream -> Some(stream),
      Tag.logprobs -> settings.logprobs,
      Tag.echo -> settings.echo,
      Tag.stop -> {
        settings.stop.size match {
          case 0 => None
          case 1 => Some(settings.stop.head)
          case _ => Some(settings.stop)
        }
      },
      Tag.presence_penalty -> settings.presence_penalty,
      Tag.frequency_penalty -> settings.frequency_penalty,
      Tag.best_of -> settings.best_of,
      Tag.logit_bias -> {
        if (settings.logit_bias.isEmpty) None else Some(settings.logit_bias)
      },
      Tag.user -> settings.user
    )

  override def createEdit(
    input: String,
    instruction: String,
    settings: CreateEditSettings
  ): Future[TextEditResponse] =
    execPOST(
      Command.edits,
      bodyParams = jsonBodyParams(
        Tag.model -> Some(settings.model),
        Tag.input -> Some(input),
        Tag.instruction -> Some(instruction),
        Tag.n -> settings.n,
        Tag.temperature -> settings.temperature,
        Tag.top_p -> settings.top_p
      )
    ).map(
      _.asSafe[TextEditResponse]
    )

  override def createImage(
    prompt: String,
    settings: CreateImageSettings
  ): Future[ImageInfo] =
    execPOST(
      Command.images_generations,
      bodyParams = jsonBodyParams(
        Tag.prompt -> Some(prompt),
        Tag.n -> settings.n,
        Tag.size -> settings.size.map(_.toString),
        Tag.response_format -> settings.response_format.map(_.toString),
        Tag.user -> settings.user
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
      Command.images_edits,
      fileParams = Seq(Tag.image -> image) ++ mask.map((Tag.mask ->_)),
      bodyParams = Seq(
        Tag.prompt -> Some(prompt),
        Tag.n -> settings.n,
        Tag.size -> settings.size.map(_.toString),
        Tag.response_format -> settings.response_format.map(_.toString),
        Tag.user -> settings.user
      )
    ).map(
      _.asSafe[ImageInfo]
    )

  override def createImageVariation(
    image: File,
    settings: CreateImageSettings
  ): Future[ImageInfo] =
    execPOSTMultipart(
      Command.images_variations,
      fileParams = Seq(Tag.image -> image),
      bodyParams = Seq(
        Tag.n -> settings.n,
        Tag.size -> settings.size.map(_.toString),
        Tag.response_format -> settings.response_format.map(_.toString),
        Tag.user -> settings.user
      )
    ).map(
      _.asSafe[ImageInfo]
    )

  override def createEmbeddings(
    input: Seq[String],
    settings: CreateEmbeddingsSettings
  ): Future[EmbeddingResponse] =
    execPOST(
      Command.embeddings,
      bodyParams = jsonBodyParams(
        Tag.input -> {
          input.size match {
            case 0 => None
            case 1 => Some(input.head)
            case _ => Some(input)
          }
        },
        Tag.model -> Some(settings.model),
        Tag.user -> settings.user
      )
    ).map(
      _.asSafe[EmbeddingResponse]
    )

  override def listFiles: Future[Seq[FileInfo]] =
    execGET(Command.files).map { response =>
      (response.asSafe[JsObject] \ "data").toOption.map {
        _.asSafeArray[FileInfo]
      }.getOrElse(
        throw new OpenAIScalaClientException(s"The attribute 'data' is not present in the response: ${response.toString()}.")
      )
    }

  override def uploadFile(
    file: File,
    settings: UploadFileSettings = DefaultSettings.UploadFile
  ): Future[FileInfo] =
    execPOSTMultipart(
      Command.files,
      fileParams = Seq(Tag.file -> file),
      bodyParams = Seq(
        Tag.purpose -> Some(settings.purpose)
      )
    ).map(
      _.asSafe[FileInfo]
    )

  override def deleteFile(
    fileId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      Command.files,
      endPointParam = Some(fileId)
    ).map( response =>
      handleNotFoundAndError(response).map(jsResponse =>
        (jsResponse \ "deleted").toOption.map {
          _.asSafe[Boolean] match {
            case true => DeleteResponse.Deleted
            case false => DeleteResponse.NotDeleted
          }
        }.getOrElse(
          throw new OpenAIScalaClientException(s"The attribute 'deleted' is not present in the response: ${response.toString()}.")
        )
      ).getOrElse(
        // we got a not-found http code (404)
        DeleteResponse.NotFound
      )
    )

  override def retrieveFile(
    fileId: String
  ): Future[Option[FileInfo]] =
    execGETWithStatus(
      Command.files,
      endPointParam = Some(fileId)
    ).map { response =>
      handleNotFoundAndError(response).map(_.asSafe[FileInfo])
    }

  // because the output type here is string we need to do bit of a manual request building and calling
  override def retrieveFileContent(
    fileId: String
  ): Future[Option[String]] = {
    val endPoint = Command.files
    val endPointParam = Some(s"${fileId}/content")

    val request = getWSRequestOptional(Some(endPoint), endPointParam)

    execGETStringAux(request, Some(endPoint)).map(response =>
      handleNotFoundAndError(response)
    )
  }

  override def createFineTune(
    training_file: String,
    validation_file: Option[String] = None,
    settings: CreateFineTuneSettings
  ): Future[FineTuneJob] =
    execPOST(
      Command.fine_tunes,
      bodyParams = jsonBodyParams(
        Tag.training_file -> Some(training_file),
        Tag.validation_file -> validation_file,
        Tag.model -> settings.model,
        Tag.n_epochs -> settings.n_epochs,
        Tag.batch_size -> settings.batch_size,
        Tag.learning_rate_multiplier -> settings.learning_rate_multiplier,
        Tag.prompt_loss_weight -> settings.prompt_loss_weight,
        Tag.compute_classification_metrics -> settings.compute_classification_metrics,
        Tag.classification_n_classes -> settings.classification_n_classes,
        Tag.classification_positive_class -> settings.classification_positive_class,
        Tag.classification_betas -> settings.classification_betas,
        Tag.suffix -> settings.suffix
      )
    ).map(
      _.asSafe[FineTuneJob]
    )

  override def listFineTunes: Future[Seq[FineTuneJob]] =
    execGET(Command.fine_tunes).map { response =>
      (response.asSafe[JsObject] \ "data").toOption.map {
        _.asSafeArray[FineTuneJob]
      }.getOrElse(
        throw new OpenAIScalaClientException(s"The attribute 'data' is not present in the response: ${response.toString()}.")
      )
    }

  override def retrieveFineTune(
    fineTuneId: String
  ): Future[Option[FineTuneJob]] =
    execGETWithStatus(
      Command.fine_tunes,
      endPointParam = Some(fineTuneId)
    ).map(response =>
      handleNotFoundAndError(response).map(_.asSafe[FineTuneJob])
    )

  override def cancelFineTune(
    fineTuneId: String
  ): Future[Option[FineTuneJob]] =
    execPOSTWithStatus(
      Command.fine_tunes,
      endPointParam = Some(s"$fineTuneId/cancel")
    ).map(response =>
      handleNotFoundAndError(response).map(_.asSafe[FineTuneJob])
    )

  override def listFineTuneEvents(
    fineTuneId: String
  ): Future[Option[Seq[FineTuneEvent]]] =
    execGETWithStatus(
      Command.fine_tunes,
      endPointParam = Some(s"$fineTuneId/events"),
      params = Seq(
        Tag.stream -> Some(false)
      )
    ).map { response =>
      handleNotFoundAndError(response).map(jsResponse =>
        (jsResponse.asSafe[JsObject] \ "data").toOption.map {
          _.asSafeArray[FineTuneEvent]
        }.getOrElse(
          throw new OpenAIScalaClientException(s"The attribute 'data' is not present in the response: ${response.toString()}.")
        )
      )
    }

  override def deleteFineTuneModel(
    modelId: String
  ): Future[DeleteResponse] =
    execDELETEWithStatus(
      Command.models,
      endPointParam = Some(modelId)
    ).map( response =>
      handleNotFoundAndError(response).map(jsResponse =>
        (jsResponse \ "deleted").toOption.map {
          _.asSafe[Boolean] match {
            case true => DeleteResponse.Deleted
            case false => DeleteResponse.NotDeleted
          }
        }.getOrElse(
          throw new OpenAIScalaClientException(s"The attribute 'deleted' is not present in the response: ${response.toString()}.")
        )
      ).getOrElse(
        // we got a not-found http code (404)
        DeleteResponse.NotFound
      )
    )

  override def createModeration(
    input: String,
    settings: CreateModerationSettings = DefaultSettings.CreateModeration
  ): Future[ModerationResponse] =
    execPOST(
      Command.moderations,
      bodyParams = jsonBodyParams(
        Tag.input -> Some(input),
        Tag.model -> settings.model
      )
    ).map(
      _.asSafe[ModerationResponse]
    )

  // aux

  override protected def getWSRequestOptional(
    endPoint: Option[PEP#Value],
    endPointParam: Option[String],
    params: Seq[(PT#Value, Option[Any])] = Nil
  ) =
    addHeaders(super.getWSRequestOptional(endPoint, endPointParam, params))

  override protected def getWSRequest(
    endPoint: Option[PEP#Value],
    endPointParam: Option[String],
    params: Seq[(PT#Value, Any)] = Nil
  ) =
    addHeaders(super.getWSRequest(endPoint, endPointParam, params))

  private def addHeaders(request: StandaloneWSRequest) = {
    val orgIdHeader = orgId.map(("OpenAI-Organization", _))
    val headers = orgIdHeader ++: Seq(("Authorization", s"Bearer $apiKey"))

    request.addHttpHeaders(headers :_*)
  }
}

object OpenAIServiceFactory extends OpenAIServiceFactoryHelper[OpenAIService] {

  override def apply(
    apiKey: String,
    orgId: Option[String] = None,
    timeouts: Option[Timeouts] = None)(
    implicit ec: ExecutionContext, materializer: Materializer
  ): OpenAIService =
    new OpenAIServiceImpl(apiKey, orgId, timeouts)
}