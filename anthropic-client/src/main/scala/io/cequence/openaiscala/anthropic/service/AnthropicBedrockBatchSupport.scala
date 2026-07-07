package io.cequence.openaiscala.anthropic.service

/**
 * Configuration enabling the provider-agnostic chat-completion batch endpoints on the Bedrock
 * OpenAI adapter. Bedrock batch inference has no inline input/output, so requests are staged
 * as a JSONL file in S3 and results are read back from there.
 *
 * @param batchService
 *   The batch-inference service to run the jobs with (same region as the S3 bucket is not
 *   required, but the IAM role must be allowed to access both).
 * @param s3Bucket
 *   S3 bucket used for staging inputs and outputs.
 * @param roleArn
 *   ARN of an IAM service role trusted by `bedrock.amazonaws.com`, with permissions to read
 *   and write `s3Bucket`. See <a
 *   href="https://docs.aws.amazon.com/bedrock/latest/userguide/batch-iam-sr.html">Create a
 *   service role for batch inference</a>.
 * @param s3Region
 *   Region of `s3Bucket`. Defaults to the batch-inference service's own region.
 * @param s3PathPrefix
 *   Object-key prefix under which the batch folders are created.
 */
final case class AnthropicBedrockBatchSupport(
  batchService: AnthropicBedrockBatchInferenceService,
  s3Bucket: String,
  roleArn: String,
  s3Region: Option[String] = None,
  s3PathPrefix: String = "openai-scala-client-batches"
)
