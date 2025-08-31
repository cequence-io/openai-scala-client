package io.cequence.openaiscala.domain.responsesapi

/**
 * Options for streaming responses.
 *
 * @param includeObfuscation
 *   When true, stream obfuscation will be enabled. Stream obfuscation adds random characters
 *   to an obfuscation field on streaming delta events to normalize payload sizes as a
 *   mitigation to certain side-channel attacks. These obfuscation fields are included by
 *   default, but add a small amount of overhead to the data stream. You can set
 *   include_obfuscation to false to optimize for bandwidth if you trust the network links
 *   between your application and the OpenAI API.
 */
case class StreamOptions(
  includeObfuscation: Option[Boolean] = None
)
