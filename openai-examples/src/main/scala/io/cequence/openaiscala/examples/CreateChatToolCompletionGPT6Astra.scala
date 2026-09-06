package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}

import scala.concurrent.Future

/**
 * Smoke test: GPT-6 Astra rejects function tools on the chat completions API altogether (it
 * demands reasoning_effort 'none' with tools, but doesn't accept 'none'), so the full
 * OpenAIService transparently routes `createChatToolCompletion` through the Responses API for
 * gpt-6 models - the call below works unchanged and keeps the requested reasoning effort.
 * Live-verified 2026-09-05.
 */
object CreateChatToolCompletionGPT6Astra extends Example {

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What's the weather like in Oslo and Tokyo?")
  )

  private val tools = Seq(
    FunctionTool(
      name = "get_current_weather",
      description = Some("Get the current weather in a given location"),
      parameters = JsonSchema.Object(
        properties = Seq(
          "location" -> JsonSchema.String(description = Some("The city, e.g. Oslo")),
          "unit" -> JsonSchema.String(`enum` = Seq("celsius", "fahrenheit"))
        ),
        required = Seq("location")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatToolCompletion(
        messages = messages,
        tools = tools,
        responseToolChoice = None, // "auto"
        settings = CreateChatCompletionSettings(
          model = ModelId.gpt_6_astra,
          reasoning_effort = Some(ReasoningEffort.high)
        )
      )
      .map { response =>
        val choice = response.choices.head
        println(s"finish_reason: ${choice.finish_reason.getOrElse("N/A")}")
        choice.message.tool_calls.foreach { case (id, spec: FunctionCallSpec) =>
          println(s"tool call $id: ${spec.name}(${spec.arguments})")
        }
        response.usage.foreach(u => println(s"usage: $u"))
      }
}
