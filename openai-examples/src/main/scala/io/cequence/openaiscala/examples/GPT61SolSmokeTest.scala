package io.cequence.openaiscala.examples

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatToolCompletionResponse
import io.cequence.openaiscala.domain.responsesapi.{
  CreateModelResponseSettings,
  Inputs,
  MultiAgentCall,
  MultiAgentConfig,
  ReasoningConfig,
  ReasoningMode
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort,
  ServiceTier
}
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{BedrockAuth, BedrockEndpoint, OpenAIServiceFactory}
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for GPT-6.1 Sol (`gpt-6.1-sol`, DevDay 2026-09-29) and the service tiers /
 * reasoning mode that came with it, covering what the client adapts (live-verified
 * 2026-09-29):
 *
 *   - GPT-6.1 Sol follows the GPT-6 ASTRA rules, not GPT-6 Sol's: reasoning is always on
 *     (`reasoning_effort` `none` / `minimal` -> `low`, `max` -> `xhigh` on chat completions
 *     and kept on the Responses API), sampling params stripped, `max_tokens` ->
 *     `max_completion_tokens`, and function tools go through the Responses API with ANY effort
 *     (the chat completions API rejects them)
 *   - `setResponsesReasoningMode(ReasoningMode.pro)` routes a chat call through the Responses
 *     API with `reasoning.mode = pro`
 *   - the `fast` / `priority` tier (Fast mode - a GPT-6 response reports `fast`) and the
 *     access-controlled `ultrafast` tier, which only the Responses API serves (GPT-6 Astra) -
 *     the full service routes it there
 *   - server-hosted multi-agent execution (beta): `CreateModelResponseSettings.multiAgent` on
 *     the Responses API and `setResponsesMultiAgent` from the chat interface (the typed stream
 *     shows the delegation as `multi_agent.*` server-side tool calls, the text is the root
 *     agent's answer only)
 *   - Bedrock's `global.openai.gpt-6.1-sol` (bedrock-runtime), incl. its tool routing
 *
 * Every section prints PASS/FAIL and the run continues; the exit code is 1 if any failed or
 * the run did not complete. Requires `OPENAI_SCALA_CLIENT_API_KEY`; the Bedrock section also a
 * Bedrock API key in `AWS_BEARER_TOKEN_BEDROCK` (skipped otherwise; region from
 * `AWS_BEDROCK_REGION`, default eu-central-1).
 */
object GPT61SolSmokeTest {

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

  private val multiAgentTask =
    "Use two subagents in parallel: one lists three fruits, the other three vegetables (one " +
      "short line each). Then reply with a single combined line."

  private def functionCalls(response: ChatToolCompletionResponse): String =
    response.choices.head.message.tool_calls.collect { case (_, fc: FunctionCallSpec) =>
      s"${fc.name}(${fc.arguments})"
    }.mkString(", ") match {
      case ""    => throw new IllegalStateException("no tool call")
      case calls => calls
    }

  private def describe(e: Throwable): String =
    s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("(no message)").take(400)}"

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val openAI = OpenAIServiceFactory.withStreaming()
    val model = ModelId.gpt_6_1_sol
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
          println(s"[FAIL] $name: ${describe(e)}")
          Success(())
      }
    }

    val all = for {
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

      _ <- section(s"$model: reasoning_effort=none / minimal -> low (always reasoning)") {
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
        s"$model: tools with reasoning_effort=none (Responses API, 'none' -> 'low')"
      ) {
        openAI
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            settings = CreateChatCompletionSettings(
              model,
              reasoning_effort = Some(ReasoningEffort.none)
            )
          )
          .map(r => s"${functionCalls(r)} id=${r.id.take(5)}")
      }

      _ <- section(s"$model: forced tool choice (Responses API)") {
        openAI
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            responseToolChoice = Some(weatherTool.name),
            settings = CreateChatCompletionSettings(model)
          )
          .map(functionCalls)
      }

      _ <- section(s"$model: typed streamed tools with reasoning_effort=max (Responses API)") {
        openAI
          .createChatToolCompletionStreamed(
            weatherQuestion,
            Seq(weatherTool),
            None,
            CreateChatCompletionSettings(model, reasoning_effort = Some(ReasoningEffort.max))
          )
          .assembled
          .map { a =>
            if (a.toolCalls.isEmpty) throw new IllegalStateException(s"no tool call: $a")
            a.toolCalls.map(c => s"${c.toolName}(${c.arguments})").mkString(", ") +
              s" thinking=${a.thinking.length} chars finish=${a.finishReason}"
          }
      }

      _ <- section(s"$model: createChatCompletionWithJSON[Capital] (json_schema)") {
        openAI
          .createChatCompletionWithJSON[Capital](
            Seq(UserMessage("Capital of Norway?")),
            CreateChatCompletionSettings(
              model = model,
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(c => s"parsed=$c")
      }

      _ <- section(s"$model: setResponsesReasoningMode(pro) - sync, via the Responses API") {
        openAI
          .createChatCompletion(
            Seq(UserMessage("Is 391 prime? One sentence.")),
            CreateChatCompletionSettings(model, reasoning_effort = Some(ReasoningEffort.low))
              .setResponsesReasoningMode(ReasoningMode.pro)
          )
          .map(r => s"content='${r.contentHead}' id=${r.id.take(5)}")
      }

      _ <- section(s"$model: setResponsesReasoningMode(pro) - typed stream") {
        openAI
          .createChatCompletionStreamedTyped(
            Seq(UserMessage("Is 391 prime? One sentence.")),
            CreateChatCompletionSettings(model).setResponsesReasoningMode(ReasoningMode.pro)
          )
          .assembled
          .map(a => s"thinking=${a.thinking.length} chars text='${a.text.take(80)}'")
      }

      _ <- section(s"$model: Responses API - effort max + mode pro echoed back") {
        openAI
          .createModelResponse(
            Inputs.Text("Is 391 prime? One sentence."),
            CreateModelResponseSettings(
              model = model,
              reasoning = Some(
                ReasoningConfig(
                  effort = Some(ReasoningEffort.max),
                  mode = Some(ReasoningMode.pro)
                )
              ),
              store = Some(false)
            )
          )
          .map { r =>
            if (!r.reasoning.flatMap(_.mode).contains(ReasoningMode.pro))
              throw new IllegalStateException(s"mode not echoed: ${r.reasoning}")
            s"reasoning=${r.reasoning} text='${r.outputText.getOrElse("").take(60)}'"
          }
      }

      _ <- section(s"$model: multi-agent execution (Responses API, beta)") {
        openAI
          .createModelResponse(
            Inputs.Text(multiAgentTask),
            CreateModelResponseSettings(
              model = model,
              multiAgent = Some(MultiAgentConfig(maxConcurrentSubagents = Some(2))),
              store = Some(false)
            )
          )
          .map { r =>
            val actions = r.output.collect { case call: MultiAgentCall => call.action }
            if (actions.isEmpty) throw new IllegalStateException(s"no delegation: ${r.output}")
            s"actions=$actions subagents=${r.subagentMessages.flatMap(_.agent).map(_.agentName)} answer='${r.outputText
                .getOrElse("")}'"
          }
      }

      _ <- section(s"$model: setResponsesMultiAgent - typed stream (root answer only)") {
        openAI
          .createChatCompletionStreamedTyped(
            Seq(UserMessage(multiAgentTask)),
            CreateChatCompletionSettings(model, reasoning_effort = Some(ReasoningEffort.low))
              .setResponsesMultiAgent(MultiAgentConfig(maxConcurrentSubagents = Some(2)))
          )
          .assembled
          .map { a =>
            val delegation = a.toolCalls.filter(_.toolName.startsWith("multi_agent."))
            if (delegation.isEmpty) throw new IllegalStateException(s"no delegation: $a")
            s"delegation=${delegation.map(_.toolName)} results=${a.toolResults.size} " +
              s"subagent messages=${a.other.count(_.kind == "subagent.message")} text='${a.text}'"
          }
      }

      _ <- section(
        s"$model: service_tier=fast (chat completions, a GPT-6 response says fast)"
      ) {
        openAI
          .createChatCompletionStreamed(
            Seq(UserMessage("Say hi")),
            CreateChatCompletionSettings(model).withServiceTier(ServiceTier.fast)
          )
          .runWith(Sink.seq)
          .map { chunks =>
            val text = chunks.flatMap(_.choices.headOption.flatMap(_.delta.content)).mkString
            if (text.isEmpty) throw new IllegalStateException("no streamed text")
            s"${chunks.size} chunks, text='$text'"
          }
      }

      _ <- section(
        s"${ModelId.gpt_6_astra}: service_tier=ultrafast (routed to the Responses API)"
      ) {
        val ultrafast =
          CreateChatCompletionSettings(ModelId.gpt_6_astra)
            .withServiceTier(ServiceTier.ultrafast)
        for {
          sync <- openAI.createChatCompletion(Seq(UserMessage("Say hi")), ultrafast)
          streamed <- openAI
            .createChatCompletionStreamed(Seq(UserMessage("Count from 1 to 5.")), ultrafast)
            .runWith(Sink.seq)
        } yield {
          val text = streamed.flatMap(_.choices.headOption.flatMap(_.delta.content)).mkString
          if (text.isEmpty) throw new IllegalStateException("no streamed text")
          s"sync='${sync.contentHead}' streamed='$text'"
        }
      }

      _ <- section(
        s"global.${ModelId.bedrock_openai_gpt_6_1_sol}: Bedrock (bedrock-runtime)"
      ) {
        BedrockAuth.bearerTokenFromEnv() match {
          case None =>
            Future.successful("skipped - AWS_BEARER_TOKEN_BEDROCK not set")

          case Some(token) =>
            val bedrock = OpenAIServiceFactory.forBedrock(
              BedrockAuth.BearerToken(token),
              region = sys.env.getOrElse("AWS_BEDROCK_REGION", "eu-central-1"),
              endpoint = BedrockEndpoint.Runtime
            )
            val bedrockModel = "global." + ModelId.bedrock_openai_gpt_6_1_sol
            (for {
              chat <- bedrock.createChatCompletion(
                Seq(UserMessage("Capital of Norway? One word.")),
                CreateChatCompletionSettings(
                  bedrockModel,
                  temperature = Some(0.2),
                  reasoning_effort = Some(ReasoningEffort.none)
                )
              )
              tools <- bedrock.createChatToolCompletion(
                weatherQuestion,
                Seq(weatherTool),
                settings = CreateChatCompletionSettings(bedrockModel)
              )
            } yield s"chat='${chat.contentHead}' tools=${functionCalls(tools)}").andThen {
              case _ => bedrock.close()
            }
        }
      }
    } yield ()

    // a run that fails or times out before its last section is a failure too
    Try(Await.result(all, 20.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: ${describe(e)}")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    openAI.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
