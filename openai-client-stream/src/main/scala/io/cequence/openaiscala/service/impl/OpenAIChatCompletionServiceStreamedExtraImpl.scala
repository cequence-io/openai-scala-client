package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.domain.ChatCompletionTool
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.service.ChatChunks
import io.cequence.openaiscala.service.adapter.ChatCompletionSettingsConversions

import akka.NotUsed
import io.cequence.openaiscala.service.{
  ClassifiedStreamingWSClient,
  HandleOpenAIErrorCodes,
  StreamingConsts
}
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonFormats._
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.service.OpenAIChatCompletionStreamedServiceExtra
import io.cequence.wsclient.JsonUtil.JsonOps
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
    with ClassifiedStreamingWSClient
    with HandleOpenAIErrorCodes {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    unsupportedToolApprovalDecisions(settings)
      .orElse(
        ResponsesChatCompletionSettingsOps
          .unsupportedResponsesTools(
            settings,
            "The OpenAI-shaped createChatCompletionStreamed"
          )
          .map(Source.failed)
      )
      .getOrElse(
        execChunkStream(createBodyParamsForChatCompletion(messages, settings, stream = true))
      )

  // a run paused for approval lives on the Responses API - not reachable from here
  private def unsupportedToolApprovalDecisions(
    settings: CreateChatCompletionSettings
  ): Option[Source[Nothing, NotUsed]] =
    ToolApprovalSettingsOps
      .unsupportedDecisions(
        settings,
        "A chat-only streamed OpenAI service (chat completions API)"
      )
      .map(Source.failed)

  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] =
    if (settings.toolApprovalDecisions.nonEmpty)
      unsupportedToolApprovalDecisions(settings).getOrElse(Source.empty)
    else if (settings.responsesTools.nonEmpty)
      Source.failed(
        new OpenAIScalaClientException(
          "Responses-native tools (setResponsesTools) need the Responses API - use the full streamed OpenAIService (OpenAIServiceFactory.withStreaming()), which routes them there automatically."
        )
      )
    else if (
      tools.nonEmpty && ChatCompletionSettingsConversions.chatToolsUnsupported(settings.model)
    )
      Source.failed(
        new OpenAIScalaClientException(
          ChatCompletionBodyMaker.toolsUnsupportedMessage(settings.model)
        )
      )
    else if (tools.nonEmpty && chatToolsRequireResponsesAPI(settings.model, tools))
      Source.failed(
        new OpenAIScalaClientException(
          ChatCompletionBodyMaker.responsesOnlyToolsMessage(settings.model, tools) +
            " Use the full streamed OpenAIService (OpenAIServiceFactory.withStreaming()), which routes such typed tool streams through the Responses API automatically, " +
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
    execJsonStream(
      EndPoint.chat_completions,
      "POST",
      bodyParams = bodyParams,
      maxFrameLength = Some(OpenAIChatCompletionServiceStreamedExtraImpl.maxFrameLength)
    ).map { (json: JsValue) =>
      inBandStreamError(json).foreach(throw _)
      json.asSafe[ChatCompletionChunkResponse]
    }
}

object OpenAIChatCompletionServiceStreamedExtraImpl {
  // SSE frames of gateways / OpenAI-compatible providers (e.g. usage-only chunks with
  // large tool payloads) can exceed ws-client's 20 000-byte default
  val maxFrameLength: Int = StreamingConsts.DefaultMaxFrameLength
}
