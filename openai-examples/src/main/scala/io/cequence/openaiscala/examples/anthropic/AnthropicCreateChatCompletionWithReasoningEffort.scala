package io.cequence.openaiscala.examples.anthropic

import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Example demonstrating how to use Anthropic Claude's extended thinking/reasoning capabilities
 * via the OpenAI adapter using the reasoning_effort parameter.
 *
 * The reasoning_effort gets automatically converted to Anthropic's thinking budget based on
 * the configuration in openai-scala-client.conf:
 *   - none -> 0 (don't enable extended thinking)
 *   - minimal -> 1024 tokens
 *   - low -> 2048 tokens
 *   - medium -> 4096 tokens
 *   - high -> 8192 tokens
 *
 * Requires `openai-scala-anthropic-client` as a dependency and `ANTHROPIC_API_KEY` environment
 * variable to be set.
 *
 * Note: Extended thinking is supported on Claude 3.7 Sonnet and later models.
 */
object AnthropicCreateChatCompletionWithReasoningEffort
    extends ExampleBase[OpenAIChatCompletionService] {

  private val settings = CreateChatCompletionSettings(
    model = NonOpenAIModelId.claude_haiku_4_5_20251001,
    max_tokens = Some(10000),
    temperature = Some(0.2)
  )

  override protected val service: OpenAIChatCompletionService =
    AnthropicServiceFactory.asOpenAI()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant who knows elfs personally."),
    UserMessage(
      "What is the weather like in Norway? Write a two-page saga about it. Think as much as possible on this task. While doing this count the number of words with Scandinavian origin you used."
    )
  )

  override protected def run: Future[_] = {
    // Example with no reasoning effort (extended thinking disabled)
    println("=== Example 1: No Reasoning Effort (Baseline) ===")
    for {
      response1 <- service.createChatCompletion(
        messages,
        settings.copy(
          // Converted to Anthropic thinking budget: 0 (no extended thinking)
          reasoning_effort = Some(ReasoningEffort.none)
        )
      )

      _ = {
        println("Response:")
        println(response1.contentHead)
        println(s"\nUsage: ${response1.usage}")
        response1.usage.flatMap(_.completion_tokens_details).foreach { details =>
          println(s"  Reasoning tokens: ${details.reasoning_tokens.getOrElse(0)}")
        }
      }

      // Example with minimal reasoning effort (1024 token thinking budget)
      _ = println("\n=== Example 2: Minimal Reasoning Effort ===")

      response2 <- service.createChatCompletion(
        messages,
        settings.copy(
          // Converted to Anthropic thinking budget: 1024
          reasoning_effort = Some(ReasoningEffort.minimal)
        )
      )

      _ = {
        println("Response:")
        println(response2.contentHead)
        println(s"\nUsage: ${response2.usage}")
        response2.usage.flatMap(_.completion_tokens_details).foreach { details =>
          println(s"  Reasoning tokens: ${details.reasoning_tokens.getOrElse(0)}")
        }
      }

      // Example with low reasoning effort (2048 token thinking budget)
      _ = println("\n=== Example 3: Low Reasoning Effort ===")

      response3 <- service.createChatCompletion(
        messages,
        settings.copy(
          // Converted to Anthropic thinking budget: 2048
          reasoning_effort = Some(ReasoningEffort.low)
        )
      )
      _ = {
        println("Response:")
        println(response3.contentHead)
        println(s"\nUsage: ${response3.usage}")
        response3.usage.flatMap(_.completion_tokens_details).foreach { details =>
          println(s"  Reasoning tokens: ${details.reasoning_tokens.getOrElse(0)}")
        }
      }

      // Example with medium reasoning effort (4096 token thinking budget)
      _ = println("\n=== Example 4: Medium Reasoning Effort ===")
      response4 <- service.createChatCompletion(
        messages,
        settings.copy(
          // Converted to Anthropic thinking budget: 4096
          reasoning_effort = Some(ReasoningEffort.medium)
        )
      )

      _ = {
        println("Response:")
        println(response4.contentHead)
        println(s"\nUsage: ${response4.usage}")
        response4.usage.flatMap(_.completion_tokens_details).foreach { details =>
          println(s"  Reasoning tokens: ${details.reasoning_tokens.getOrElse(0)}")
        }
      }

      // Example with high reasoning effort (8192 token thinking budget)
      _ = println("\n=== Example 5: High Reasoning Effort ===")
      response5 <- service.createChatCompletion(
        messages,
        settings.copy(
          // Converted to Anthropic thinking budget: 8192
          reasoning_effort = Some(ReasoningEffort.high)
        )
      )
    } yield {
      println("Response:")
      println(response5.contentHead)
      println(s"\nUsage: ${response5.usage}")
      response5.usage.flatMap(_.completion_tokens_details).foreach { details =>
        println(s"  Reasoning tokens: ${details.reasoning_tokens.getOrElse(0)}")
      }
    }
  }
}
