package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.domain.ChatCompletionTool
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.service.ChatChunks

import akka.NotUsed
import io.cequence.openaiscala.service.StreamingConsts
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.OpenAIChatCompletionStreamedServiceExtra
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.service.WSClientWithEngineStreamTypes.WSClientWithOutputStreamEngine
import play.api.libs.json.JsValue

/**
 * Private impl. class of [[OpenAIChatCompletionStreamedServiceExtra]] which offers chat
 * completion with streaming support.
 *
 * @since March
 *   2024
 */
private[service] trait OpenAIChatCompletionServiceStreamedExtraImpl
    extends OpenAIChatCompletionStreamedServiceExtra
    with ChatCompletionBodyMaker
    with WSClientWithOutputStreamEngine {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    execChunkStream(createBodyParamsForChatCompletion(messages, settings, stream = true))

  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] =
    if (tools.nonEmpty && chatToolsRequireResponsesAPI(settings.model))
      Source.failed(
        new OpenAIScalaClientException(
          s"${settings.model} model doesn't support function tools on the chat completions API (OpenAI: 'To use function tools, use /v1/responses'). " +
            "Use the full streamed OpenAIService (OpenAIServiceFactory.withStreaming()), which routes typed tool streams through the Responses API automatically, " +
            "or wrap a Responses-capable streamed service in OpenAIResponsesChatCompletionService."
        )
      )
    else {
      val settingsFinal =
        if (tools.nonEmpty) settingsForChatToolCompletion(settings) else settings

      val bodyParams =
        createBodyParamsForChatCompletion(messages, settingsFinal, stream = true) ++
          (if (tools.nonEmpty) createToolBodyParams(tools, responseToolChoice) else Nil) ++
          createStreamOptionsParams(settingsFinal)

      execChunkStream(bodyParams).via(ChatChunks.fromOpenAIChunks)
    }

  private def execChunkStream(
    bodyParams: Seq[(Param, Option[JsValue])]
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    engine
      .execJsonStream(
        site,
        EndPoint.chat_completions.toString(),
        "POST",
        bodyParams = paramTuplesToStrings(bodyParams),
        maxFrameLength = Some(OpenAIChatCompletionServiceStreamedExtraImpl.maxFrameLength)
      )
      .map { (json: JsValue) =>
        (json \ "error").toOption.map { error =>
          throw new OpenAIScalaClientException(error.toString())
        }.getOrElse(
          json.asSafe[ChatCompletionChunkResponse]
        )
      }
}

object OpenAIChatCompletionServiceStreamedExtraImpl {
  // SSE frames of gateways / OpenAI-compatible providers (e.g. usage-only chunks with
  // large tool payloads) can exceed ws-client's 20 000-byte default
  val maxFrameLength: Int = StreamingConsts.DefaultMaxFrameLength
}
