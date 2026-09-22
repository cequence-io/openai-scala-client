package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response.ChatToolCompletionResponse
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for GPT-6 Sol / Luna (`gpt-6-sol`, `gpt-6-luna`) and Claude Opus 5.5
 * (`claude-opus-5-5`), covering what the client adapts for them (live-verified 2026-09-22):
 *
 *   - GPT-6 Sol/Luna follow the GPT-5.6 rules, NOT Astra's: sampling params stripped,
 *     `max_tokens` -> `max_completion_tokens`, `reasoning_effort` `max` -> `xhigh` and
 *     `minimal` -> `low` while `none` is kept. Function tools (sync and typed streamed) go
 *     through the Responses API, which keeps reasoning with tools (any effort incl. `max`;
 *     `minimal` -> `low`; temperature / top_p dropped) - only `reasoning_effort = none` stays
 *     on the chat completions API, the one effort it accepts with tools
 *   - Opus 5.5: adaptive thinking + `output_config.effort` only, no sampling params, and a
 *     forced tool choice downgraded to `auto` plus a system instruction (the API rejects
 *     `tool_choice` `any`/`tool`)
 *   - every model: `createChatCompletionWithJSON` in json_schema mode (also with high
 *     reasoning; numeric `minimum`/`maximum` and `enum` - OpenAI strict mode honours both,
 *     Anthropic rejects the bounds so the adapter strips them and only `enum` is enforced),
 *     json_schema via the Responses API for GPT-6, prompt-mode JSON for Opus 5.5 on Bedrock
 *     (which rejects `output_config.format`), function tools (sync and typed streamed)
 *
 * Every section prints PASS/FAIL and the run continues; the exit code is 1 if any failed.
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `ANTHROPIC_API_KEY`.
 */
object GPT6SolLunaOpus55SmokeTest {

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

  private case class Rating(stars: Int)

  private implicit val ratingFormat: Format[Rating] = Json.format[Rating]

  // a deliberately unusual range - an in-range answer means the bound was actually read
  private def ratingSchema(stars: JsonSchema) = JsonSchemaDef(
    name = "rating",
    strict = true,
    structure =
      Left(JsonSchema.Object(properties = Seq("stars" -> stars), required = Seq("stars")))
  )

  private val boundedStars =
    JsonSchema.Integer(Some("The rating"), minimum = Some(30), maximum = Some(40))

  private val enumStars = JsonSchema.Integer(Some("The rating"), `enum` = Seq(30L, 35L, 40L))

  private val ratingQuestion =
    Seq(UserMessage("Rate this hotel: 'The room was clean and the staff friendly.'"))

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

    val openAI = OpenAIServiceFactory.withStreaming()
    val openAIViaResponses = OpenAIResponsesChatCompletionService(openAI)
    val anthropic = AnthropicServiceFactory.asOpenAI()

    // the Opus and GPT-6 runs are concurrent
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

