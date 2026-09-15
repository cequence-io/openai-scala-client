package io.cequence.openaiscala.service

import io.cequence.openaiscala.aws.AwsCredentialsProvider

/**
 * How a Bedrock service authenticates. Both forms work against both [[BedrockEndpoint]] hosts,
 * so auth and endpoint are independent choices.
 */
sealed trait BedrockAuth

object BedrockAuth {

  /**
   * A Bedrock API key, sent as a plain `Authorization: Bearer` header - no request signing.
   */
  final case class BearerToken(apiKey: String) extends BedrockAuth

  /**
   * An IAM access key and secret (optionally an STS session token), signed per request with
   * AWS Signature Version 4. Credentials are resolved on EVERY request, so rotating STS /
   * instance-profile / IRSA credentials are picked up without restarting the service.
   */
  final case class SigV4(
    credentials: AwsCredentialsProvider = AwsCredentialsProvider.fromEnv()
  ) extends BedrockAuth

  /** The env var AWS itself documents for the Bedrock API key. */
  val bearerTokenEnvKey = "AWS_BEARER_TOKEN_BEDROCK"

  /** Accepted as a fallback spelling of [[bearerTokenEnvKey]]. */
  val bearerTokenEnvKeyAlt = "AWS_BEDROCK_BEARER_TOKEN"

  /** The Bedrock API key from the environment, if either spelling is set and non-empty. */
  def bearerTokenFromEnv(): Option[String] =
    Seq(bearerTokenEnvKey, bearerTokenEnvKeyAlt).iterator
      .map(key => Option(System.getenv(key)))
      .collectFirst { case Some(token) if token.trim.nonEmpty => token.trim }

  /**
   * The bearer token when one is in the environment, SigV4 credentials otherwise - the usual
   * "API key in development, IAM role in production" split, without the caller branching.
   *
   * Nothing is resolved eagerly beyond the token lookup: the SigV4 case reads its credentials
   * per request, so this does not throw when neither is configured. The first request does.
   */
  def fromEnv(): BedrockAuth =
    bearerTokenFromEnv().map(BearerToken(_)).getOrElse(SigV4())
}
