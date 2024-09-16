package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import scala.concurrent.Future

object CreateChatToolCompletion extends Example {

  val messages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?")
  )

  val tools = Seq(
    FunctionTool(
      name = "get_current_weather",
      description = Some("Get the current weather in a given location"),
      parameters = Map(
        "type" -> "object",
        "properties" -> Map(
          "location" -> Map(
            "type" -> "string",
            "description" -> "The city and state, e.g. San Francisco, CA"
          ),
          "unit" -> Map(
            "type" -> "string",
            "enum" -> Seq("celsius", "fahrenheit")
          )
        ),
        "required" -> Seq("location")
      )
    )
  )

  override protected def run: Future[_] =
    service
      .createChatToolCompletion(
        messages = messages,
        tools = tools,
        responseToolChoice = None, // means "auto"
        settings = CreateChatCompletionSettings(ModelId.gpt_3_5_turbo_1106)
      )
      .map { response =>
        val chatFunCompletionMessage = response.choices.head.message
        val toolCalls = chatFunCompletionMessage.tool_calls.collect {
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
