package io.cequence.openaiscala.examples.fireworksai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

/**
 * Requires `FIREWORKS_API_KEY` environment variable to be set.
 */
object FireworksAICreateChatToolCompletion extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.fireworks

  private val fireworksModelPrefix = "accounts/fireworks/models/"

  private val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?")
  )

  private val tools = Seq(
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

  override protected def run: Future[_] =
    service
      .createChatToolCompletion(
        messages = messages,
        tools = tools,
        responseToolChoice = None,
        settings = CreateChatCompletionSettings(
          fireworksModelPrefix + "kimi-k2-instruct-0905",
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
