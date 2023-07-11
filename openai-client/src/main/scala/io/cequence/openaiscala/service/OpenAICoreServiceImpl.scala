package io.cequence.openaiscala.service

import akka.stream.Materializer
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.json.{JsArray, JsNull, JsObject, JsValue, Json}
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.{BaseMessageSpec, FunMessageSpec, MessageSpec}
import io.cequence.openaiscala.service.ws.{Timeouts, WSRequestHelper}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Private impl. of [[OpenAICoreService]].
 *
 * @param ec
 * @param materializer
 *
 * @since July
 *   2023
 */
private trait OpenAICoreServiceImpl extends OpenAICoreService with WSRequestHelper {

  override protected type PEP = EndPoint
  override protected type PT = Param

  protected implicit val ec: ExecutionContext
  protected implicit val materializer: Materializer

  protected val explTimeouts: Option[Timeouts]
  protected val authHeaders: Seq[(String, String)]

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

  // auth

  override protected def getWSRequestOptional(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(PT, Option[Any])] = Nil
  ): StandaloneWSRequest#Self =
    super.getWSRequestOptional(endPoint, endPointParam, params).addHttpHeaders(authHeaders: _*)

  override protected def getWSRequest(
    endPoint: Option[PEP],
    endPointParam: Option[String],
    params: Seq[(PT, Any)] = Nil
  ): StandaloneWSRequest#Self =
    super.getWSRequest(endPoint, endPointParam, params).addHttpHeaders(authHeaders: _*)
}
