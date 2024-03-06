package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.{EndPoint, OpenAICoreService, Param}
import play.api.libs.json.{JsObject, JsValue}

import scala.concurrent.Future

/**
 * Private impl. of [[OpenAICoreService]].
 *
 * @since July
 *   2023
 */
private[service] trait OpenAICoreServiceImpl
    extends OpenAICoreService
    with OpenAIChatCompletionServiceImpl {

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
      Param.user -> settings.user,
      Param.seed -> settings.seed
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
        Param.encoding_format -> settings.encoding_format.map(_.toString),
        Param.user -> settings.user
      )
    ).map(
      _.asSafe[EmbeddingResponse]
    )
}
