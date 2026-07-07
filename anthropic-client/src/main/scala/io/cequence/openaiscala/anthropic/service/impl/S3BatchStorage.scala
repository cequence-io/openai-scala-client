package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.EncryptionUtil.sha256Hash

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, blocking}

/**
 * Minimal S3 REST client used for staging Bedrock batch-inference inputs and reading batch
 * outputs. Every request is SigV4-signed via [[BedrockAuthHelper]] (bearer-token auth is not
 * supported here - S3 access always requires a real AWS access/secret key pair).
 */
private[service] class S3BatchStorage(
  connectionInfo: BedrockConnectionSettings,
  s3Region: String
)(
  implicit ec: ExecutionContext
) extends BedrockAuthHelper {

  private def host(bucket: String): String = s"$bucket.s3.$s3Region.amazonaws.com"

  def upload(
    bucket: String,
    key: String,
    content: String
  ): Future[Unit] = Future {
    blocking {
      val body = content.getBytes(StandardCharsets.UTF_8)
      val url = new URL(s"https://${host(bucket)}/${encodePath(key)}")

      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("PUT")
      connection.setDoOutput(true)
      connection.setFixedLengthStreamingMode(body.length)
      connection.setRequestProperty("Content-Type", "application/octet-stream")
      sign("PUT", url, content, connection)

      val out = connection.getOutputStream
      try out.write(body)
      finally out.close()

      handleResponse(connection, s"upload of s3://$bucket/$key")
      ()
    }
  }

  def download(
    bucket: String,
    key: String
  ): Future[String] = Future {
    blocking {
      val url = new URL(s"https://${host(bucket)}/${encodePath(key)}")
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      sign("GET", url, "", connection)

      handleResponse(connection, s"download of s3://$bucket/$key")
    }
  }

  def delete(
    bucket: String,
    key: String
  ): Future[Unit] = Future {
    blocking {
      val url = new URL(s"https://${host(bucket)}/${encodePath(key)}")
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("DELETE")
      sign("DELETE", url, "", connection)

      handleResponse(connection, s"deletion of s3://$bucket/$key")
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
        continuationToken: Option[String],
        acc: Seq[String]
      ): Seq[String] = {
        val tokenParam = continuationToken
          .map(token => s"&continuation-token=${rfc3986Encode(token)}")
          .getOrElse("")
        val query = s"?list-type=2&prefix=${rfc3986Encode(prefix)}$tokenParam"
        val url = new URL(s"https://${host(bucket)}/$query")

        val connection = url.openConnection().asInstanceOf[HttpURLConnection]
        connection.setRequestMethod("GET")
        sign("GET", url, "", connection)

        val xml = handleResponse(connection, s"listing of s3://$bucket/$prefix")
        val (keys, nextToken) = parseListObjectsResponse(xml)

        nextToken match {
          case Some(token) => listPages(Some(token), acc ++ keys)
          case None        => acc ++ keys
        }
      }

      listPages(None, Nil)
    }
  }

  private def sign(
    method: String,
    url: URL,
    body: String,
    connection: HttpURLConnection
  ): Unit = {
    if (connectionInfo.accessKey.isEmpty || connectionInfo.secretKey.isEmpty)
      throw new OpenAIScalaClientException(
        "S3 staging requires SigV4 credentials (accessKey/secretKey) - bearer-token-only Bedrock auth cannot sign S3 requests."
      )

    val payloadHash = sha256Hash(body)

    val signedHeaders = addAuthHeaders(
      method,
      url.toString,
      headers = Map("x-amz-content-sha256" -> payloadHash),
      body = body,
      accessKey = connectionInfo.accessKey,
      secretKey = connectionInfo.secretKey,
      region = s3Region,
      service = "s3",
      sessionToken = connectionInfo.sessionToken
    )

    signedHeaders.foreach { case (name, value) => connection.setRequestProperty(name, value) }
  }

  // split("/") with no explicit limit drops trailing empty strings, so a key ending in "/"
  // (e.g. an S3 "folder placeholder" object) would silently lose that trailing slash here -
  // limit -1 preserves it.
  private def encodePath(key: String): String =
    key.split("/", -1).map(rfc3986Encode).mkString("/")

  private def parseListObjectsResponse(xml: String): (Seq[String], Option[String]) = {
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
    doc.getDocumentElement.normalize()

    val keyNodes = doc.getElementsByTagName("Key")
    val keys = (0 until keyNodes.getLength).map(i => keyNodes.item(i).getTextContent)

    val nextTokenNodes = doc.getElementsByTagName("NextContinuationToken")
    val nextToken =
      if (nextTokenNodes.getLength > 0) Some(nextTokenNodes.item(0).getTextContent) else None

    (keys, nextToken)
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
        s"S3 $operation failed with the status $status: $body"
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
}
