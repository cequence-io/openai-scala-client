package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala._
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Demonstrates that Gemini exceptions are properly repackaged as OpenAI exceptions when using
 * the OpenAI adapter. This allows consistent error handling regardless of the underlying
 * provider.
 *
 * Requires `GOOGLE_API_KEY` environment variable to be set.
 */
object GoogleGeminiErrorHandlingWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = GeminiServiceFactory.asOpenAI()

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("Say hello.")
  )

  override protected def run: Future[_] = {
    // Test 1: Use a non-existent model to trigger a 404 error
    println("=== Test 1: Non-existent model (expecting OpenAIScalaClientException) ===")
    val test1 = service
      .createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(
          model = "non-existent-model-xyz"
        )
      )
      .map(printMessageContent)
      .recover {
        case e: OpenAIScalaUnauthorizedException =>
          println(s"[OK] Caught OpenAIScalaUnauthorizedException: ${e.getMessage}")
        case e: OpenAIScalaClientException =>
          println(s"[OK] Caught OpenAIScalaClientException: ${e.getMessage}")
        case e: Throwable =>
          println(s"[UNEXPECTED] Caught ${e.getClass.getSimpleName}: ${e.getMessage}")
      }

    // Test 2: Normal successful call to verify the adapter works
    println("\n=== Test 2: Valid model (expecting success) ===")
    val test2 = test1.flatMap { _ =>
      service
        .createChatCompletion(
          messages = messages,
          settings = CreateChatCompletionSettings(
            model = NonOpenAIModelId.gemini_2_5_flash
          )
        )
        .map { response =>
          println(s"[OK] Success: ${response.contentHead}")
        }
        .recover {
          case e: OpenAIScalaClientException =>
            println(s"[FAIL] Caught OpenAIScalaClientException: ${e.getMessage}")
          case e: Throwable =>
            println(s"[UNEXPECTED] Caught ${e.getClass.getSimpleName}: ${e.getMessage}")
        }
    }

    // Test 3: Check that Retryable classification works on repackaged exceptions
    println("\n=== Test 3: Retryable check on repackaged exceptions ===")
    test2.flatMap { _ =>
      service
        .createChatCompletion(
          messages = messages,
          settings = CreateChatCompletionSettings(
            model = "non-existent-model-xyz"
          )
        )
        .map(_ => ())
        .recover { case e: OpenAIScalaClientException =>
          val isRetryable = Retryable(e)
          println(s"[OK] Exception type: ${e.getClass.getSimpleName}, retryable: $isRetryable")
        }
    }
  }
}
