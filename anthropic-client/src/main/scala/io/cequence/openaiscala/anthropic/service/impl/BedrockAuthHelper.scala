package io.cequence.openaiscala.anthropic.service.impl

import java.net.{URL, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.collection.mutable
import io.cequence.wsclient.EncryptionUtil._

trait BedrockAuthHelper {

  private val SignaturePrefix = "AWS4-HMAC-SHA256"

  protected def addAuthHeaders(
    method: String,
    url: String,
    headers: Map[String, String],
    body: String,
    accessKey: String,
    secretKey: String,
    region: String,
    service: String
  ): Map[String, String] = {
    // ISO 8601 format for date/time and a short date
    val now = java.time.Instant.now()
    val amzdate = java.time.format.DateTimeFormatter
      .ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(java.time.ZoneOffset.UTC)
      .format(now)

    val datestamp =
      java.time.format.DateTimeFormatter
        .ofPattern("yyyyMMdd")
        .withZone(java.time.ZoneOffset.UTC)
        .format(now)

    val newHeaders = mutable.Map(headers.toSeq: _*)

    // Add required headers
    newHeaders += ("X-Amz-Date" -> amzdate)

    // Compute payload hash
    val payloadHash = sha256Hash(body)

    // Create canonical request
    val (canonicalRequest, signedHeadersStr) =
      createCanonicalRequest(method, url, newHeaders, payloadHash)

    // Create string to sign
    val (stringToSign, credentialScope) =
      createStringToSign(canonicalRequest, datestamp, amzdate, region, service)

    // Calculate the signature
    val signature = calculateSignature(secretKey, datestamp, region, service, stringToSign)

    // Create Authorization header
    val authorizationHeader =
      s"AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeadersStr, Signature=$signature"

    newHeaders += ("Authorization" -> authorizationHeader)

    newHeaders.toMap
  }

  private def createStringToSign(
    canonicalRequest: String,
    datestamp: String,
    amzdate: String,
    region: String,
    service: String
  ): (String, String) = {
    val credentialScope = s"$datestamp/$region/$service/aws4_request"
    val hash = sha256Hash(canonicalRequest)
    val stringToSign =
      s"""$SignaturePrefix
         |$amzdate
         |$credentialScope
         |$hash""".stripMargin
    (stringToSign, credentialScope)
  }

  private def calculateSignature(
    secretKey: String,
    datestamp: String,
    region: String,
    service: String,
    stringToSign: String
  ): String = {
    val kDate = hmacSHA256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), datestamp)
    val kRegion = hmacSHA256(kDate, region)
    val kService = hmacSHA256(kRegion, service)
    val kSigning = hmacSHA256(kService, "aws4_request")
    val signature = hmacSHA256(kSigning, stringToSign)
    signature.map("%02x".format(_)).mkString
  }

  // URL util
  private def hostFromUrl(url: String): String = {
    val parsedUrl = new URL(url)
    val scheme = parsedUrl.getProtocol
    val host = parsedUrl.getHost.toLowerCase
    val port = parsedUrl.getPort
    val defaultPort = scheme match {
      case "http"  => 80
      case "https" => 443
      case _       => -1
    }
    if (port != -1 && port != defaultPort) s"$host:$port" else host
  }

  private def normalizePath(path: String): String = {
    val normalizedPath = if (!path.startsWith("/")) "/" + path else path
    normalizedPath.replace(":", "%3A") // TODO: expand this to handle more special characters
  }

  private def canonicalQueryString(url: String): String = {
    val parsedUrl = new URL(url)
    val query = parsedUrl.getQuery
    if (query == null || query.isEmpty) {
      ""
    } else {
      val queryParams = query
        .split("&")
        .toList
        .map { param =>
          val Array(key, value) = param.split("=", 2) match {
            case Array(k, v) => Array(k, v)
            case Array(k)    => Array(k, "")
          }
          (
            URLEncoder.encode(key, "UTF-8").replace("+", "%20"),
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
          )
        }
        .sortBy(_._1)

      queryParams.map { case (k, v) => s"$k=$v" }.mkString("&")
    }
  }

  private def createCanonicalRequest(
    method: String,
    url: String,
    headers: mutable.Map[String, String],
    payloadHash: String
  ): (String, String) = {
    // Ensure 'host' header is present
    if (!headers.exists { case (k, _) => k.equalsIgnoreCase("host") }) {
      headers += ("Host" -> hostFromUrl(url))
    }

    // Lowercase header keys and trim values
    val lowercaseHeaders = headers.map { case (k, v) => (k.toLowerCase, v.trim) }
    val sortedHeaderKeys = lowercaseHeaders.keys.toList.sorted

    // Canonical headers
    val canonicalHeadersStr =
      sortedHeaderKeys.map(k => s"$k:${lowercaseHeaders(k)}").mkString("\n") + "\n"

    // Signed headers
    val signedHeadersStr = sortedHeaderKeys.mkString(";")

    // Path and query
    val parsedUrl = new URL(url)
    val canonicalPath = normalizePath(parsedUrl.getPath)
    val canonicalQuery = canonicalQueryString(url)

    // Build canonical request
    val canonicalRequest =
      s"""${method.toUpperCase}
         |$canonicalPath
         |$canonicalQuery
         |$canonicalHeadersStr
         |$signedHeadersStr
         |$payloadHash""".stripMargin

    (canonicalRequest, signedHeadersStr)
  }
}
