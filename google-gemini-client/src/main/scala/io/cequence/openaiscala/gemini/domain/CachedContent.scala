package io.cequence.openaiscala.gemini.domain

import io.cequence.openaiscala.gemini.domain.settings.ToolConfig

/**
 * The request body contains an instance of CachedContent.
 *
 * @param contents
 *   Optional. Input only. Immutable. The content to cache.
 * @param tools
 *   Optional. Input only. Immutable. A list of Tools the model may use to generate the next
 *   response.
 * @param expiration
 *   Specifies when this resource will expire. Can be either expireTime or ttl.
 *   - Timestamp in UTC of when this resource is considered expired. Uses RFC 3339 format.
 *   - New TTL for this resource, input only. A duration in seconds with up to nine fractional
 *     digits, ending with 's'.
 * @param name
 *   Optional. Identifier. The resource name referring to the cached content. Format:
 *   cachedContents/{id}.
 * @param displayName
 *   Optional. Immutable. The user-generated meaningful display name of the cached content.
 *   Maximum 128 Unicode characters.
 * @param model
 *   Required. Immutable. The name of the Model to use for cached content. Format:
 *   models/{model}.
 * @param systemInstruction
 *   Optional. Input only. Immutable. Developer set system instruction. Currently text only.
 * @param toolConfig
 *   Optional. Input only. Immutable. Tool config. This config is shared for all tools.
 */
case class CachedContent(
  contents: Seq[Content] = Nil,
  tools: Seq[Tool] = Nil,
  expireTime: Expiration = Expiration.TTL("300s"),
  name: Option[String] = None,
  displayName: Option[String] = None,
  model: String,
  systemInstruction: Option[Content] = None,
  toolConfig: Option[ToolConfig] = None
)

sealed trait Expiration

object Expiration {
  case class ExpireTime(value: String) extends Expiration
  case class TTL(value: String) extends Expiration
}
