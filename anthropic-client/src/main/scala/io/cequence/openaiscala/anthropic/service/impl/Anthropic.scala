package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.JsonFormats
import io.cequence.openaiscala.anthropic.domain.{ChatRole, Content, Message}
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, SystemMessageContent}
import io.cequence.openaiscala.anthropic.domain.response.ContentBlockDelta
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, HandleAnthropicErrorCodes}
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithStreamEngine
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsString, JsValue, Json, Writes}
import com.typesafe.scalalogging.Logger
import io.cequence.wsclient.JsonUtil.JsonOps

trait Anthropic
    extends AnthropicService
    with WSClientWithStreamEngine
    with HandleAnthropicErrorCodes
    with JsonFormats {

  protected val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  protected val skillHeaders: Seq[(String, String)] = Seq(
    ("anthropic-beta", "skills-2025-10-02"),
//    ("anthropic-beta", "code-execution-2025-08-25"),
//    ("anthropic-beta", "files-api-2025-04-14")
  )

  protected def createBodyParamsForMessageCreation(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings,
    stream: Option[Boolean],
    ignoreModel: Boolean = false
  ): Seq[(Param, Option[JsValue])] = {
    assert(messages.nonEmpty, "At least one message expected.")

    val (system, nonSystem) = messages.partition(_.isSystem)

    assert(nonSystem.head.role == ChatRole.User, "First non-system message must be from user.")
    assert(
      system.size <= 1,
      "System message can be only 1. Use SystemMessageContent to include more content blocks."
    )

    val messageJsons = nonSystem.map(Json.toJson(_))

    val systemJson: Seq[JsValue] = system.map {
      case SystemMessage(text, cacheControl) =>
        if (cacheControl.isEmpty) JsString(text)
        else {
          val blocks =
            Seq(Content.ContentBlockBase(Content.ContentBlock.TextBlock(text), cacheControl))

          Json.toJson(blocks)(Writes.seq(contentBlockBaseWrites))
        }
      case SystemMessageContent(blocks) =>
        Json.toJson(blocks)(Writes.seq(contentBlockBaseWrites))
    }

    jsonBodyParams(
      Param.messages -> Some(messageJsons),
      Param.model -> (if (ignoreModel) None else Some(settings.model)),
      Param.system -> {
        if (system.isEmpty) None
        else Some(systemJson.head)
      },
      Param.max_tokens -> Some(settings.max_tokens),
      Param.metadata -> { if (settings.metadata.isEmpty) None else Some(settings.metadata) },
      Param.stop_sequences -> {
        if (settings.stop_sequences.nonEmpty) Some(settings.stop_sequences) else None
      },
      Param.stream -> stream,
      Param.temperature -> settings.temperature,
      Param.top_p -> settings.top_p,
      Param.top_k -> settings.top_k,
      Param.thinking -> settings.thinking.map(Json.toJson(_)(thinkingSettingsFormat)),
      Param.container -> settings.container.map(Json.toJson(_)(containerFormat)),
      Param.tools -> {
        if (settings.tools.nonEmpty)
          Some(Json.toJson(settings.tools)(Writes.seq(toolWrites)))
        else
          None
      }
    )
  }

  protected def serializeStreamedJson(json: JsValue): Option[ContentBlockDelta] =
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
