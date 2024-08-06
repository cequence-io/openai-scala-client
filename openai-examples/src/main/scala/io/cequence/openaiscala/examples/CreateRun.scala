package io.cequence.openaiscala.examples
import io.cequence.openaiscala.domain
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.Assistant
import io.cequence.openaiscala.domain.settings.CreateRunSettings
import io.cequence.openaiscala.domain.{ModelId, ThreadMessage}

import scala.collection.immutable.ListMap
import scala.concurrent.Future

object CreateRun extends Example {

  def createPlanner: Future[Assistant] = for {
    assistant <- service.createAssistant(
      model = ModelId.gpt_4o,
      name = Some("Schedule planner"),
      instructions = Some(
        "You plan my week."
      ),
      tools = Seq(
        FunctionTool("name", description = None, Map())
      ),
      toolResources = Seq()
    )
  } yield assistant

  def createEventMessages: Future[domain.Thread] =
    for {
      thread <- service.createThread(
        messages = Seq(
          ThreadMessage("My mom wants to have dinner on Friday"),
          ThreadMessage("I want to play soccer during the weekend. It should be sunny.")
        ),
        metadata = Map("user_id" -> "986413")
      )
    } yield thread

  override protected def run: Future[_] =
    for {
      assistant <- createPlanner
      eventsThread <- createEventMessages
      run <- service.createRun(
        threadId = eventsThread.id,
        assistantId = assistant.id,
        instructions = Some(
          "If you need the weather forecast for a specific city and date, you can use the weather_forecast_for_city function."
        ),
        tools = Seq(
          FunctionTool(
            "weather_forecast_for_city",
            description =
              Some("returns the weather forecast for a given day in the given city"),
            ListMap(
              "type" -> "object",
              "properties" -> ListMap(
                "city" -> ListMap("type" -> "string", "description" -> "The city name"),
                "date" -> ListMap(
                  "type" -> "string",
                  "description" -> "The date in format dd-mm-yyyy"
                )
              )
            )
          )
        ),
        responseToolChoice = None,
        settings = CreateRunSettings(
          model = Some(ModelId.gpt_4o),
          metadata = Map(
            "user_id" -> "986413"
          ),
          temperature = Some(0.5),
          topP = Some(1.0),
          maxPromptTokens = Some(2048),
          maxCompletionTokens = Some(2048),
          responseFormat = None
        ),
        stream = false
      )
    } yield println(run)
}
