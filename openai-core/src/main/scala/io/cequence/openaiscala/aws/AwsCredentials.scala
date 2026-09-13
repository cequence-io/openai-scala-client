package io.cequence.openaiscala.aws

import io.cequence.openaiscala.OpenAIScalaClientException

/**
 * AWS credentials used to SigV4-sign a request. `sessionToken` is set only for temporary
 * credentials (STS, an instance profile, IRSA, SSO), in which case it is sent as
 * `X-Amz-Security-Token` and participates in the signature.
 */
final case class AwsCredentials(
  accessKeyId: String,
  secretAccessKey: String,
  sessionToken: Option[String] = None
) {

  // never let a secret reach a log line through an accidental interpolation
  override def toString: String =
    s"AwsCredentials(accessKeyId=${accessKeyId.take(4)}..., secretAccessKey=<redacted>, " +
      s"sessionToken=${sessionToken.map(_ => "<redacted>").getOrElse("none")})"
}

/**
 * Resolves the credentials to sign a request with. Called on EVERY request, so an
 * implementation must be cheap, non-blocking and thread-safe - which is also what makes
 * rotating credentials (STS session tokens, IRSA) work without restarting the service.
 */
trait AwsCredentialsProvider {
  def resolve(): AwsCredentials
}

object AwsCredentialsProvider {

  def apply(f: () => AwsCredentials): AwsCredentialsProvider =
    new AwsCredentialsProvider {
      override def resolve(): AwsCredentials = f()
    }

  /** Fixed credentials, e.g. the access key and secret of a production VM. */
  def static(
    accessKeyId: String,
    secretAccessKey: String,
    sessionToken: Option[String] = None
  ): AwsCredentialsProvider = {
    val fixed = AwsCredentials(accessKeyId, secretAccessKey, sessionToken)
    apply(() => fixed)
  }

  /**
   * Reads the standard AWS environment variables on every call, falling back to the
   * `AWS_BEDROCK_*` names this library already uses for Anthropic on Bedrock:
   *   - `AWS_ACCESS_KEY_ID`, then `AWS_BEDROCK_ACCESS_KEY`
   *   - `AWS_SECRET_ACCESS_KEY`, then `AWS_BEDROCK_SECRET_KEY`
   *   - `AWS_SESSION_TOKEN` (optional)
   *
   * Re-reading on each call means a process whose environment is refreshed picks the new
   * values up without a restart.
   */
  def fromEnv(): AwsCredentialsProvider = apply { () =>
    def env(names: String*): Option[String] =
      names.iterator.map(n => Option(System.getenv(n)).filter(_.nonEmpty)).collectFirst {
        case Some(v) => v
      }

    val accessKey = env("AWS_ACCESS_KEY_ID", "AWS_BEDROCK_ACCESS_KEY").getOrElse(
      throw new OpenAIScalaClientException(
        "No AWS access key found - set AWS_ACCESS_KEY_ID (or AWS_BEDROCK_ACCESS_KEY), " +
          "or pass credentials explicitly via AwsCredentialsProvider.static(...)."
      )
    )
    val secretKey = env("AWS_SECRET_ACCESS_KEY", "AWS_BEDROCK_SECRET_KEY").getOrElse(
      throw new OpenAIScalaClientException(
        "No AWS secret key found - set AWS_SECRET_ACCESS_KEY (or AWS_BEDROCK_SECRET_KEY), " +
          "or pass credentials explicitly via AwsCredentialsProvider.static(...)."
      )
    )

    AwsCredentials(accessKey, secretKey, env("AWS_SESSION_TOKEN"))
  }
}
