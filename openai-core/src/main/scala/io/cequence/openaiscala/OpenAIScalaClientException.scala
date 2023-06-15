package io.cequence.openaiscala

class OpenAIScalaClientException(message: String, cause: Throwable) extends RuntimeException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaClientTimeoutException(message: String, cause: Throwable) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaClientUnknownHostException(message: String, cause: Throwable) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}

class OpenAIScalaTokenCountExceededException(message: String, cause: Throwable) extends OpenAIScalaClientException(message, cause) {
  def this(message: String) = this(message, null)
}