package io.cequence.openaiscala.examples.anthropic

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.anthropic.service.impl.toAnthropicSettings
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
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Smoke test for Claude Sonnet 5 (claude-sonnet-5) through the OpenAI adapter, exercising both
 * transports:
 *   1. Network-free check of the reasoning-effort mapping - Sonnet 5 must use adaptive
 *      thinking + output_config.effort (incl. xhigh, the first Sonnet-tier model to support
 *      it), drop temperature/top_p, and convert an explicit thinking budget to adaptive
 *      (budget_tokens returns 400 on Sonnet 5). 2. Live JSON-schema structured output. 3. Live
 *      reasoning_effort=xhigh completion. Each live step runs against vanilla Anthropic and EU
 *      Bedrock (eu.anthropic.claude-sonnet-5).
 *
 * Standalone main (no Example trait) so sbt does not swallow the output. Requires
 * ANTHROPIC_API_KEY for the vanilla path and AWS_BEDROCK_ACCESS_KEY / AWS_BEDROCK_SECRET_KEY /
 * AWS_BEDROCK_REGION (an EU region) for the Bedrock path.
 *
 * Note: the Bedrock leg only succeeds once Sonnet 5 is provisioned on Amazon Bedrock in the
 * target region - until then Bedrock returns 400 "The provided model identifier is invalid"
 * (the adapter, credentials, and routing are still exercised correctly; verified against a
 * sibling model that is available).
 */
object AnthropicSonnet5JsonSchemaAndReasoningLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private val vanillaModel = NonOpenAIModelId.claude_sonnet_5
  private val bedrockModel = "eu." + NonOpenAIModelId.bedrock_claude_sonnet_5

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

    // explicit thinking budget must be converted to adaptive (budget_tokens 400s on Sonnet 5)
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

  private def runLive(
    label: String,
    service: OpenAIChatCompletionService,
    model: String
  ): Unit = {
    // JSON schema
    println(s"\n=== live [$label]: JSON schema ($model) ===")
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

    // reasoning effort = xhigh (adaptive thinking + output_config.effort)
    println(s"\n=== live [$label]: reasoning_effort=xhigh ($model) ===")
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
  }

  def main(args: Array[String]): Unit = {
    val vanilla = AnthropicServiceFactory.asOpenAI()
    val bedrock = AnthropicServiceFactory.bedrockAsOpenAI()

    try {
      // 1. network-free mapping checks (direct + Bedrock ids)
      reportMapping(vanillaModel)
      reportMapping(bedrockModel)

      // 2. + 3. live JSON schema + reasoning on both transports
      runLive("vanilla", vanilla, vanillaModel)
      runLive("bedrock-eu", bedrock, bedrockModel)

      println("\nAll Sonnet 5 smoke tests passed.")
    } finally {
      vanilla.close()
      bedrock.close()
      system.terminate()
    }
  }
}
