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
