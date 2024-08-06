package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.{BaseMessage, UserMessage}

import scala.concurrent.Future

object CreateChatFunCompletion extends Example {

  val messages: Seq[BaseMessage] = Seq(
    UserMessage("What's the weather like in Boston?")
  )

  // as a param type we can use "number", "string", "boolean", "object", "array", and "null"
  val functions: Seq[FunctionTool] = Seq(
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

  def run: Future[Unit] =
    // if we want to force the model to use the above function as a response
    // we can do so by passing: responseFunctionName = Some("get_current_weather")`
    service
      .createChatFunCompletion(
        messages = messages,
        functions = functions,
        responseFunctionName = None
      )
      .map { response =>
        val chatFunCompletionMessage = response.choices.head.message
        val functionCall = chatFunCompletionMessage.function_call

        println(
          "function call name      : " + functionCall.map(_.name).getOrElse("N/A")
        )
        println(
          "function call arguments : " + functionCall.map(_.arguments).getOrElse("N/A")
        )
      }
}
