package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateRunSettings
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}
import play.api.libs.json.Json

import scala.concurrent.Future

object CreateRunWithFunctionCall extends Example {

  private val adapters = OpenAIServiceAdapters.forFullService
  override protected val service: OpenAIService =
    adapters.log(
      OpenAIServiceFactory(),
      "openAIService1",
      println(_) // simple logging
    )

  val userId = "123"
  val model = ModelId.gpt_3_5_turbo

  val whatIsTheWeatherMessages: Seq[ThreadMessage] = Seq(
    ThreadMessage(
      "What is the weather in San Francisco?"
    )
  )

  private def createAssistant() =
    for {
      assistant <- service.createAssistant(
        model = model,
        name = Some("Weatherman"),
        instructions = Some(
          "You are a helpful assistant"
        ),
        tools = tools
      )
    } yield assistant

  def createSpecMessagesThread(): Future[Thread] =
    for {
      thread <- service.createThread(
        messages = whatIsTheWeatherMessages,
        metadata = Map("user_id" -> userId)
      )
      _ = println(thread)
    } yield thread

  override protected def run: Future[_] =
    for {
      assistant <- createAssistant()
      assistantId = assistant.id
      eventsThread <- createSpecMessagesThread()

      _ <- service.listThreadMessages(eventsThread.id).map { messages =>
        println("messages:" + messages.map(_.content).mkString("\n"))
      }

      thread <- service.retrieveThread(eventsThread.id)
      _ = println(thread)

      run <- service.createRun(
        threadId = eventsThread.id,
        assistantId = assistantId,
        tools = tools,
        responseToolChoice = Some(ToolChoice.Required),
        settings = CreateRunSettings(),
        stream = false
      )

      _ = java.lang.Thread.sleep(10000)
      updatedRunOpt <- service.retrieveRun(eventsThread.id, run.id)
      updatedRun = updatedRunOpt.get
      toolCalls = updatedRun.required_action.get.submit_tool_outputs.tool_calls
      functionCalls = toolCalls.collect {
        case toolCall if toolCall.function.isInstanceOf[FunctionCallSpec] => toolCall
      }
      available_functions = Map("get_current_weather" -> getCurrentWeather _)

      toolMessages = functionCalls.map { toolCall =>
        val functionCallSpec = toolCall.function
        val functionName = functionCallSpec.name
        val functionArgsJson = Json.parse(functionCallSpec.arguments)
        val functionResponse = available_functions.get(functionName) match {
          case Some(functionToCall) =>
            functionToCall(
              (functionArgsJson \ "location").as[String],
              (functionArgsJson \ "unit").asOpt[String]
            )
          case None =>
            Json.obj("error" -> s"Function $functionName not found")
        }
        AssistantToolOutput(
          output = Option(functionResponse.toString),
          tool_call_id = toolCall.id
        )
      }
      _ <- service.submitToolOutputs(updatedRun.thread_id, updatedRun.id, toolMessages, stream = false)
      _ = java.lang.Thread.sleep(5000)
      finalMessages <- service.listThreadMessages(eventsThread.id)
    } yield {
      println(run)
      println(updatedRunOpt)
      updatedRunOpt.get.required_action.get.submit_tool_outputs.tool_calls.foreach {
        toolCall =>
          println(s"Tool call: ${toolCall.id}")
          println(toolCall)
      }
      println("Assistant answer:" + finalMessages.map(_.content).mkString("\n"))
    }

  val tools: Seq[FunctionTool] = Seq(
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
