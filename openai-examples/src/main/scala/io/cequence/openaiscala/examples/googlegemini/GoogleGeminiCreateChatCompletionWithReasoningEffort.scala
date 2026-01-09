package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.domain.{NonOpenAIModelId, SystemMessage, UserMessage}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Example demonstrating how to use Gemini's thinking/reasoning capabilities via the OpenAI
 * adapter using the reasoning_effort parameter.
 *
 * The reasoning_effort gets automatically converted to Gemini's thinkingBudget based on the
 * configuration in openai-scala-client.conf:
 *   - none -> 0 or 128 (no thinking or absolute minimal thinking)
 *   - minimal -> 256 tokens
 *   - low -> 1024 tokens
 *   - medium -> 4096 tokens
 *   - high -> 8192 tokens
 *
 * Requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY`
 * environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionWithReasoningEffort
    extends ExampleBase[OpenAIChatCompletionService] {

  private val settings = CreateChatCompletionSettings(
    model = NonOpenAIModelId.gemini_3_pro_preview,
    max_tokens = Some(10000),
    temperature = Some(0.2)
  )

  override protected val service: OpenAIChatCompletionService =
    GeminiServiceFactory.asOpenAI()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant who knows elfs personally."),
    UserMessage(
      "What is the weather like in Norway? Write a two-page saga about it. Think as much as possible on this task. While doing this count the number of words with Scandinavian origin you used."
    )
  )

  override protected def run: Future[_] = {
    // Example with no reasoning effort (thinking disabled)
    println("=== Example 1: No Reasoning Effort (Baseline) ===")
    for {
      response1 <- service.createChatCompletion(
        messages,
        settings.copy(
          // Converted to thinkingBudget: 0 (no thinking)
          // Note: Some models like Gemini 2.5 Pro have a minimum thinking budget (128),
          // so they'll use minimal thinking instead of disabling it completely.
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

      // Example with minimal reasoning effort (256 token thinking budget)
      _ = println("\n=== Example 2: Minimal Reasoning Effort ===")

      response2 <- service.createChatCompletion(
        messages,
        settings.copy(
          // Converted to thinkingBudget: 256
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

      // Example with low reasoning effort (1024 token thinking budget)
      _ = println("\n=== Example 3: Low Reasoning Effort ===")

      response3 <- service.createChatCompletion(
        messages,
        settings.copy(
          // Converted to thinkingBudget: 1024
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
          // Converted to thinkingBudget: 4096
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
          // Converted to thinkingBudget: 8192
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
