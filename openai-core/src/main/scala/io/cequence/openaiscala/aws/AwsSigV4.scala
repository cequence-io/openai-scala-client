package io.cequence.openaiscala.aws

import io.cequence.wsclient.EncryptionUtil._

import java.net.{URL, URLEncoder}
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import scala.collection.mutable

/**
 * AWS Signature Version 4 request signer.
 *
 * Service-agnostic: pass the AWS service name (e.g. `bedrock`, `sts`, `s3`) and region, and it
 * computes the canonical request, derives the signing key and returns the headers to send -
 * `Authorization: AWS4-HMAC-SHA256 ...`, `X-Amz-Date`, `Host`, and `X-Amz-Security-Token` when
 * temporary credentials are used.
 *
 * `now` is a parameter purely so the known-answer tests can pin a timestamp; production
 * callers should leave the default.
 *
 * Lives in `openai-core` so that both the Anthropic Bedrock client (via the
 * `BedrockAuthHelper` shim, which delegates here) and the OpenAI-compatible Bedrock path (via
 * [[SigningWSClientEngine]]) share one implementation.
 */
object AwsSigV4 {

  private val SignaturePrefix = "AWS4-HMAC-SHA256"

  private val amzDateFormat =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

  private val dateStampFormat =
    DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

  /**
   * The headers to attach to the request: the caller's `headers` plus `Host` (derived from
   * `url` when absent), `X-Amz-Date`, an optional `X-Amz-Security-Token` and `Authorization`.
   */
  def signedHeaders(
    method: String,
    url: String,
    headers: Map[String, String],
    body: String,
    accessKey: String,
    secretKey: String,
    region: String,
    service: String,
    sessionToken: Option[String] = None,
    now: Instant = Instant.now()
  ): Map[String, String] = {
    val amzdate = amzDateFormat.format(now)
    val datestamp = dateStampFormat.format(now)

    val newHeaders = mutable.Map(headers.toSeq: _*)

    newHeaders += ("X-Amz-Date" -> amzdate)

    // an STS session token must participate in the signed headers (a SigV4 requirement when
    // using temporary credentials), so it is added BEFORE canonicalization
    sessionToken.foreach(token => newHeaders += ("X-Amz-Security-Token" -> token))

    val payloadHash = sha256Hash(body)

    val (canonicalRequest, signedHeadersStr) =
      createCanonicalRequest(method, url, newHeaders, payloadHash)

    val (stringToSign, credentialScope) =
      createStringToSign(canonicalRequest, datestamp, amzdate, region, service)

    val signature = calculateSignature(secretKey, datestamp, region, service, stringToSign)

    newHeaders += ("Authorization" ->
      s"$SignaturePrefix Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeadersStr, Signature=$signature")

    newHeaders.toMap
  }

  /**
   * RFC 3986 percent-encoding as required by SigV4 canonicalization - `URLEncoder` alone emits
   * form-encoding ('+' for space, bare '*', '%7E' for '~') which AWS re-canonicalizes
   * differently, breaking the signature.
   */
  def rfc3986Encode(value: String): String =
    URLEncoder
      .encode(value, "UTF-8")
      .replace("+", "%20")
      .replace("*", "%2A")
      .replace("%7E", "~")

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
    hmacSHA256(kSigning, stringToSign).map("%02x".format(_)).mkString
  }

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
    normalizedPath.replace(":", "%3A")
  }

  /**
   * `getQuery` returns the query string exactly as written in the URL - already
   * percent-encoded, since callers build it via `URLEncoder.encode(...)` before constructing
   * the URL (`java.net.URL` does not decode). Re-encoding here would double-encode it (e.g.
   * "%2F" becoming "%252F"), producing a canonical request that no longer matches what is sent
   * on the wire - so this only sorts the already-encoded pairs.
   */
  private def canonicalQueryString(url: String): String = {
    val parsedUrl = new URL(url)
    val query = parsedUrl.getQuery
    if (query == null || query.isEmpty) ""
    else {
      val queryParams = query
        .split("&")
        .toList
        .map { param =>
          param.split("=", 2) match {
            case Array(k, v) => (k, v)
            case Array(k)    => (k, "")
            case _           => ("", "")
          }
        }
        // SigV4 orders by name, then by value for repeated names
        .sortBy { case (k, v) => (k, v) }

      queryParams.map { case (k, v) => s"$k=$v" }.mkString("&")
    }
  }

  private def createCanonicalRequest(
    method: String,
    url: String,
    headers: mutable.Map[String, String],
    payloadHash: String
  ): (String, String) = {
    if (!headers.exists { case (k, _) => k.equalsIgnoreCase("host") }) {
      headers += ("Host" -> hostFromUrl(url))
    }

    val lowercaseHeaders = headers.map { case (k, v) => (k.toLowerCase, v.trim) }
    val sortedHeaderKeys = lowercaseHeaders.keys.toList.sorted

    val canonicalHeadersStr =
      sortedHeaderKeys.map(k => s"$k:${lowercaseHeaders(k)}").mkString("\n") + "\n"

    val signedHeadersStr = sortedHeaderKeys.mkString(";")

    val parsedUrl = new URL(url)
    val canonicalPath = normalizePath(parsedUrl.getPath)
    val canonicalQuery = canonicalQueryString(url)

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
