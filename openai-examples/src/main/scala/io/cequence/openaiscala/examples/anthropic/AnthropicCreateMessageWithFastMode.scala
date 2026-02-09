package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.{SystemMessage, UserMessage}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMessageSettings,
  Speed
}
import io.cequence.openaiscala.anthropic.service.{AnthropicService, AnthropicServiceFactory}
import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.examples.ExampleBase

import scala.concurrent.Future

// requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment variable to be set
// Fast mode delivers up to 2.5x faster output token generation for Opus models at premium pricing ($30/$150 per MTok)
// Requires the beta header "fast-mode-2026-02-01"
// Note: Fast mode has separate rate limits (default 0 tokens/min). You must request a rate limit increase
// at https://platform.claude.com/docs/en/api/rate-limits or contact sales at https://www.anthropic.com/contact-sales
object AnthropicCreateMessageWithFastMode extends ExampleBase[AnthropicService] {

  override protected val service: AnthropicService = AnthropicServiceFactory()

  val messages: Seq[Message] = Seq(
    SystemMessage("You are a helpful assistant who knows elfs personally."),
    UserMessage("What is the weather like in Norway?")
  )

  private val baseSettings = AnthropicCreateMessageSettings(
    model = NonOpenAIModelId.claude_opus_4_6,
    max_tokens = 4096
  )

  override protected def run: Future[_] =
    for {
      // Normal mode
      (normalResponse, normalTime) <- timed {
        service.createMessage(messages, settings = baseSettings)
      }

      _ = {
        println("=" * 60)
        println("NORMAL MODE")
        println("=" * 60)
        println(s"Time: ${normalTime}ms")
        println(s"Response: ${normalResponse.text.take(200)}...")
        println()
      }

      // Fast mode
      (fastResponse, fastTime) <- timed {
        service.createMessage(
          messages,
          settings = baseSettings.copy(speed = Some(Speed.fast))
        )
      }

      _ = {
        println("=" * 60)
        println("FAST MODE")
        println("=" * 60)
        println(s"Time: ${fastTime}ms")
        println(s"Response: ${fastResponse.text.take(200)}...")
        println()
      }
    } yield {
      println("=" * 60)
      println("COMPARISON")
      println("=" * 60)
      println(s"Normal mode: ${normalTime}ms")
      println(s"Fast mode:   ${fastTime}ms")
      println(s"Speedup:     ${normalTime.toDouble / fastTime}x")
    }

  private def timed[T](f: => Future[T]): Future[(T, Long)] = {
    val start = System.currentTimeMillis()
    f.map { result =>
      val elapsed = System.currentTimeMillis() - start
      (result, elapsed)
    }
  }
}
