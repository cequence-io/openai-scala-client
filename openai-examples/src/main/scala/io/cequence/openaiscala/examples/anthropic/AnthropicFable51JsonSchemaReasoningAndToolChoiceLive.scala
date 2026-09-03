package io.cequence.openaiscala.examples.anthropic

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.anthropic.service.impl.{
  toAnthropicSettings,
  toAnthropicToolChoice
}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  NonOpenAIModelId,
  SystemMessage,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra.OpenAIChatCompletionImplicits
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps.RichCreateChatCompletionSettings
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Smoke test for Claude Fable 5.1 (claude-fable-5-1):
 *   1. Network-free check of the reasoning-effort mapping - Fable 5.1 must use adaptive
 *      thinking + output_config.effort (incl. xhigh), drop temperature/top_p, and convert an
 *      explicit thinking budget to adaptive (budget_tokens returns 400 on Fable 5.1). 2. Live
 *      JSON-schema structured output. 3. Live reasoning_effort=xhigh completion. 4.
 *      Network-free + live check that forced tool_choice is downgraded to auto + a system
 *      instruction (Fable 5.1 rejects tool_choice type "tool"/"any" with a 400 - new vs. Fable
 *      5).
 *
 * Standalone main (no Example trait) so sbt does not swallow the output. Requires
 * ANTHROPIC_API_KEY.
 */
object AnthropicFable51JsonSchemaReasoningAndToolChoiceLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val model = NonOpenAIModelId.claude_fable_5_1

  private val weatherSchema: JsonSchema = JsonSchema.Object(
    properties = Seq(
      "response" -> JsonSchema.Array(
        items = JsonSchema.Object(
          properties = Seq(
            "city" -> JsonSchema.String(),
            "temperature" -> JsonSchema.String(),
            "weather" -> JsonSchema.String()
          ),
          required = Seq("city", "temperature", "weather")
        ),
        description = Some("The list MUST contain EXACTLY 2 city entries.")
      )
    ),
    required = Seq("response")
  )

  private def reportMapping(modelId: String): Unit = {
    println(s"\n=== mapping: $modelId ===")
    Seq(
      ReasoningEffort.none,
      ReasoningEffort.low,
      ReasoningEffort.medium,
      ReasoningEffort.high,
      ReasoningEffort.xhigh
    ).foreach { effort =>
      val anthropic = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = modelId,
          max_tokens = Some(10000),
          temperature = Some(0.2),
          top_p = Some(0.9),
          reasoning_effort = Some(effort)
        )
      )
      println(
        f"reasoning_effort=${effort.toString}%-8s -> thinking=${anthropic.thinking}%-40s " +
          s"output_config=${anthropic.output_config}, temperature=${anthropic.temperature}, top_p=${anthropic.top_p}"
      )
    }

    // explicit thinking budget must be converted to adaptive (budget_tokens 400s on Fable 5.1)
    val withBudget = toAnthropicSettings(
      CreateChatCompletionSettings(
        model = modelId,
        max_tokens = Some(10000)
      ).setAnthropicThinkingBudgetTokens(4096)
    )
    println(
      s"explicit budget=4096    -> thinking=${withBudget.thinking}, output_config=${withBudget.output_config}"
    )
  }

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory.asOpenAI()

    try {
      // 1. network-free mapping checks
      reportMapping(model)
      reportMapping(NonOpenAIModelId.bedrock_claude_fable_5_1)

      // 2. live JSON schema
      println(s"\n=== live: JSON schema ($model) ===")
      val json = Await.result(
        service.createChatCompletionWithJSON[JsObject](
          messages = Seq(
            SystemMessage("You are a helpful weather assistant that responds in JSON."),
            UserMessage("What is the weather like in Norway? List exactly two cities.")
          ),
          settings = CreateChatCompletionSettings(
            model = model,
            max_tokens = Some(16000),
            response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
            jsonSchema = Some(
              JsonSchemaDef(
                name = "weather_response",
                strict = true,
                structure = Left(weatherSchema)
              )
            )
          )
        ),
        5.minutes
      )
      println(Json.prettyPrint(json))

      // 3. live reasoning effort = xhigh (adaptive thinking + output_config.effort)
      println(s"\n=== live: reasoning_effort=xhigh ($model) ===")
      val response = Await.result(
        service.createChatCompletion(
          messages = Seq(
            SystemMessage("You are a concise assistant."),
            UserMessage("In one sentence: why is the sky blue?")
          ),
          settings = CreateChatCompletionSettings(
            model = model,
            max_tokens = Some(8000),
            temperature = Some(0.5), // must be dropped, not sent (400 otherwise)
            reasoning_effort = Some(ReasoningEffort.xhigh)
          )
        ),
        5.minutes
      )
      println(response.contentHead)
      println(s"Usage: ${response.usage}")

      // 4. forced tool_choice is removed on Fable 5.1 - offline + live check of the downgrade
      println(s"\n=== mapping: toAnthropicToolChoice ($model) ===")
      val (toolChoice, extraSystemMessages) =
        toAnthropicToolChoice(model, Some("get_weather"), None)
      println(s"tool_choice=$toolChoice, extraSystemMessages=$extraSystemMessages")

      println(s"\n=== live: forced tool_choice downgrade ($model) ===")
      val toolResponse = Await.result(
        service.createChatToolCompletion(
          messages = Seq(
            UserMessage("What's the weather in Bergen right now? Use the tool.")
          ),
          tools = Seq(
            FunctionTool(
              name = "get_weather",
              description = Some("Get current weather for a city"),
              parameters = JsonSchema.Object(
                properties = Seq("city" -> JsonSchema.String()),
                required = Seq("city")
              )
            )
          ),
          responseToolChoice = Some("get_weather"),
          settings = CreateChatCompletionSettings(model = model, max_tokens = Some(4000))
        ),
        5.minutes
      )
      val toolCalls = toolResponse.choices.head.message.tool_calls
      println(s"Tool calls: $toolCalls")
      require(
        toolCalls.nonEmpty,
        "Expected at least one tool call from the forced tool_choice downgrade"
      )

      println("\nAll Fable 5.1 smoke tests passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
