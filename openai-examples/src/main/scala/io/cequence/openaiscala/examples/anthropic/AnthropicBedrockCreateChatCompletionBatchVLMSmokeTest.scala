package io.cequence.openaiscala.examples.anthropic

import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  NonOpenAIModelId,
  SystemMessage,
  TextContent,
  UserMessage,
  UserSeqMessage,
  VLMContent
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live smoke test: multimodal (VLM) content through '''Bedrock batch inference'''
 * (`model-invocation-job`) via the OpenAI adapter - the Bedrock sibling of
 * [[io.cequence.openaiscala.examples.CreateChatCompletionBatchVLMSmokeTest]], which covers the
 * direct Anthropic Message Batches API (plus OpenAI and Gemini).
 *
 * Two solid-color PNGs (base64 data URIs - Bedrock/Anthropic reject remote image URLs) ask for
 * the dominant color in one word; the batch is padded with text filler requests up to
 * Bedrock's account-level minimum record count (see the AnthropicBedrockCreateChatCompletion-
 * BatchWithOpenAIAdapter scaladoc for the full Bedrock batch setup and gotchas - incl. that a
 * `Completed` job status alone is NOT proof records succeeded, hence the per-item checks
 * here).
 *
 * Requires `AWS_BEDROCK_ACCESS_KEY`, `AWS_BEDROCK_SECRET_KEY`, `AWS_BEDROCK_REGION`,
 * `AWS_BEDROCK_BATCH_S3_BUCKET`, and `AWS_BEDROCK_BATCH_ROLE_ARN`. Jobs are queued and
 * commonly take tens of minutes even for small batches.
 *
 * Written as a standalone `main` (not `Example`) so output is not swallowed by sbt's TrapExit
 *   - see the "Running Examples" note in CLAUDE.md.
 */
object AnthropicBedrockCreateChatCompletionBatchVLMSmokeTest {

  // 64x64 solid-color PNGs, generated offline - no external assets or URLs needed
  private val redPngBase64 =
    "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAeUlEQVR4nO3PQQkAMAzAwCqpf1ETMxF7HINABFzm7H7dcEEDWtCAFjSgBQ1oQQNa0IAWNKAFDWhBA1rQgBY0oAUNaEEDWtCAFjSgBQ1oQQNa0IAWNKAFDWhBA1rQgBY0oAUNaEEDWtCAFjSgBQ1oQQNa0IAWNKAFj13PLIEAOXyUUwAAAABJRU5ErkJggg=="

  private val bluePngBase64 =
    "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAe0lEQVR4nO3PUQkAIBTAwJfEJPZPYRhD+HEIgwW4zdrn64YLGtCCBrSgAS1oQAsa0IIGtKABLWhACxrQgga0oAEtaEALGtCCBrSgAS1oQAsa0IIGtKABLWhACxrQgga0oAEtaEALGtCCBrSgAS1oQAsa0IIGtKABLXjsArR1YR6a53LGAAAAAElFTkSuQmCC"

  private implicit val actorSystem: ActorSystem = ActorSystem("bedrock-vlm-batch-smoke")
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val materializer: Materializer = Materializer(actorSystem)
  private implicit val scheduler: Scheduler = actorSystem.scheduler

  // EU cross-region inference profile prefix - matches the region the S3 bucket/IAM role are
  // scoped to
  private val model = "eu." + NonOpenAIModelId.bedrock_claude_haiku_4_5_20251001_v1_0

  // see the sibling text example's scaladoc - tune to your account's actual minimum
  private val minBatchSize = 100

  private val systemMessage = SystemMessage("You are a concise assistant.")

  private val expectedByCustomId = Map(
    "vlm-red-square" -> "red",
    "vlm-blue-square" -> "blue"
  )

  private def vlmRequest(
    customId: String,
    pngBase64: String,
    fileName: String
  ) = ChatCompletionBatchRequest(
    customId = customId,
    messages = Seq(
      systemMessage,
      UserSeqMessage(
        Seq(
          TextContent(
            "What is the dominant color of the attached image? Answer with exactly one word."
          )
        ) ++ VLMContent.of(pngBase64, fileName)
      )
    )
  )

  private val requests = {
    val vlm = Seq(
      vlmRequest("vlm-red-square", redPngBase64, "red-square.png"),
      vlmRequest("vlm-blue-square", bluePngBase64, "blue-square.png")
    )

    // filler requests padding the batch up to Bedrock's minimum record count
    val filler = (1 to (minBatchSize - vlm.size)).map { index =>
      ChatCompletionBatchRequest(
        customId = s"filler-$index",
        messages = Seq(systemMessage, UserMessage("Say hello in one word."))
      )
    }

    vlm ++ filler
  }

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory.bedrockAsOpenAIWithBatchSupport(
      s3Bucket = sys.env("AWS_BEDROCK_BATCH_S3_BUCKET"),
      roleArn = sys.env("AWS_BEDROCK_BATCH_ROLE_ARN")
    )

    println(s"[bedrock] submitting VLM batch (model: $model, ${requests.size} records)...")

    try {
      val items = Await.result(
        service.createChatCompletionBatchAndWaitForResults(
          requests,
          settings = CreateChatCompletionSettings(model),
          pollingInterval = 60.seconds,
          timeout = Some(90.minutes),
          deleteBatchAfterUse = true
        ),
        95.minutes
      )

      println("\n===== Bedrock VLM batch smoke test results =====")

      val vlmLines =
        items.filter(item => expectedByCustomId.contains(item.customId)).map { item =>
          item.result match {
            case Right(response) =>
              val text = response.contentHead.trim
              val expected = expectedByCustomId(item.customId)
              val verdict =
                if (text.toLowerCase.contains(expected)) "PASS"
                else s"FAIL (expected '$expected')"
              f"bedrock    ${item.customId}%-16s -> '$text' $verdict"
            case Left(error) =>
              f"bedrock    ${item.customId}%-16s -> BATCH ITEM ERROR: ${error.message} FAIL"
          }
        }
      vlmLines.foreach(println)

      val fillerErrors = items.count(i => i.customId.startsWith("filler-") && i.result.isLeft)
      println(
        s"filler records: ${items.count(_.customId.startsWith("filler-"))} " +
          s"($fillerErrors errored)"
      )

      val failures = vlmLines.count(_.contains("FAIL"))
      println(
        if (failures == 0)
          "\nALL PASS - image content survives the Bedrock batch inference path"
        else s"\n$failures FAILURE(S)"
      )
    } finally {
      service.close()
      actorSystem.terminate()
      ()
    }
  }
}
