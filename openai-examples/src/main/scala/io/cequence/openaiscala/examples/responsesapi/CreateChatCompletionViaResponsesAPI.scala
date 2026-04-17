package io.cequence.openaiscala.examples.responsesapi

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.Example
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService

import scala.concurrent.Future

/**
 * Demonstrates using the Responses API as a drop-in replacement for the Chat Completions API
 * via `OpenAIResponsesChatCompletionService` adapter.
 *
 * This wraps the underlying `OpenAIResponsesService` (Responses API) and exposes it through
 * the `OpenAIChatCompletionService` interface (`createChatCompletion` /
 * `createChatToolCompletion`).
 */
object CreateChatCompletionViaResponsesAPI extends Example {

  // Wrap the service (which is an OpenAIResponsesService) with the adapter
  private val chatService = OpenAIResponsesChatCompletionService(service)

  override protected def run: Future[_] =
    for {
      _ <- runChatCompletion()
      _ <- runChatToolCompletion()
    } yield ()

  private def runChatCompletion(): Future[Unit] = {
    println("=== Chat Completion via Responses API ===\n")

    chatService
      .createChatCompletion(
        messages = Seq(
          SystemMessage("You are a helpful assistant."),
          UserMessage("What is the capital of France?")
        ),
        settings = CreateChatCompletionSettings(ModelId.gpt_5_mini)
      )
      .map { response =>
        println(s"Response: ${response.contentHead}")
        response.usage.foreach { usage =>
          println(
            s"Usage: ${usage.prompt_tokens} prompt + " +
              s"${usage.completion_tokens.getOrElse(0)} completion = ${usage.total_tokens} total"
          )
        }
        println()
      }
  }

  private def runChatToolCompletion(): Future[Unit] = {
    println("=== Chat Tool Completion via Responses API ===\n")

    val tools = Seq(
      FunctionTool(
        name = "get_current_weather",
        description = Some("Get the current weather in a given location"),
        parameters = JsonSchema.Object(
          properties = Seq(
            "location" -> JsonSchema.String(
              description = Some("The city and state, e.g. San Francisco, CA")
            ),
            "unit" -> JsonSchema.String(
              description = Some("The unit of temperature"),
              `enum` = Seq("celsius", "fahrenheit")
            )
          ),
          required = Seq("location")
        )
      )
    )

    chatService
      .createChatToolCompletion(
        messages = Seq(
          SystemMessage("You are a helpful assistant."),
          UserMessage(
            "What's the weather like in San Francisco and Tokyo? Use celsius."
          )
        ),
        tools = tools,
        responseToolChoice = None,
        settings = CreateChatCompletionSettings(
          ModelId.gpt_5_mini,
          parallel_tool_calls = Some(true)
        )
      )
      .map { response =>
        val toolCalls = response.assistantToolMessage.tool_calls.collect {
          case (id, x: FunctionCallSpec) => (id, x)
        }

        println(
          "tool call ids                : " + toolCalls.map(_._1).mkString(", ")
        )
        println(
          "function/tool call names     : " + toolCalls.map(_._2.name).mkString(", ")
        )
        println(
          "function/tool call arguments : " + toolCalls.map(_._2.arguments).mkString(", ")
        )
      }
  }
}
