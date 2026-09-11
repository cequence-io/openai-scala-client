package io.cequence.openaiscala.service

object StreamingConsts {

  /**
   * Maximum size of one streamed frame (an SSE event / JSON array element). ws-client's
   * default of 20 000 bytes is too small for provider payloads such as Anthropic server-tool
   * result blocks, Gemini grounding metadata or gateway usage chunks.
   */
  val DefaultMaxFrameLength: Int = 1024 * 1024
}
