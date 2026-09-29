package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatToolCompletionResponse
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for Claude Sonnet 5.5 (`claude-sonnet-5-5`) through the OpenAI adapter,
 * covering what the client adapts for it (live-verified 2026-09-29):
 *
 *   - adaptive thinking + `output_config.effort` (low..max), no sampling params (temperature /
 *     top_p are dropped)
 *   - `reasoning_effort = none` -> `thinking.type = between_tools`, its lowest setting (it
 *     rejects `disabled`, and omitting thinking would mean adaptive thinking at effort high) -
 *     sent without a `display`, which that setting rejects, also on the typed stream
 *   - a forced tool choice downgraded to `auto` plus a system instruction (the API rejects
 *     `tool_choice` `any`/`tool`, like Opus 5.5)
 *   - json_schema structured output on the Claude API; prompt-mode JSON on Bedrock, where only
 *     the `global.` inference profile serves it and `output_config.format` is rejected
 *
 * Every section prints PASS/FAIL and the run continues; the exit code is 1 if any failed.
 * Requires `ANTHROPIC_API_KEY`; the Bedrock section also a Bedrock API key in
 * `AWS_BEARER_TOKEN_BEDROCK` (skipped otherwise).
 */
object ClaudeSonnet55SmokeTest {

  private case class Capital(
    country: String,
    capital: String
  )

  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  private val capitalSchema = JsonSchemaDef(
    name = "capital",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "country" -> JsonSchema.String(),
          "capital" -> JsonSchema.String()
        ),
        required = Seq("country", "capital")
      )
    )
  )

  private val weatherTool = FunctionTool(
    name = "get_weather",
    description = Some("Get the current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String(description = Some("City name"))),
      required = Seq("city")
    )
  )

  private val weatherQuestion = Seq(UserMessage("What's the weather in Paris right now?"))

  private def functionCalls(response: ChatToolCompletionResponse): String =
    response.choices.head.message.tool_calls.collect { case (_, fc: FunctionCallSpec) =>
      s"${fc.name}(${fc.arguments})"
    }.mkString(", ") match {
      case ""    => throw new IllegalStateException("no tool call")
      case calls => calls
    }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val anthropic = AnthropicServiceFactory.asOpenAI()
    val model = NonOpenAIModelId.claude_sonnet_5_5
    val failures = new AtomicInteger(0)

    def section(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] = {
      val start = System.currentTimeMillis()
      Try(f).fold(Future.failed, identity).transform {
        case Success(msg) =>
          println(s"[PASS] $name (${System.currentTimeMillis() - start} ms): $msg")
          Success(())
        case Failure(e) =>
          failures.incrementAndGet()
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage.take(400)}")
          Success(())
      }
    }

    val all = for {
      _ <- section(s"$model: temperature/top_p dropped, reasoning_effort=xhigh -> adaptive") {
        anthropic
          .createChatCompletion(
            Seq(UserMessage("What is 17*23? Answer with the number only.")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(4000),
              temperature = Some(0.2),
              top_p = Some(0.5),
              reasoning_effort = Some(ReasoningEffort.xhigh)
            )
          )
          .map(r => s"content='${r.contentHead}'")
      }

      _ <- section(s"$model: reasoning_effort=none -> thinking between_tools") {
        anthropic
          .createChatCompletion(
            Seq(UserMessage("Say hi")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(200),
              reasoning_effort = Some(ReasoningEffort.none)
            )
          )
          .map(r => s"content='${r.contentHead}'")
      }

      _ <- section(s"$model: typed stream, reasoning_effort=none + tool (no display sent)") {
        anthropic
          .createChatToolCompletionStreamed(
            weatherQuestion,
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(2000),
              reasoning_effort = Some(ReasoningEffort.none)
            )
          )
          .assembled
          .map { a =>
            if (a.toolCalls.isEmpty) throw new IllegalStateException(s"no tool call: $a")
            s"calls=${a.toolCalls.map(_.toolName)} thinking=${a.thinking.length} chars finish=${a.finishReason}"
          }
      }

      _ <- section(s"$model: typed stream, reasoning_effort=max (summarized thinking)") {
        anthropic
          .createChatCompletionStreamedTyped(
            Seq(UserMessage("Is 391 prime? One sentence.")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(8000),
              reasoning_effort = Some(ReasoningEffort.max)
            )
          )
          .assembled
          .map(a => s"thinking=${a.thinking.length} chars text='${a.text.take(80)}'")
      }

      _ <- section(s"$model: chat tools (auto)") {
        anthropic
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            settings = CreateChatCompletionSettings(model = model, max_tokens = Some(2000))
          )
          .map(functionCalls)
      }

      _ <- section(s"$model: forced tool choice (downgraded to auto + instruction)") {
        anthropic
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            responseToolChoice = Some(weatherTool.name),
            settings = CreateChatCompletionSettings(model = model, max_tokens = Some(2000))
          )
          .map(functionCalls)
      }

      _ <- section(s"$model: createChatCompletionWithJSON[Capital] (json_schema)") {
        anthropic
          .createChatCompletionWithJSON[Capital](
            Seq(UserMessage("Capital of Norway?")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(2000),
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(c => s"parsed=$c")
      }

      // Bedrock serves Sonnet 5.5 through the global. profile only and rejects
      // output_config.format, so the helper falls back to prompt-based JSON mode
      _ <- section(
        s"global.${NonOpenAIModelId.bedrock_claude_sonnet_5_5}: JSON (prompt mode)"
      ) {
        if (sys.env.get("AWS_BEARER_TOKEN_BEDROCK").forall(_.isEmpty))
          Future.successful("skipped - AWS_BEARER_TOKEN_BEDROCK not set")
        else {
          val bedrock =
            AnthropicServiceFactory.bedrockAsOpenAIWithBearerToken(region = "eu-central-1")
          bedrock
            .createChatCompletionWithJSON[Capital](
              Seq(UserMessage("Capital of Norway?")),
              CreateChatCompletionSettings(
                model = "global." + NonOpenAIModelId.bedrock_claude_sonnet_5_5,
                max_tokens = Some(2000),
                response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
                jsonSchema = Some(capitalSchema)
              )
            )
            .map(c => s"parsed=$c")
            .andThen { case _ => bedrock.close() }
        }
      }
    } yield ()

    Try(Await.result(all, 15.minutes))
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    anthropic.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
