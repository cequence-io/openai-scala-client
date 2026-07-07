package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.anthropic.JsonFormats._
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.TextBlock
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{
  SystemMessage => AnthropicSystemMessage,
  SystemMessageContent
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import play.api.libs.json.{JsObject, JsString, JsValue, Json, Writes}

/**
 * Builds the Bedrock InvokeModel body (a batch record's `modelInput`) from a unified batch
 * request's messages/settings. Mirrors [[Anthropic.createBodyParamsForMessageCreation]] (with
 * `ignoreModel`/`ignoreOutputFormat` = true, plus `anthropic_version`) for the fields
 * reachable through the unified batch API: messages, system (incl. `cache_control`),
 * `max_tokens`, `temperature`, `top_p`, `top_k`, `stop_sequences`, `metadata`, `thinking`.
 *
 * Tools, JSON-schema output, and container are not supported here - the unified
 * [[io.cequence.openaiscala.domain.ChatCompletionBatchRequest]] doesn't carry them either, so
 * this is not a narrower feature set than the rest of the batch API.
 */
private[service] object BedrockBatchRequestBuilder {

  private val bedrockAnthropicVersion = "bedrock-2023-05-31"

  def modelInput(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): JsObject = {
    val (system, nonSystem) = messages.partition(_.isSystem)

    val messageJsons = nonSystem.map(Json.toJson(_))

    val systemJson: Option[JsValue] = system.headOption.map {
      case AnthropicSystemMessage(text, cacheControl) =>
        if (cacheControl.isEmpty) JsString(text)
        else
          Json.toJson(Seq(ContentBlockBase(TextBlock(text), cacheControl)))(
            Writes.seq(contentBlockBaseWrites)
          )
      case SystemMessageContent(blocks) =>
        Json.toJson(blocks)(Writes.seq(contentBlockBaseWrites))
      case other =>
        throw new IllegalArgumentException(s"Expected a system message, got: $other")
    }

    val fields: Seq[(String, JsValue)] = Seq(
      "anthropic_version" -> Some(JsString(bedrockAnthropicVersion)),
      "messages" -> Some(Json.toJson(messageJsons)),
      "system" -> systemJson,
      "max_tokens" -> Some(Json.toJson(settings.max_tokens)),
      "temperature" -> settings.temperature.map(Json.toJson(_)),
      "top_p" -> settings.top_p.map(Json.toJson(_)),
      "top_k" -> settings.top_k.map(Json.toJson(_)),
      "stop_sequences" ->
        (if (settings.stop_sequences.nonEmpty) Some(Json.toJson(settings.stop_sequences))
         else None),
      "metadata" ->
        (if (settings.metadata.isEmpty) None else Some(Json.toJson(settings.metadata))),
      "thinking" -> settings.thinking.map(Json.toJson(_)(thinkingSettingsFormat))
    ).collect { case (key, Some(value)) => key -> value }

    JsObject(fields)
  }
}
