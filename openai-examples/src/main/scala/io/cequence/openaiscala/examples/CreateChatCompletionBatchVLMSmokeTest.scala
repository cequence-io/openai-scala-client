package io.cequence.openaiscala.examples

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatCompletionBatchRequest,
  ModelId,
  NonOpenAIModelId,
  SystemMessage,
  TextContent,
  UserSeqMessage,
  VLMContent
}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService,
  OpenAIServiceFactory
}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Cross-provider smoke test: multimodal (VLM) content through the provider-agnostic
 * chat-completion BATCH support - OpenAI (JSONL Batch API), Anthropic (Message Batches), and
 * Gemini (Batch Mode) via their OpenAI adapters.
 *
 * Each provider gets the same two-request batch: a solid red and a solid blue 64x64 PNG
 * (embedded as base64 data URIs - the only image form all providers accept; Anthropic and
 * Vertex reject remote image URLs), asking for the dominant color in one word. The test PASSes
 * per item when the answer contains the expected color, proving image content survives each
 * provider's batch body construction and round-trips through result retrieval.
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY`, `ANTHROPIC_API_KEY`, and `GOOGLE_API_KEY`.
 *
 * Written as a standalone `main` (not `Example`) so output is not swallowed by sbt's TrapExit
 *   - see the "Running Examples" note in CLAUDE.md. Batches usually complete in minutes, but
 *     providers only guarantee ~24h; on timeout the error message carries the batch id so it
 *     can be picked up later with `getChatCompletionBatch`.
 */
object CreateChatCompletionBatchVLMSmokeTest {

  // 64x64 solid-color PNGs, generated offline - no external assets or URLs needed
  private val redPngBase64 =
    "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAeUlEQVR4nO3PQQkAMAzAwCqpf1ETMxF7HINABFzm7H7dcEEDWtCAFjSgBQ1oQQNa0IAWNKAFDWhBA1rQgBY0oAUNaEEDWtCAFjSgBQ1oQQNa0IAWNKAFDWhBA1rQgBY0oAUNaEEDWtCAFjSgBQ1oQQNa0IAWNKAFj13PLIEAOXyUUwAAAABJRU5ErkJggg=="

  private val bluePngBase64 =
    "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAe0lEQVR4nO3PUQkAIBTAwJfEJPZPYRhD+HEIgwW4zdrn64YLGtCCBrSgAS1oQAsa0IIGtKABLWhACxrQgga0oAEtaEALGtCCBrSgAS1oQAsa0IIGtKABLWhACxrQgga0oAEtaEALGtCCBrSgAS1oQAsa0IIGtKABLXjsArR1YR6a53LGAAAAAElFTkSuQmCC"

  private implicit val actorSystem: ActorSystem = ActorSystem("vlm-batch-smoke")
  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val scheduler: Scheduler = actorSystem.scheduler

  private val expectedByCustomId = Map(
    "vlm-red-square" -> "red",
    "vlm-blue-square" -> "blue"
  )

  // custom ids kept within [a-z0-9_-] so the same requests also work on Vertex AI batch
  private def requests = Seq(
    ChatCompletionBatchRequest(
      customId = "vlm-red-square",
      messages = Seq(
        SystemMessage("You are a concise assistant."),
        UserSeqMessage(
          Seq(
            TextContent(
              "What is the dominant color of the attached image? Answer with exactly one word."
            )
          ) ++ VLMContent.of(redPngBase64, "red-square.png")
        )
      )
    ),
    ChatCompletionBatchRequest(
      customId = "vlm-blue-square",
      messages = Seq(
        SystemMessage("You are a concise assistant."),
        UserSeqMessage(
          Seq(
            TextContent(
              "What is the dominant color of the attached image? Answer with exactly one word."
            )
          ) ++ VLMContent.of(bluePngBase64, "blue-square.png")
        )
      )
    )
  )

  private def runProvider(
    name: String,
    service: OpenAIChatCompletionService with OpenAIChatCompletionBatchService,
    model: String
  ): Future[Seq[String]] = {
    println(s"[$name] submitting VLM batch (model: $model)...")
    service
      .createChatCompletionBatchAndWaitForResults(
        requests,
        settings = CreateChatCompletionSettings(model),
        pollingInterval = 15.seconds,
        timeout = Some(30.minutes),
        deleteBatchAfterUse = true
      )
      .map(_.map { item =>
        item.result match {
          case Right(response) =>
            val text = response.contentHead.trim
            val expected = expectedByCustomId(item.customId)
            val verdict =
              if (text.toLowerCase.contains(expected)) "PASS"
              else s"FAIL (expected '$expected')"
            f"$name%-10s ${item.customId}%-16s -> '$text' $verdict"
          case Left(error) =>
            f"$name%-10s ${item.customId}%-16s -> BATCH ITEM ERROR: ${error.message} FAIL"
        }
      })
      .recover { case e =>
        Seq(f"$name%-10s BATCH FAILED: ${e.getMessage} FAIL")
      }
      .andThen { case _ => service.close() }
  }

  def main(args: Array[String]): Unit = {
    val results = Await.result(
      Future.sequence(
        Seq(
          runProvider("openai", OpenAIServiceFactory(), ModelId.gpt_5_6_luna),
          runProvider(
            "anthropic",
            AnthropicServiceFactory.asOpenAI(),
            NonOpenAIModelId.claude_haiku_4_5
          ),
          runProvider(
            "gemini",
            GeminiServiceFactory.asOpenAI(),
            NonOpenAIModelId.gemini_2_5_flash_lite
          )
        )
      ),
      35.minutes
    )

    println("\n===== VLM batch smoke test results =====")
    val lines = results.flatten
    lines.foreach(println)

    val failures = lines.count(l => l.endsWith("FAIL") || l.contains("FAIL ("))
    println(
      if (failures == 0) "\nALL PASS - image content survives all three providers' batch paths"
      else s"\n$failures FAILURE(S)"
    )

    actorSystem.terminate()
    ()
  }
}