    // shared by both providers
    def common(
      service: OpenAIChatCompletionStreamedService,
      model: String
    ): Future[Unit] = for {
      _ <- section(s"$model: createChatCompletionWithJSON[Capital] (json_schema)") {
        service
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

      _ <- section(s"$model: json_schema + reasoning_effort=high") {
        service
          .createChatCompletionWithJSON[Capital](
            Seq(UserMessage("Capital of the country north of Denmark across the Skagerrak?")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(8000),
              reasoning_effort = Some(ReasoningEffort.high),
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(c => s"parsed=$c")
      }

      _ <- section(s"$model: numeric bounds 30..40 / enum {30,35,40}") {
        def rate(stars: JsonSchema) =
          service.createChatCompletionWithJSON[Rating](
            ratingQuestion,
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(2000),
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(ratingSchema(stars))
            )
          )

        for {
          bounded <- rate(boundedStars)
          enumerated <- rate(enumStars)
        } yield {
          val inRange = bounded.stars >= 30 && bounded.stars <= 40
          if (!Seq(30, 35, 40).contains(enumerated.stars))
            throw new IllegalStateException(s"enum not honoured: $enumerated")
          s"bounded=${bounded.stars} (${if (inRange) "honoured" else "not enforced"}) enum=${enumerated.stars}"
        }
      }

      _ <- section(s"$model: typed streamed tool call") {
        service
          .createChatToolCompletionStreamed(
            weatherQuestion,
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(model = model, max_tokens = Some(2000))
          )
          .assembled
          .map { a =>
            if (a.toolCalls.isEmpty) throw new IllegalStateException(s"no tool call: $a")
            a.toolCalls.map(c => s"${c.toolName}(${c.arguments})").mkString(", ") +
              s" finish=${a.finishReason}"
          }
      }
    } yield ()

    def gpt6(model: String): Future[Unit] = for {
      _ <- section(s"$model: sampling params + max_tokens + reasoning_effort=max converted") {
        openAI
          .createChatCompletion(
            Seq(UserMessage("What is 17*23? Answer with the number only.")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(2000),
              temperature = Some(0.2),
              top_p = Some(0.5),
              presence_penalty = Some(0.5),
              frequency_penalty = Some(0.5),
              logprobs = Some(true),
              reasoning_effort = Some(ReasoningEffort.max)
            )
          )
          .map { r =>
            val reasoningTokens =
              r.usage.flatMap(_.completion_tokens_details).flatMap(_.reasoning_tokens)
            s"content='${r.contentHead}' reasoning_tokens=$reasoningTokens"
          }
      }

      _ <- section(s"$model: reasoning_effort=none kept, minimal -> low") {
        for {
          none <- openAI.createChatCompletion(
            Seq(UserMessage("Say hi")),
            CreateChatCompletionSettings(model, reasoning_effort = Some(ReasoningEffort.none))
          )
          minimal <- openAI.createChatCompletion(
            Seq(UserMessage("Say hi")),
            CreateChatCompletionSettings(
              model,
              reasoning_effort = Some(ReasoningEffort.minimal)
            )
          )
        } yield s"none='${none.contentHead}' minimal='${minimal.contentHead}'"
      }

      _ <- section(
        s"$model: tools with reasoning_effort=high + temperature (routed to the Responses API)"
      ) {
        openAI
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            settings = CreateChatCompletionSettings(
              model = model,
              temperature = Some(0.2),
              reasoning_effort = Some(ReasoningEffort.high)
            )
          )
          .map { r =>
            val reasoningTokens =
              r.usage.flatMap(_.completion_tokens_details).flatMap(_.reasoning_tokens)
            s"${functionCalls(r)} reasoning_tokens=$reasoningTokens"
          }
      }

      _ <- section(s"$model: tools with reasoning_effort=none (stays on chat completions)") {
        openAI
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            settings = CreateChatCompletionSettings(
              model = model,
              reasoning_effort = Some(ReasoningEffort.none)
            )
          )
          .map(r => s"${functionCalls(r)} id=${r.id.take(9)}")
      }

      _ <- section(
        s"$model: typed streamed tools with reasoning_effort=high (Responses API)"
      ) {
        openAI
          .createChatToolCompletionStreamed(
            weatherQuestion,
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(
              model = model,
              reasoning_effort = Some(ReasoningEffort.high)
            )
          )
          .assembled
          .map { a =>
            if (a.toolCalls.isEmpty) throw new IllegalStateException(s"no tool call: $a")
            val reasoningTokens =
              a.usage.flatMap(_.completion_tokens_details).flatMap(_.reasoning_tokens)
            a.toolCalls.map(c => s"${c.toolName}(${c.arguments})").mkString(", ") +
              s" reasoning_tokens=$reasoningTokens id=${a.id.getOrElse("").take(9)}"
          }
      }

      _ <- section(s"$model: forced tool choice") {
        openAI
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            responseToolChoice = Some(weatherTool.name),
            settings = CreateChatCompletionSettings(model = model)
          )
          .map(functionCalls)
      }

