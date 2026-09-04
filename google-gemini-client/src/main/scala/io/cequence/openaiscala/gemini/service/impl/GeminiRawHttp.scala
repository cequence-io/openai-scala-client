package io.cequence.openaiscala.gemini.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException

import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets

/**
 * Small, testable helper for the raw (non-JSON-engine) HTTP calls used by the Gemini
 * file-upload/download paths. Sends the API key as the `x-goog-api-key` header rather than as
 * a `key=...` URL query parameter, so it never ends up in proxy/access logs or in exception
 * messages that echo the URL.
 */
object GeminiRawHttp {

  private val ApiKeyHeader = "x-goog-api-key"

  /**
   * Opens a [[HttpURLConnection]] to the given URL (left untouched - no key appended) with the
   * given HTTP method and the API key set as the `x-goog-api-key` request header.
   */
  def openConnection(
    url: String,
    method: String,
    apiKey: String
  ): HttpURLConnection = {
    val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestMethod(method)
    connection.setRequestProperty(ApiKeyHeader, apiKey)
    connection
  }

  /**
   * Reads the response body of a raw HTTP connection (error stream for a >= 400 status, input
   * stream otherwise), always closing the stream and disconnecting the connection afterwards.
   * Throws an [[OpenAIScalaClientException]] on a >= 400 status; the exception message carries
   * only the status, the operation label, and the response body - never the URL or the API
   * key.
   */
  def readResponse(
    connection: HttpURLConnection,
    operation: String
  ): String =
    try {
      val status = connection.getResponseCode
      val stream = if (status >= 400) connection.getErrorStream else connection.getInputStream

      val body =
        if (stream == null) ""
        else
          try {
            val buffer = new java.io.ByteArrayOutputStream()
            val chunk = new Array[Byte](8192)
            var read = stream.read(chunk)
            while (read != -1) {
              buffer.write(chunk, 0, read)
              read = stream.read(chunk)
            }
            buffer.toString(StandardCharsets.UTF_8.name())
          } finally stream.close()

      if (status >= 400)
        throw new OpenAIScalaClientException(
          s"Gemini $operation failed with the status $status: $body"
        )

      body
    } finally connection.disconnect()
}
