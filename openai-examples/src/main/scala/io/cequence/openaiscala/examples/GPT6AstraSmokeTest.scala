package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatToolCompletionResponse
import io.cequence.openaiscala.domain.responsesapi.tools.Tool
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.MCPRequireApproval
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  ReasoningConfig
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIServiceFactory
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.adapter.OpenAIResponsesChatCompletionService
import play.api.libs.json.{Format, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for GPT-6 Astra (`gpt-6-astra`) covering everything the client adapts for
 * the model (all live-verified 2026-09-05):
 *
 *   - chat completions: parameter conversions (sampling params stripped, `max_tokens` →
 *     `max_completion_tokens`, `reasoning_effort` `max`→`xhigh`, `none`/`minimal`→`low`)
 *   - structured output: native `json_schema`, `json_object`, and
 *     `createChatCompletionWithJSON`
 *   - function tools: NOT supported on the chat completions API for gpt-6, so the full
 *     `OpenAIService` routes `createChatToolCompletion` through the Responses API
 *   - streaming
 *   - Responses API: `reasoning_effort=max`, structured output via the chat-completion
 *     adapter, function tools, an MCP tool (DeepWiki over streamable HTTP - the `/sse`
 *     endpoints are rejected by OpenAI's connector with 424), and web search
 *   - Batch API (slow: a two-request batch takes a few minutes) - pass `--skip-batch` to omit
 *
 * Every section prints PASS/FAIL and the run continues; the exit code is 1 if any failed.
 * Requires `OPENAI_SCALA_CLIENT_API_KEY`.
 */
object GPT6AstraSmokeTest {

  private val model = ModelId.gpt_6_astra

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

  private def functionCalls(response: ChatToolCompletionResponse): Seq[String] =
    response.choices.head.message.tool_calls.collect { case (_, fc: FunctionCallSpec) =>
      s"${fc.name}(${fc.arguments})"
    }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val skipBatch = args.contains("--skip-batch")

    val service = OpenAIServiceFactory.withStreaming()
    val viaResponses = OpenAIResponsesChatCompletionService(service)

    var failures = 0

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
          failures += 1
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage.take(400)}")
          Success(())
      }
    }

    val all = for {
      _ <- section("chat: sampling params + max_tokens + reasoning_effort=none converted") {
        service
          .createChatCompletion(
            Seq(UserMessage("Say hi")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(200),
              temperature = Some(0.2),
              top_p = Some(0.5),
              presence_penalty = Some(0.5),
              frequency_penalty = Some(0.5),
              logprobs = Some(true),
              reasoning_effort = Some(ReasoningEffort.none)
            )
          )
          .map(r => s"content='${r.contentHead}' finish=${r.choices.head.finish_reason}")
      }

      _ <- section("chat: reasoning_effort=max downgraded to xhigh") {
        service
          .createChatCompletion(
            Seq(UserMessage("What is 17*23? Answer with the number only.")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(2000),
              reasoning_effort = Some(ReasoningEffort.max)
            )
          )
          .map { r =>
            val reasoningTokens =
              r.usage.flatMap(_.completion_tokens_details).flatMap(_.reasoning_tokens)
            s"content='${r.contentHead}' reasoning_tokens=$reasoningTokens"
          }
      }

      _ <- section("chat: native json_schema") {
        service
          .createChatCompletion(
            Seq(UserMessage("Capital of Norway?")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(500),
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(r => s"content=${Json.parse(r.contentHead).as[Capital]}")
      }

      _ <- section("chat: json_object") {
        service
          .createChatCompletion(
            Seq(
              UserMessage(
                "Return a JSON object with the capital of Norway under key 'capital'."
              )
            ),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(500),
              response_format_type = Some(ChatCompletionResponseFormatType.json_object)
            )
          )
          .map(r => s"content=${Json.parse(r.contentHead)}")
      }

      _ <- section("chat: createChatCompletionWithJSON[Capital]") {
        service
          .createChatCompletionWithJSON[Capital](
            Seq(UserMessage("Capital of Sweden?")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(500),
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(c => s"parsed=$c")
      }

      _ <- section("chat: function tool (auto-routed through the Responses API)") {
        service
          .createChatToolCompletion(
            Seq(UserMessage("What's the weather in Oslo?")),
            tools = Seq(weatherTool),
            settings = CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(500),
              reasoning_effort = Some(ReasoningEffort.high)
            )
          )
          .map(r => s"toolCalls=${functionCalls(r)} finish=${r.choices.head.finish_reason}")
      }

      _ <- section("chat: forced function tool") {
        service
          .createChatToolCompletion(
            Seq(UserMessage("Hello there")),
            tools = Seq(weatherTool),
            responseToolChoice = Some("get_weather"),
            settings = CreateChatCompletionSettings(model = model, max_tokens = Some(500))
          )
          .map(r => s"toolCalls=${functionCalls(r)}")
      }

      _ <- section("chat: streamed") {
        service
          .createChatCompletionStreamed(
            Seq(UserMessage("Count from 1 to 5, comma separated.")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(300),
              reasoning_effort = Some(ReasoningEffort.low)
            )
          )
          .runWith(Sink.seq)
          .map { chunks =>
            val text = chunks.flatMap(_.contentHead).mkString
            val finish = chunks.flatMap(_.choices.flatMap(_.finish_reason)).lastOption
            s"chunks=${chunks.size} text='$text' finish=$finish"
          }
      }

      _ <- section("responses: reasoning_effort=max") {
        service
          .createModelResponse(
            Inputs.Text("What is the capital of Norway? One word."),
            CreateModelResponseSettings(
              model = model,
              reasoning = Some(ReasoningConfig(effort = Some(ReasoningEffort.max))),
              maxOutputTokens = Some(4000)
            )
          )
          .map { r =>
            val reasoningTokens = r.usage.flatMap(_.outputTokensDetails).map(_.reasoningTokens)
            s"text=${r.outputText} reasoning_tokens=$reasoningTokens"
          }
      }

      _ <- section("responses: json_schema via the chat-completion adapter") {
        viaResponses
          .createChatCompletion(
            Seq(UserMessage("Capital of Finland?")),
            CreateChatCompletionSettings(
              model = model,
              max_tokens = Some(2000),
              reasoning_effort = Some(ReasoningEffort.max),
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(r => s"content=${Json.parse(r.contentHead).as[Capital]}")
      }

      _ <- section("responses: function tool") {
        service
          .createModelResponse(
            Inputs.Text("What's the weather in Oslo?"),
            CreateModelResponseSettings(
              model = model,
              tools = Seq(
                Tool.function(
                  name = "get_weather",
                  parameters = JsonSchema.Object(
                    properties = Seq("city" -> JsonSchema.String()),
                    required = Seq("city")
                  ),
                  description = Some("Get weather")
                )
              )
            )
          )
          .map(r =>
            s"functionCalls=${r.outputFunctionCalls.map(f => s"${f.name}(${f.arguments})")}"
          )
      }

      _ <- section("responses: MCP tool (DeepWiki, streamable HTTP)") {
        service
          .createModelResponse(
            Inputs.Text(
              "Using the deepwiki tool, tell me in one sentence what the repository scala/scala is."
            ),
            CreateModelResponseSettings(
              model = model,
              tools = Seq(
                Tool.mcp(
                  serverLabel = "deepwiki",
                  serverUrl = Some("https://mcp.deepwiki.com/mcp"),
                  requireApproval = Some(MCPRequireApproval.Setting.Never)
                )
              )
            )
          )
          .map { r =>
            val kinds = r.output.map(_.getClass.getSimpleName).distinct
            s"outputs=${kinds.mkString(",")} text=${r.outputText.map(_.take(160))}"
          }
      }

      _ <- section("responses: web search") {
        service
          .createModelResponse(
            Inputs.Text("What is today's date according to a web search? One line."),
            CreateModelResponseSettings(model = model, tools = Seq(Tool.webSearch()))
          )
          .map { r =>
            val kinds = r.output.map(_.getClass.getSimpleName).distinct
            s"outputs=${kinds.mkString(",")} text=${r.outputText.map(_.take(120))}"
          }
      }

      _ <-
        if (skipBatch) Future.successful(println("[SKIP] batch (--skip-batch)"))
        else
          section("batch: two chat completions (polls every 20s, up to 15 min)") {
            service
              .createChatCompletionBatchAndWaitForResults(
                Seq(
                  ChatCompletionBatchRequest(
                    "norway",
                    Seq(UserMessage("Capital of Norway? One word."))
                  ),
                  ChatCompletionBatchRequest(
                    "sweden",
                    Seq(UserMessage("Capital of Sweden? One word."))
                  )
                ),
                settings = CreateChatCompletionSettings(model = model, max_tokens = Some(100)),
                pollingInterval = 20.seconds,
                timeout = Some(15.minutes),
                deleteBatchAfterUse = true
              )
              .map(_.map { item =>
                s"${item.customId}=${item.result.fold(e => "ERROR " + e.message, _.contentHead)}"
              }.mkString("; "))
          }
    } yield ()

    try Await.result(all, 20.minutes)
    finally {
      service.close()
      Await.result(system.terminate(), 10.seconds)
    }

    println(if (failures == 0) "ALL PASSED" else s"$failures FAILED")
    if (failures > 0) System.exit(1)
  }
}
