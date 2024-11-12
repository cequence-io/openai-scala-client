package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.JsonFormats
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.{ChatRole, Content, Message}
import io.cequence.openaiscala.anthropic.service.{AnthropicService, HandleAnthropicErrorCodes}
import io.cequence.wsclient.JsonUtil.JsonOps
import io.cequence.wsclient.ResponseImplicits.JsonSafeOps
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithStreamEngine
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsString, JsValue, Json, Writes}

import scala.concurrent.Future

trait Anthropic
    extends AnthropicService
    with WSClientWithStreamEngine
    with HandleAnthropicErrorCodes
    with JsonFormats

private[service] trait AnthropicServiceImpl extends Anthropic {

  override protected type PEP = EndPoint
  override protected type PT = Param

  private val logger = LoggerFactory.getLogger("AnthropicServiceImpl")

  override def createMessage(
    messages: Seq[Message],
    system: Option[Content] = None,
    settings: AnthropicCreateMessageSettings
  ): Future[CreateMessageResponse] =
    execPOST(
      EndPoint.messages,
      bodyParams =
        createBodyParamsForMessageCreation(system, messages, settings, stream = false)
    ).map(
      _.asSafeJson[CreateMessageResponse]
    )

  override def createMessageStreamed(
    system: Option[Content],
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Source[ContentBlockDelta, NotUsed] =
    engine
      .execJsonStream(
        EndPoint.messages.toString(),
        "POST",
        bodyParams = paramTuplesToStrings(
          createBodyParamsForMessageCreation(system, messages, settings, stream = true)
        )
      )
      .map { (json: JsValue) =>
        (json \ "error").toOption.map { error =>
          logger.error(s"Error in streamed response: ${error.toString()}")
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
            case _ =>
              logger.error(s"Unknown message type: $jsonType")
              throw new OpenAIScalaClientException(s"Unknown message type: $jsonType")
          }
        }
      }
      .collect { case Some(delta) => delta }

  private def createBodyParamsForMessageCreation(
    system: Option[Content],
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings,
    stream: Boolean
  ): Seq[(Param, Option[JsValue])] = {
    assert(messages.nonEmpty, "At least one message expected.")
    assert(messages.head.role == ChatRole.User, "First message must be from user.")

    val messageJsons = messages.map(Json.toJson(_))

    val systemJson = system.map {
      case single @ Content.SingleString(text, cacheControl) =>
        if (cacheControl.isEmpty) JsString(text)
        else {
          val blocks =
            Seq(Content.ContentBlockBase(Content.ContentBlock.TextBlock(text), cacheControl))

          Json.toJson(blocks)(Writes.seq(contentBlockWrites))
        }
      case Content.ContentBlocks(blocks) =>
        Json.toJson(blocks)(Writes.seq(contentBlockWrites))
      case Content.ContentBlockBase(content, cacheControl) =>
        val blocks = Seq(Content.ContentBlockBase(content, cacheControl))
        Json.toJson(blocks)(Writes.seq(contentBlockWrites))
    }

    jsonBodyParams(
      Param.messages -> Some(messageJsons),
      Param.model -> Some(settings.model),
      Param.system -> Some(systemJson),
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
