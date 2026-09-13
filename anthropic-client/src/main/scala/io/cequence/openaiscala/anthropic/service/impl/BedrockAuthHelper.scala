package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.aws.AwsSigV4

/**
 * AWS SigV4 request signer for the Anthropic-on-Bedrock services.
 *
 * The implementation now lives in `openai-core` as [[AwsSigV4]], so the OpenAI-compatible
 * Bedrock path can share it; this trait is a thin delegating shim that keeps the mixin shape
 * (and the `protected` signatures) the Anthropic services were written against.
 *
 * Used by [[AnthropicBedrockServiceImpl]] (Bedrock Invoke), [[BedrockStsClient]]
 * (STS:GetSessionToken), [[S3BatchStorage]] and [[AnthropicBedrockBatchInferenceServiceImpl]].
 */
trait BedrockAuthHelper {

  protected def addAuthHeaders(
    method: String,
    url: String,
    headers: Map[String, String],
    body: String,
    accessKey: String,
    secretKey: String,
    region: String,
    service: String,
    sessionToken: Option[String] = None
  ): Map[String, String] =
    AwsSigV4.signedHeaders(
      method = method,
      url = url,
      headers = headers,
      body = body,
      accessKey = accessKey,
      secretKey = secretKey,
      region = region,
      service = service,
      sessionToken = sessionToken
    )

  protected def rfc3986Encode(value: String): String = AwsSigV4.rfc3986Encode(value)
}
