package io.cequence.openaiscala.anthropic.service

import io.cequence.wsclient.domain.CequenceWSException

class AnthropicScalaClientException(
  message: String,
  cause: Throwable
) extends CequenceWSException(message, cause) {
  def this(message: String) = this(message, null)
}

class AnthropicScalaClientTimeoutException(
  message: String,
  cause: Throwable
) extends AnthropicScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class AnthropicScalaClientUnknownHostException(
  message: String,
  cause: Throwable
) extends AnthropicScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

// TODO: no usage?
class AnthropicScalaTokenCountExceededException(
  message: String,
  cause: Throwable
) extends AnthropicScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class AnthropicScalaUnauthorizedException(
  message: String,
  cause: Throwable
) extends AnthropicScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class AnthropicScalaNotFoundException(
  message: String,
  cause: Throwable
) extends AnthropicScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class AnthropicScalaRateLimitException(
  message: String,
  cause: Throwable
) extends AnthropicScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class AnthropicScalaServerErrorException(
  message: String,
  cause: Throwable
) extends AnthropicScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class AnthropicScalaEngineOverloadedException(
  message: String,
  cause: Throwable
) extends AnthropicScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}
