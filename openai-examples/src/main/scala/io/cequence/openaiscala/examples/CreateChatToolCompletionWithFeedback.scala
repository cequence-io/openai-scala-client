package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import play.api.libs.json.Json

import scala.concurrent.Future

// based on: https://platform.openai.com/docs/guides/function-calling
object CreateChatToolCompletionWithFeedback extends Example {

  private val modelId = ModelId.gpt_4_turbo_preview

  val introMessages = Seq(
    SystemMessage("You are a helpful assistant."),
    UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?")
  )

  // as a param type we can use "number", "string", "boolean", "object", "array", and "null"
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
    for {
      assistantToolResponse <- service.createChatToolCompletion(
        messages = introMessages,
        tools = tools,
        responseToolChoice = None, // means "auto"
        settings = CreateChatCompletionSettings(modelId)
      )

      assistantToolMessage = assistantToolResponse.choices.head.message

      toolCalls = assistantToolMessage.tool_calls

      // we can handle only function calls (that will change in future)
      functionCalls = toolCalls.collect { case (toolCallId, x: FunctionCallSpec) =>
        (toolCallId, x)
      }

      available_functions = Map("get_current_weather" -> getCurrentWeather _)

      toolMessages = functionCalls.map { case (toolCallId, functionCallSpec) =>
        val functionName = functionCallSpec.name
        val functionArgsJson = Json.parse(functionCallSpec.arguments)

        // this is not very generic, but it's ok for a demo
        val functionResponse = available_functions.get(functionName) match {
          case Some(functionToCall) =>
            functionToCall(
              (functionArgsJson \ "location").as[String],
              (functionArgsJson \ "unit").asOpt[String]
            )

          case _ => throw new IllegalArgumentException(s"Unknown function: $functionName")
        }

        ToolMessage(
          tool_call_id = toolCallId,
          content = Some(functionResponse.toString),
          name = functionName
        )
      }

      messages = introMessages ++ Seq(assistantToolMessage) ++ toolMessages

      finalAssistantResponse <- service.createChatCompletion(
        messages = messages,
        settings = CreateChatCompletionSettings(modelId)
      )
    } yield {
      println(finalAssistantResponse.choices.head.message.content)
    }

  // unit is ignored here
  private def getCurrentWeather(
    location: String,
    unit: Option[String]
  ) =
    location.toLowerCase() match {
      case loc if loc.contains("tokyo") =>
        Json.obj("location" -> "Tokyo", "temperature" -> "10", "unit" -> "celsius")

      case loc if loc.contains("san francisco") =>
        Json.obj("location" -> "San Francisco", "temperature" -> "72", "unit" -> "fahrenheit")

      case loc if loc.contains("paris") =>
        Json.obj("location" -> "Paris", "temperature" -> "22", "unit" -> "celsius")

      case _ =>
        Json.obj("location" -> location, "temperature" -> "unknown")
    }
}