      _ <- section(s"$model: Responses API tools keep reasoning_effort=high") {
        openAIViaResponses
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            settings = CreateChatCompletionSettings(
              model = model,
              reasoning_effort = Some(ReasoningEffort.high)
            )
          )
          .map(functionCalls)
      }

      _ <- section(
        s"$model: Responses API tools with reasoning_effort=max (kept) / minimal (-> low)"
      ) {
        def viaResponses(effort: ReasoningEffort) =
          openAIViaResponses
            .createChatToolCompletion(
              weatherQuestion,
              Seq(weatherTool),
              settings = CreateChatCompletionSettings(model, reasoning_effort = Some(effort))
            )
            .map(functionCalls)

        for {
          max <- viaResponses(ReasoningEffort.max)
          minimal <- viaResponses(ReasoningEffort.minimal)
        } yield s"max=$max minimal=$minimal"
      }

      _ <- section(s"$model: json_schema via the Responses API (reasoning_effort=max)") {
        openAIViaResponses
          .createChatCompletionWithJSON[Capital](
            Seq(UserMessage("Capital of Norway?")),
            CreateChatCompletionSettings(
              model = model,
              reasoning_effort = Some(ReasoningEffort.max),
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(c => s"parsed=$c")
      }

      _ <- common(openAI, model)
    } yield ()

    val opus = NonOpenAIModelId.claude_opus_5_5

    val opus55 = for {
      _ <- section(s"$opus: temperature/top_p dropped, reasoning_effort=xhigh -> adaptive") {
        anthropic
          .createChatCompletion(
            Seq(UserMessage("What is 17*23? Answer with the number only.")),
            CreateChatCompletionSettings(
              model = opus,
              max_tokens = Some(4000),
              temperature = Some(0.2),
              top_p = Some(0.5),
              reasoning_effort = Some(ReasoningEffort.xhigh)
            )
          )
          .map(r => s"content='${r.contentHead}'")
      }

      _ <- section(s"$opus: reasoning_effort=none (no thinking sent)") {
        anthropic
          .createChatCompletion(
            Seq(UserMessage("Say hi")),
            CreateChatCompletionSettings(
              model = opus,
              max_tokens = Some(200),
              reasoning_effort = Some(ReasoningEffort.none)
            )
          )
          .map(r => s"content='${r.contentHead}'")
      }

      _ <- section(s"$opus: chat tools (auto)") {
        anthropic
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            settings = CreateChatCompletionSettings(model = opus, max_tokens = Some(2000))
          )
          .map(functionCalls)
      }

      _ <- section(s"$opus: forced tool choice (downgraded to auto + instruction)") {
        anthropic
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            responseToolChoice = Some(weatherTool.name),
            settings = CreateChatCompletionSettings(model = opus, max_tokens = Some(2000))
          )
          .map(functionCalls)
      }

      _ <- common(anthropic, opus)

      // Bedrock rejects output_config.format for Opus 5.5, so its id is not in
      // models-supporting-json-schema and the helper falls back to prompt-based JSON mode
      _ <- section(s"eu.${NonOpenAIModelId.bedrock_claude_opus_5_5}: JSON (prompt mode)") {
        if (sys.env.get("AWS_BEDROCK_ACCESS_KEY").forall(_.isEmpty))
          Future.successful("skipped - AWS_BEDROCK_ACCESS_KEY not set")
        else {
          val bedrock = AnthropicServiceFactory.bedrockAsOpenAI(region = "eu-central-1")
          bedrock
            .createChatCompletionWithJSON[Capital](
              Seq(UserMessage("Capital of Norway?")),
              CreateChatCompletionSettings(
                model = "eu." + NonOpenAIModelId.bedrock_claude_opus_5_5,
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

    val all = for {
      _ <- gpt6(ModelId.gpt_6_luna)
      _ <- gpt6(ModelId.gpt_6_sol)
      _ <- opus55
    } yield ()

    Try(Await.result(all, 20.minutes))
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    openAI.close()
    anthropic.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
