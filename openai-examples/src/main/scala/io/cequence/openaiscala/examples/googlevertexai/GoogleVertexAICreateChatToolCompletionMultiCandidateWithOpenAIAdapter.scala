package io.cequence.openaiscala.examples.googlevertexai

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.ChatToolCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.examples.{ChatCompletionProvider, ExampleBase}
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

// requires `openai-scala-google-vertexai-client` as a dependency and `VERTEXAI_LOCATION` and `VERTEXAI_PROJECT_ID` env vars
//
// Exercises the tool-path fixes in toOpenAIToolResponse:
//   - bug_001: parallel calls to the same function must get distinct tool_call_ids
//              (the question forces 3 parallel calls to `get_current_weather`).
//   - bug_012: candidateCount > 1 (n = Some(2)) must yield one choice per candidate,
//              not a single merged choice.
object GoogleVertexAICreateChatToolCompletionMultiCandidateWithOpenAIAdapter
    extends ExampleBase[OpenAIChatCompletionService] {

  override val service: OpenAIChatCompletionService = ChatCompletionProvider.vertexAI

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
          model = NonOpenAIModelId.gemini_2_5_flash,
          n = Some(2)
        )
      )
      .map(reportResponse)

  private def reportResponse(response: ChatToolCompletionResponse): Unit = {
    println(s"Got ${response.choices.size} choice(s) (expected 2 with n=2)")

    response.choices.foreach { choice =>
      println(s"\n--- choice index=${choice.index} finish_reason=${choice.finish_reason} ---")

      val toolCalls = choice.message.tool_calls.collect { case (id, x: FunctionCallSpec) =>
        (id, x)
      }
      println(s"  tool call ids   : ${toolCalls.map(_._1).mkString(", ")}")
      println(s"  tool call names : ${toolCalls.map(_._2.name).mkString(", ")}")
      println(s"  tool call args  : ${toolCalls.map(_._2.arguments).mkString(" | ")}")

      val ids = toolCalls.map(_._1)
      val unique = ids.toSet
      if (ids.size != unique.size)
        println(
          s"  !!! tool_call_id collision detected (${ids.size} calls, ${unique.size} unique)"
        )
      else
        println(s"  OK: ${ids.size} call(s), all ids unique")
    }
  }
}
