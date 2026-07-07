package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/**
 * Runs a provider-agnostic chat-completion batch against '''Bedrock's own batch inference
 * API''' (`model-invocation-job`), through the OpenAI adapter - distinct from the direct
 * Anthropic Message Batches example, which Bedrock does not expose.
 *
 * Under the hood, this adapter stages the requests as a JSONL file in S3, submits a
 * `CreateModelInvocationJob`, polls it, and reads the results back from S3 - all transparent
 * to the caller via the same provider-agnostic `createChatCompletionBatchAndWaitForResults`
 * helper.
 *
 * Differences from the direct API worth knowing:
 *   - No prompt caching: Bedrock batch inference does not support `cache_control` - every
 *     record in a batch that carries one fails individually with "You invoked an unsupported
 *     model or your request did not allow prompt caching" even though the job itself still
 *     reaches a `Completed` status (`successRecordCount=0`). See
 *     [[io.cequence.openaiscala.anthropic.service.AnthropicBedrockBatchInferenceService]] for
 *     more. Don't set `setUseAnthropicSystemMessagesCache` (or any `cache_control`) on
 *     requests going through this adapter.
 *   - Bedrock enforces an account-level '''minimum record count''' per batch job (commonly
 *     cited as 100-1,000+; check Service Quotas -> Amazon Bedrock -> "Minimum number of
 *     records per batch inference job" for your account). This example pads the batch with
 *     filler requests up to [[minBatchSize]] - lower it if your account allows a smaller
 *     minimum.
 *   - Jobs are queued (`Submitted` -> `Validating` -> `Scheduled` -> `InProgress`) and
 *     commonly take tens of minutes before producing any output, even for small batches - much
 *     slower than the direct Message Batches API. The polling interval below is set
 *     accordingly.
 *   - "Deleting" a batch only cleans up the S3 input/output objects this adapter staged -
 *     Bedrock batch inference jobs have no delete API, only stop.
 *
 * Requires `AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_KEY`, `AWS_BEDROCK_REGION`, plus:
 *   - `AWS_BEDROCK_BATCH_S3_BUCKET` - S3 bucket for staging batch inputs/outputs
 *   - `AWS_BEDROCK_BATCH_ROLE_ARN` - IAM service role ARN trusted by `bedrock.amazonaws.com`
 *     with read/write access to that bucket (see <a
 *     href="https://docs.aws.amazon.com/bedrock/latest/userguide/batch-iam-sr.html">Create a
 *     service role for batch inference</a>)
 *
 * ==AWS setup (both the calling IAM user AND the service role need their own grants)==
 *
 * '''1) Trust policy''' on the service role, so Bedrock can assume it:
 * {{{
 * {
 *   "Version": "2012-10-17",
 *   "Statement": [{
 *     "Effect": "Allow",
 *     "Principal": { "Service": "bedrock.amazonaws.com" },
 *     "Action": "sts:AssumeRole",
 *     "Condition": {
 *       "StringEquals": { "aws:SourceAccount": "<account-id>" },
 *       "ArnLike": { "aws:SourceArn": "arn:aws:bedrock:*:<account-id>:model-invocation-job/ *" }
 *     }
 *   }]
 * }
 * }}}
 * (Region wildcarded rather than pinned to one region - see gotcha below on testing multiple
 * regions.)
 *
 * '''2) S3 policy''' - identical policy needed on BOTH the IAM user and the service role (the
 * user stages the input file directly; the role is what Bedrock itself uses to read input /
 * write output). Wildcard the bucket name so it covers every bucket used across regions (extra
 * spaces around some `*`/`/` below are just to keep Scaladoc's comment nesting from breaking -
 * remove them in the real policy):
 * {{{
 * {
 *   "Version": "2012-10-17",
 *   "Statement": [
 *     {
 *       "Effect": "Allow",
 *       "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
 *       "Resource": "arn:aws:s3:::<bucket-prefix>* /openai-scala-client-batches/ *"
 *     },
 *     {
 *       "Effect": "Allow",
 *       "Action": "s3:ListBucket",
 *       "Resource": "arn:aws:s3:::<bucket-prefix>*",
 *       "Condition": { "StringLike": { "s3:prefix": ["openai-scala-client-batches/ *"] } }
 *     }
 *   ]
 * }
 * }}}
 *
 * '''3) `bedrock:InvokeModel`''' on the role, scoped to whatever model(s)/inference-profile(s)
 * you use (or just attach the AWS-managed `AmazonBedrockLimitedAccess` policy).
 *
 * '''4) `iam:PassRole`''' on the calling user, for the service role's ARN, scoped with
 * `iam:PassedToService: bedrock.amazonaws.com` - needed because the user's
 * `CreateModelInvocationJob` call hands the role off to Bedrock.
 *
 * ==Recommendations and things to watch for==
 *
 *   - '''Check both "Model access" and "Quotas" in the Bedrock console, per region.''' They're
 *     separate screens - a model can show a quota without actually being invokable there, and
 *     vice versa.
 *   - '''Cross-region inference profile IDs (the `"eu."`/`"us."` prefix) can be accepted at
 *     `CreateModelInvocationJob` time even when a model isn't actually invokable for batch in
 *     that account/region''' - the job proceeds through `Submitted -> Validating -> Scheduled
 * -> InProgress` and reaches `Completed` regardless. A `Completed` status is '''not''' proof
 * records succeeded - always check `successRecordCount`/`errorRecordCount` on the job itself,
 * not just its terminal status.
 *   - A "Legacy" model (per Anthropic/AWS) fails fast at job-creation with a clear message
 *     ("This Model is marked by provider as Legacy...") rather than a silent per-record
 *     failure - if you see that, switch to a current model.
 *   - If Bedrock reports "Could not validate ListBucket permissions" even though the bucket,
 *     role, and policies all look correct, it's worth simply retrying after a delay before
 *     re-auditing IAM - a very recently created bucket can trip this even with fully correct
 *     permissions.
 */
