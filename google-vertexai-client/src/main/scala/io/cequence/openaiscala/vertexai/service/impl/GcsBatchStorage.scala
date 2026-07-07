package io.cequence.openaiscala.vertexai.service.impl

import com.google.auth.oauth2.GoogleCredentials
import io.cequence.openaiscala.OpenAIScalaClientException
import play.api.libs.json.{JsValue, Json}

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.{HttpURLConnection, URL, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, blocking}

/**
 * Minimal Cloud Storage client (JSON API) used for staging batch-prediction inputs and reading
 * batch outputs. Authenticates with the given (auto-refreshed) Google credentials.
 */
private[service] class GcsBatchStorage(
  credentials: GoogleCredentials
)(
  implicit ec: ExecutionContext
) {

  private val storageBaseUrl = "https://storage.googleapis.com"

  def upload(
    bucket: String,
    objectName: String,
    content: String
  ): Future[Unit] = Future {
    blocking {
      val url = new URL(
        s"$storageBaseUrl/upload/storage/v1/b/${encode(bucket)}/o?uploadType=media&name=${encode(objectName)}"
      )
      val connection = open(url, "POST")
      connection.setDoOutput(true)
      connection.setRequestProperty("Content-Type", "application/octet-stream")

      val out = connection.getOutputStream
      try out.write(content.getBytes(StandardCharsets.UTF_8))
      finally out.close()

      handleResponse(connection, s"upload of gs://$bucket/$objectName")
      ()
    }
  }

  def listObjects(
    bucket: String,
    prefix: String
  ): Future[Seq[String]] = Future {
    blocking {
      @tailrec
      def listPages(
        pageToken: Option[String],
        acc: Seq[String]
      ): Seq[String] = {
        val tokenParam = pageToken.map(token => s"&pageToken=${encode(token)}").getOrElse("")
        val url = new URL(
          s"$storageBaseUrl/storage/v1/b/${encode(bucket)}/o?prefix=${encode(prefix)}$tokenParam"
        )
        val connection = open(url, "GET")
        val json = Json.parse(handleResponse(connection, s"listing of gs://$bucket/$prefix"))

        val names = (json \ "items")
          .asOpt[Seq[JsValue]]
          .getOrElse(Nil)
          .flatMap(item => (item \ "name").asOpt[String])

        (json \ "nextPageToken").asOpt[String] match {
          case Some(nextToken) => listPages(Some(nextToken), acc ++ names)
          case None            => acc ++ names
        }
      }

      listPages(None, Nil)
    }
  }

  def download(
    bucket: String,
    objectName: String
  ): Future[String] = Future {
    blocking {
      val url = new URL(
        s"$storageBaseUrl/storage/v1/b/${encode(bucket)}/o/${encode(objectName)}?alt=media"
      )
      val connection = open(url, "GET")
      handleResponse(connection, s"download of gs://$bucket/$objectName")
    }
  }

  def delete(
    bucket: String,
    objectName: String
  ): Future[Unit] = Future {
    blocking {
      val url = new URL(
        s"$storageBaseUrl/storage/v1/b/${encode(bucket)}/o/${encode(objectName)}"
      )
      val connection = open(url, "DELETE")
      handleResponse(connection, s"deletion of gs://$bucket/$objectName")
      ()
    }
  }

  private def open(
    url: URL,
    method: String
  ): HttpURLConnection = {
    credentials.refreshIfExpired()
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]
    connection.setRequestMethod(method)
    connection.setRequestProperty(
      "Authorization",
      s"Bearer ${credentials.getAccessToken.getTokenValue}"
    )
    connection
  }

  private def handleResponse(
    connection: HttpURLConnection,
    operation: String
  ): String = {
    val status = connection.getResponseCode
    val body = readFully(
      if (status >= 400) connection.getErrorStream else connection.getInputStream
    )

    if (status >= 400)
      throw new OpenAIScalaClientException(
        s"Cloud Storage $operation failed with the status $status: $body"
      )

    body
  }

  private def readFully(stream: InputStream): String =
    if (stream == null) ""
    else {
      val buffer = new ByteArrayOutputStream()
      val chunk = new Array[Byte](8192)
      var read = stream.read(chunk)
      while (read != -1) {
        buffer.write(chunk, 0, read)
        read = stream.read(chunk)
      }
      stream.close()
      buffer.toString(StandardCharsets.UTF_8.name())
    }

  // URLEncoder is form-encoding and emits '+' for space, but a literal '+' in a URL path means
  // plus, not space - replacing it with %20 keeps path usages correct (and is harmless in query)
  private def encode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
