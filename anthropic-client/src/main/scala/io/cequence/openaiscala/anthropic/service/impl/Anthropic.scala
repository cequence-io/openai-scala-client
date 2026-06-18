package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.JsonFormats
import io.cequence.openaiscala.anthropic.domain.{ChatRole, Content, Message, OutputFormat}
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, SystemMessageContent}
import io.cequence.openaiscala.anthropic.domain.response.ContentBlockDelta
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.{AnthropicService, HandleAnthropicErrorCodes}
import io.cequence.wsclient.service.WSClientWithEngineStreamTypes.WSClientWithOutputStreamEngine
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsString, JsValue, Json, Writes}
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.anthropic.domain.OutputFormat.JsonSchemaFormat
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.wsclient.JsonUtil.JsonOps

trait Anthropic
    extends AnthropicService
    with WSClientWithOutputStreamEngine
    with HandleAnthropicErrorCodes
    with JsonFormats {

  protected val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  protected val skillHeaders: Seq[(String, String)] = Seq(
    ("anthropic-beta", "skills-2025-10-02")
//    ("anthropic-beta", "code-execution-2025-08-25"),
//    ("anthropic-beta", "files-api-2025-04-14")
  )

  // Managed Agents control-plane endpoints (agents, environments, sessions, deployments,
  // vaults, memory stores) require this beta header. These endpoints reject ANY other
  // anthropic-beta value, which is why feature betas are sent per-operation rather than globally.
  protected val managedAgentsHeaders: Seq[(String, String)] = Seq(
    ("anthropic-beta", "managed-agents-2026-04-01")
  )

  // Whether PDF / prompt-caching betas should be added to message calls (set by the factory).
  protected def withPdf: Boolean = false
  protected def withCache: Boolean = false

  // Beta features for message creation. Sent per-call (not as always-on auth headers) so the
  // managed-agents endpoints, which reject these values, are unaffected.
  protected def messageBetaHeaders: Seq[(String, String)] = {
    val base = Seq(
      "structured-outputs-2025-11-13",
      "output-128k-2025-02-19",
      "files-api-2025-04-14",
      "code-execution-2025-08-25",
      "mcp-client-2025-04-04",
      "web-fetch-2025-09-10",
      "context-1m-2025-08-07", // deprecated (April 30, 2026)
      "fast-mode-2026-02-01"
    )
    val pdf = if (withPdf) Seq("pdfs-2024-09-25") else Nil
    val cache = if (withCache) Seq("prompt-caching-2024-07-31") else Nil
    (base ++ pdf ++ cache).map(("anthropic-beta", _))
  }

  // Files API beta header, sent on file operations.
  protected val fileBetaHeaders: Seq[(String, String)] = Seq(
    ("anthropic-beta", "files-api-2025-04-14")
  )

  protected def createBodyParamsForMessageCreation(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings,
    stream: Option[Boolean],
    ignoreModel: Boolean = false,
    ignoreOutputFormat: Boolean = false
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

    val outputFormat: Option[OutputFormat] = settings.output_format.map {
      case format: JsonSchemaFormat =>
        format.copy(
          schema = JsonSchema.setAdditionalPropertiesToFalse(format.schema)
        )
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
      Param.thinking -> settings.thinking.map(
        Json.toJson(_)(thinkingSettingsFormat)
      ),
      Param.container -> settings.container.map(
        Json.toJson(_)(containerFormat)
      ),
      Param.tools -> {
        if (settings.tools.nonEmpty)
          Some(Json.toJson(settings.tools)(Writes.seq(toolWrites)))
        else
          None
      },
      Param.tool_choice -> settings.tool_choice.map(
        Json.toJson(_)(toolChoiceFormat)
      ),
      Param.mcp_servers -> {
        if (settings.mcp_servers.nonEmpty)
          Some(Json.toJson(settings.mcp_servers)(Writes.seq(mcpServerURLDefinitionWrites)))
        else
          None
      },
      Param.output_format -> (if (ignoreOutputFormat) None
                              else
                                outputFormat.map(
                                  Json.toJson(_)(outputFormatFormat)
                                )),
      Param.output_config -> settings.output_config.map(
        Json.toJson(_)(outputConfigFormat)
      ),
      Param.speed -> settings.speed.map(_.toString)
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
