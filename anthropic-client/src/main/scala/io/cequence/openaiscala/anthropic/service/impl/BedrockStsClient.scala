package io.cequence.openaiscala.anthropic.service.impl

import java.net.{HttpURLConnection, URL}
import java.nio.charset.StandardCharsets
import scala.io.Source

/**
 * Minimal STS client that mints temporary credentials from a long-lived IAM user access/secret
 * pair, using the SigV4 signer in [[BedrockAuthHelper]] (which is service-agnostic — we just
 * pass `service = "sts"`). Equivalent to: `aws sts get-session-token --duration-seconds <n>`
 *
 * No AWS SDK or shell dependency.
 */
object BedrockStsClient extends BedrockAuthHelper {

  // STS global endpoint resides in us-east-1; SigV4 must be signed with that region.
  private val DefaultStsEndpoint = "https://sts.amazonaws.com/"
  private val StsSigningRegion = "us-east-1"
  private val StsService = "sts"

  final case class StsCredentials(
    accessKeyId: String,
    secretAccessKey: String,
    sessionToken: String,
    expiration: String
  )

  /**
   * Calls STS:GetSessionToken and returns the temporary credentials triple. Throws on non-2xx
   * responses.
   *
   * @param endpoint
   *   override only for tests
   */
  def getSessionToken(
    accessKey: String,
    secretKey: String,
    durationSeconds: Int = 3600,
    endpoint: String = DefaultStsEndpoint
  ): StsCredentials = {
    val body =
      s"Action=GetSessionToken&Version=2011-06-15&DurationSeconds=$durationSeconds"

    val signedHeaders = addAuthHeaders(
      method = "POST",
      url = endpoint,
      headers = Map("Content-Type" -> "application/x-www-form-urlencoded"),
      body = body,
      accessKey = accessKey,
      secretKey = secretKey,
      region = StsSigningRegion,
      service = StsService
    )

    val conn = new URL(endpoint).openConnection().asInstanceOf[HttpURLConnection]
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(10000)
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    signedHeaders.foreach { case (k, v) => conn.setRequestProperty(k, v) }

    val out = conn.getOutputStream
    try out.write(body.getBytes(StandardCharsets.UTF_8))
    finally out.close()

    val status = conn.getResponseCode
    if (status < 200 || status >= 300) {
      val errBody =
        Option(conn.getErrorStream).map { in =>
          val src = Source.fromInputStream(in)
          try src.mkString
          finally src.close()
        }.getOrElse("")
      throw new RuntimeException(s"STS GetSessionToken failed (status $status): $errBody")
    }

    val xml = {
      val src = Source.fromInputStream(conn.getInputStream)
      try src.mkString
      finally src.close()
    }
    parseGetSessionTokenResponse(xml)
  }

  // Response shape (excerpt):
  //   <Credentials>
  //     <AccessKeyId>...</AccessKeyId>
  //     <SecretAccessKey>...</SecretAccessKey>
  //     <SessionToken>...</SessionToken>
  //     <Expiration>...</Expiration>
  //   </Credentials>
  private def parseGetSessionTokenResponse(xml: String): StsCredentials = {
    def extract(tag: String): String = {
      val re = s"<$tag>([^<]+)</$tag>".r
      re.findFirstMatchIn(xml)
        .map(_.group(1))
        .getOrElse(
          throw new RuntimeException(
            s"STS response missing <$tag>. Response body: $xml"
          )
        )
    }
    StsCredentials(
      accessKeyId = extract("AccessKeyId"),
      secretAccessKey = extract("SecretAccessKey"),
      sessionToken = extract("SessionToken"),
      expiration = extract("Expiration")
    )
  }
}
