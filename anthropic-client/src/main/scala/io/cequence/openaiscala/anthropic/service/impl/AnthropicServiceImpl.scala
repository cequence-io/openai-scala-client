package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.JsonUtil.JsonOps
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.JsonFormats
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.{ChatRole, Message, ToolSpec}
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicWSRequestHelper}
import io.cequence.openaiscala.service.OpenAIWSRequestHelper
import io.cequence.openaiscala.service.impl.OpenAIWSStreamRequestHelper
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Future

// TODO: Introduce Anthropic specific exception and handle error codes
// Shouldn't use OpenAIWSRequestHelper and OpenAIWSStreamRequestHelper
private[service] trait AnthropicServiceImpl
    extends AnthropicService
    with AnthropicWSRequestHelper
    with OpenAIWSStreamRequestHelper
    with JsonFormats {

  override protected type PEP = EndPoint
  override protected type PT = Param

  override def createMessage(
    messages: Seq[Message],
    systemPrompt: Option[String],
    settings: AnthropicCreateMessageSettings
  ): Future[CreateMessageResponse] =
    execPOST(
      EndPoint.messages,
      bodyParams = createBodyParamsForMessageCreation(messages, systemPrompt, settings, stream = false)
    ).map(
      _.asSafe[CreateMessageResponse]
    )

  override def createToolMessage(
    messages: Seq[Message],
    systemPrompt: Option[String],
    tools: Seq[ToolSpec],
    settings: AnthropicCreateMessageSettings
  ): Future[CreateMessageResponse] = {
    val coreParams = createBodyParamsForMessageCreation(messages, systemPrompt, settings, stream = false)
    val extraParams = jsonBodyParams(
      Param.tools -> Some(tools.map(Json.toJson(_)))
    )

    def isToolCall = tools.nonEmpty

    if (isToolCall)
      execBetaPOSTWithStatus(
        EndPoint.messages,
        bodyParams = coreParams ++ extraParams
      ).map(
        _.asSafe[CreateMessageResponse]
      )
    else
      execPOST(
        EndPoint.messages,
        bodyParams = coreParams ++ extraParams
      ).map(
        _.asSafe[CreateMessageResponse]
      )
  }

  // TODO: somewhere override handleErrorCodes
  // define Anthropic exceptions based on status codes

  override def createMessageStreamed(
    messages: Seq[Message],
    systemPrompt: Option[String],
    settings: AnthropicCreateMessageSettings
  ): Source[ContentBlockDelta, NotUsed] =
    execJsonStreamAux(
      EndPoint.messages,
      "POST",
      bodyParams = createBodyParamsForMessageCreation(messages, systemPrompt, settings, stream = true)
    ).map { (json: JsValue) =>
      (json \ "error").toOption.map { error =>
        throw new OpenAIScalaClientException(error.toString())
      }.getOrElse {
        val jsonType = (json \ "type").as[String]

        // TODO: for now, we return only ContentBlockDelta
        jsonType match {
          case "message_start"       => None // json.asSafe[CreateMessageChunkResponse]
          case "content_block_start" => None
          case "ping"                => None
          case "content_block_delta" => Some(json.asSafe[ContentBlockDelta])
          case "content_block_stop"  => None
          case "message_delta"       => None
          case "message_stop"        => None
          case _ => throw new OpenAIScalaClientException(s"Unknown message type: $jsonType")
        }
      }
    }.collect { case Some(delta) => delta }

  protected def createBodyParamsForMessageCreation(
    messages: Seq[Message],
    systemPrompt: Option[String],
    settings: AnthropicCreateMessageSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messages.nonEmpty, "At least one message expected.")
    assert(messages(0).role == ChatRole.User, "First message must be from user.")

    val messageJsons = messages.map(Json.toJson(_))

    jsonBodyParams(
      Param.messages -> Some(messageJsons),
      Param.model -> Some(settings.model),
      Param.system -> systemPrompt,
      Param.max_tokens -> Some(settings.max_tokens),
      Param.metadata -> { if (settings.metadata.isEmpty) None else Some(settings.metadata) },
      Param.stop_sequences -> {
        if (settings.stop_sequences.nonEmpty) Some(settings.stop_sequences) else None
      },
      Param.stream -> Some(stream),
      Param.temperature -> settings.temperature,
      Param.top_p -> settings.top_p,
      Param.top_k -> settings.top_k
    )
  }
}
