package io.cequence.openaiscala.perplexity.service.impl

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.perplexity.domain.Message
import io.cequence.openaiscala.perplexity.domain.settings.SonarCreateChatCompletionSettings
import io.cequence.openaiscala.perplexity.JsonFormats._
import io.cequence.openaiscala.perplexity.domain.response.{
  SonarChatCompletionChunkResponse,
  SonarChatCompletionResponse
}
import io.cequence.openaiscala.perplexity.service.SonarService
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.domain.WsRequestContext
import io.cequence.wsclient.service.{WSClientEngine, WSClientEngineStreamExtra}
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithStreamEngine
import io.cequence.wsclient.service.ws.stream.PlayWSStreamClientEngine
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

private[service] class SonarServiceImpl(
  apiKey: String
)(
  override implicit val ec: ExecutionContext,
  implicit val materializer: Materializer
) extends SonarService
    with WSClientWithStreamEngine {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override protected val engine: WSClientEngine with WSClientEngineStreamExtra =
    PlayWSStreamClientEngine(
      coreUrl,
      WsRequestContext(authHeaders = Seq(("Authorization", s"Bearer ${apiKey}")))
    )

  override def createChatCompletion(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings
  ): Future[SonarChatCompletionResponse] =
    execPOST(
      EndPoint.chatCompletion,
      bodyParams = createBodyParamsForChatCompletion(messages, settings, stream = false)
    ).map(
      _.asSafeJson[SonarChatCompletionResponse]
    )

  override def createChatCompletionStreamed(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings
  ): Source[SonarChatCompletionChunkResponse, NotUsed] = {
    val bodyParams =
      createBodyParamsForChatCompletion(messages, settings, stream = true)
    val stringParams = paramTuplesToStrings(bodyParams)

    engine
      .execJsonStream(
        EndPoint.chatCompletion.toString(),
        "POST",
        bodyParams = stringParams,
        framingDelimiter = "\r\n\r\n"
      )
      .map { json =>
        (json \ "error").toOption.map { error =>
          throw new OpenAIScalaClientException(error.toString())
        }.getOrElse {
          json.asSafe[SonarChatCompletionChunkResponse]
        }
      }
  }

  private def createBodyParamsForChatCompletion(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messages.nonEmpty, "At least one message expected.")

    jsonBodyParams(
      Param.messages -> Some(Json.toJson(messages)),
      Param.model -> Some(settings.model),
      Param.frequency_penalty -> settings.frequency_penalty,
      Param.max_tokens -> settings.max_tokens,
      Param.presence_penalty -> settings.presence_penalty,
      Param.response_format -> settings.response_format.map(Json.toJson(_)),
      Param.return_images -> settings.return_images,
      Param.return_related_questions -> settings.return_related_questions,
      Param.search_domain_filter -> (if (settings.search_domain_filter.nonEmpty)
                                       Some(settings.search_domain_filter)
                                     else None),
      Param.search_recency_filter -> settings.search_recency_filter.map(_.toString()),
      Param.stream -> Some(stream),
      Param.temperature -> settings.temperature,
      Param.top_k -> settings.top_k,
      Param.top_p -> settings.top_p
    )
  }
}
