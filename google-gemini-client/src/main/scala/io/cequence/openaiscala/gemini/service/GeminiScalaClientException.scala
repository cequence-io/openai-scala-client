package io.cequence.openaiscala.gemini.service

import io.cequence.wsclient.domain.CequenceWSException

class GeminiScalaClientException(
  message: String,
  cause: Throwable
) extends CequenceWSException(message, cause) {
  def this(message: String) = this(message, null)
}

class GeminiScalaClientTimeoutException(
  message: String,
  cause: Throwable
) extends GeminiScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class GeminiScalaClientUnknownHostException(
  message: String,
  cause: Throwable
) extends GeminiScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class GeminiScalaTokenCountExceededException(
  message: String,
  cause: Throwable
) extends GeminiScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class GeminiScalaUnauthorizedException(
  message: String,
  cause: Throwable
) extends GeminiScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class GeminiScalaNotFoundException(
  message: String,
  cause: Throwable
) extends GeminiScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class GeminiScalaRateLimitException(
  message: String,
  cause: Throwable
) extends GeminiScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class GeminiScalaServerErrorException(
  message: String,
  cause: Throwable
) extends GeminiScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class GeminiScalaEngineOverloadedException(
  message: String,
  cause: Throwable
) extends GeminiScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

/**
 * Gemini answered a request with a `Tool.McpServers` call it never ran - a `functionCall` part
 * with no `functionResponse` and no answer text. Gemini's MCP executor fails this way
 * transiently (the same request also surfaces as HTTP 500 / 503), so it is a server-side error
 * and, through the OpenAI adapter, [[io.cequence.openaiscala.Retryable]].
 */
class GeminiScalaMcpCallNotExecutedException(
  message: String,
  cause: Throwable
) extends GeminiScalaServerErrorException(message, cause) {
  def this(message: String) = this(message, null)
}
