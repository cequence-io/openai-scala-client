package io.cequence.openaiscala.examples.responsesapi

import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.{CreateModelResponseSettings, Inputs}
import io.cequence.openaiscala.examples.Example
import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.responsesapi.tools.{FunctionTool, Tool, ToolChoice}
import io.cequence.openaiscala.domain.JsonSchema

object CreateModelResponseWithFunctions extends Example {

  override def run: Future[Unit] =
    service
      .createModelResponse(
        Inputs.Text("What is the weather like in Boston today?"),
        settings = CreateModelResponseSettings(
          model = ModelId.gpt_5_mini,
          tools = Seq(
            Tool.function(
              name = "get_current_weather",
              parameters = JsonSchema.Object(
                properties = Map(
                  "location" -> JsonSchema.String(
                    description = Some("The city and state, e.g. San Francisco, CA")
                  ),
                  "unit" -> JsonSchema.String(
                    `enum` = Seq("celsius", "fahrenheit")
                  )
                ),
                required = Seq("location", "unit")
              ),
              description = Some("Get the current weather in a given location"),
              strict = true
            )
          ),
          toolChoice = Some(ToolChoice.Mode.Auto)
        )
      )
      .map { response =>
        val functionCall = response.outputFunctionCalls.headOption
          .getOrElse(throw new RuntimeException("No function call output found"))

        println(
          s"""Function Call Details:
             |Name: ${functionCall.name}
             |Arguments: ${functionCall.arguments}
             |Call ID: ${functionCall.callId}
             |ID: ${functionCall.id}
             |Status: ${functionCall.status}""".stripMargin
        )

        val toolsUsed = response.tools.map(_.`type`)

        println(s"${toolsUsed.size} tools used: ${toolsUsed.mkString(", ")}")
      }
}
