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
 * For Gemini 3.x models the reasoning_effort is converted to Gemini's thinkingLevel:
 *   - none/minimal -> MINIMAL (Flash variants; Pro floors at LOW)
 *   - low -> LOW
 *   - medium -> MEDIUM
 *   - high/xhigh/max -> HIGH
 *
 * For Gemini 2.5 models it is converted to a thinkingBudget (token count) based on the
 * reasoning-effort-thinking-budget-mapping configuration in openai-scala-client.conf.
 *
 * Requires `openai-scala-google-gemini-client` as a dependency and `GOOGLE_API_KEY`
 * environment variable to be set.
 */
object GoogleGeminiCreateChatCompletionWithReasoningEffort
    extends ExampleBase[OpenAIChatCompletionService] {

  private val settings = CreateChatCompletionSettings(
    model = NonOpenAIModelId.gemini_3_6_flash,
    // temperature/top_p/top_k are deprecated and ignored as of Gemini 3.6
    max_tokens = Some(10000)
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
          // Converted to thinkingLevel: MINIMAL - Gemini 3.x cannot fully disable thinking
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
          // Converted to thinkingLevel: MINIMAL
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
          // Converted to thinkingLevel: LOW
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
          // Converted to thinkingLevel: MEDIUM
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
          // Converted to thinkingLevel: HIGH
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