object AnthropicBedrockCreateChatCompletionBatchWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService with OpenAIChatCompletionBatchService] {

  override val service: OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
    AnthropicServiceFactory.bedrockAsOpenAIWithBatchSupport(
      s3Bucket = sys.env("AWS_BEDROCK_BATCH_S3_BUCKET"),
      roleArn = sys.env("AWS_BEDROCK_BATCH_ROLE_ARN")
    )

  // EU cross-region inference profile prefix - matches the eu-central-1 region the S3
  // bucket/IAM role are scoped to
  private val model = "eu." + NonOpenAIModelId.bedrock_claude_haiku_4_5_20251001_v1_0

  private val settings = CreateChatCompletionSettings(model)

  // see the scaladoc above - tune to your account's actual minimum
  private val minBatchSize = 100

  private val systemMessage = SystemMessage("You are a concise assistant.")

  private val requests = {
    val headline = Seq("Norway", "Sweden", "Denmark").map { country =>
      ChatCompletionBatchRequest(
        customId = s"capital-${country.toLowerCase}",
        messages = Seq(
          systemMessage,
          UserMessage(s"What is the capital of $country? Reply in one word.")
        )
      )
    }

    // filler requests padding the batch up to Bedrock's minimum record count
    val filler = (1 to (minBatchSize - headline.size)).map { index =>
      ChatCompletionBatchRequest(
        customId = s"filler-$index",
        messages = Seq(systemMessage, UserMessage("Say hello in one word."))
      )
    }

    headline ++ filler
  }

  override protected def run: Future[_] =
    service
      .createChatCompletionBatchAndWaitForResults(
        requests,
        settings,
        pollingInterval = 60.seconds,
        deleteBatchAfterUse = true
      )
      .map(
        _.filter(_.customId.startsWith("capital-")).foreach { item =>
          item.result match {
            case Right(response) =>
              println(s"${item.customId}: ${response.contentHead}")
            case Left(error) =>
              println(s"${item.customId}: ERROR ${error.message}")
          }
        }
      )
}
