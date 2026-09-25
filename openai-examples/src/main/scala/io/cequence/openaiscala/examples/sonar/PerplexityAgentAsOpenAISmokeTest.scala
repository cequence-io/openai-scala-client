package io.cequence.openaiscala.examples.sonar

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.responsesapi.tools.WebSearchTool
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  FunctionCallSpec,
  JsonSchema,
  SystemMessage,
  ToolMessage,
  UserMessage
}
import io.cequence.openaiscala.perplexity.service.SonarServiceFactory
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test of `SonarServiceFactory.agentAsOpenAI` - Perplexity's Agent API behind the
 * OpenAI chat-completion interface: plain chat, a system prompt, function tools (incl. the
 * round trip with the tool result), JSON, the typed stream with tools, the legacy OpenAI chunk
 * stream, and Perplexity's web search. Wrapped in the library's retry adapter (the Agent API
 * rate-limits bursts). Requires `PERPLEXITY_API_KEY` (or `SONAR_API_KEY`).
 */
object PerplexityAgentAsOpenAISmokeTest {

  private case class Capital(
    country: String,
    capital: String
  )

  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val chat = SonarServiceFactory.agentAsOpenAI()
    // the shared retry adapter works on the repacked OpenAIScala* exceptions (429 -> retry)
    implicit val retrySettings: RetrySettings =
      RetrySettings(maxRetries = 5, delayOffset = 5.seconds)
    val retrying = OpenAIServiceAdapters.forChatCompletionService.retry(chat, Some(println(_)))
    val model = "openai/gpt-5.4-mini"
    val failures = new AtomicInteger(0)

    val weather = FunctionTool(
      name = "get_weather",
      description = Some("Get the current weather in a city"),
      parameters = JsonSchema.Object(
        properties = Seq("city" -> JsonSchema.String()),
        required = Seq("city")
      )
    )

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

    def check(
      condition: Boolean,
      message: => String
    ): Unit = if (!condition) throw new IllegalStateException(message)

    val settings = CreateChatCompletionSettings(model, max_tokens = Some(500))

    val all = for {
      _ <- section("createChatCompletion with a system prompt") {
        retrying
          .createChatCompletion(
            Seq(SystemMessage("Answer with one word."), UserMessage("Capital of Norway?")),
            settings
          )
          .map { r =>
            check(r.contentHead.contains("Oslo"), s"unexpected '${r.contentHead}'")
            s"'${r.contentHead}' model=${r.model} tokens=${r.usage.map(_.total_tokens)}"
          }
      }

      _ <- section("createChatToolCompletion + tool result round trip") {
        val question = UserMessage("What's the weather in Oslo right now?")
        for {
          first <- retrying.createChatToolCompletion(
            Seq(question),
            Seq(weather),
            settings = settings
          )
          (callId, call) = first.choices.head.message.tool_calls.collectFirst {
            case (id, f: FunctionCallSpec) => (id, f)
          }.getOrElse(throw new IllegalStateException("no tool call"))
          second <- retrying.createChatToolCompletion(
            Seq(
              question,
              AssistantToolMessage(tool_calls = Seq(callId -> call)),
              ToolMessage(
                Some("""{"temperature_c": 12, "sky": "cloudy"}"""),
                callId,
                call.name
              )
            ),
            Seq(weather),
            settings = settings
          )
        } yield {
          val answer = second.choices.head.message.content.getOrElse("")
          check(answer.contains("12"), s"answer ignores the tool output: $answer")
          s"call=${call.name}(${call.arguments}) answer='${answer.take(70)}'"
        }
      }

      _ <- section("createChatCompletionWithJSON") {
        retrying
          .createChatCompletionWithJSON[Capital](
            Seq(UserMessage("Capital of Sweden?")),
            settings.copy(
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(
                JsonSchemaDef(
                  "capital",
                  strict = true,
                  JsonSchema.Object(
                    properties = Seq(
                      "country" -> JsonSchema.String(),
                      "capital" -> JsonSchema.String()
                    ),
                    required = Seq("country", "capital")
                  )
                )
              )
            ),
            // Perplexity answers json_object with an empty {} - keep json_schema mode for the
            // provider/model id (it is not in models-supporting-json-schema)
            jsonSchemaModels = Seq(model)
          )
          .map { c =>
            check(c.capital.contains("Stockholm"), s"unexpected $c")
            s"parsed=$c"
          }
      }

      _ <- section("createChatToolCompletionStreamed (typed, tools)") {
        chat
          .createChatToolCompletionStreamed(
            Seq(UserMessage("What's the weather in Oslo?")),
            Seq(weather),
            None,
            settings
          )
          .assembled
          .map { a =>
            check(a.toolCalls.nonEmpty, s"no tool call: $a")
            s"toolCalls=${a.toolCalls.map(c => s"${c.toolName}(${c.arguments})")} finish=${a.finishReason}"
          }
      }

      _ <- section("createChatCompletionStreamed (legacy OpenAI chunks)") {
        chat
          .createChatCompletionStreamed(
            Seq(UserMessage("Capital of Denmark? One sentence.")),
            settings
          )
          .runWith(Sink.seq)
          .map { chunks =>
            val text = chunks.flatMap(_.choices.flatMap(_.delta.content)).mkString
            check(text.contains("Copenhagen"), s"unexpected '$text'")
            s"${chunks.size} chunks text='$text'"
          }
      }

      // the Responses chat adapter sends Responses tools (web search) on its tool calls - the
      // plain createChatCompletion passes none
      _ <- section("web search (sync, createChatToolCompletion without function tools)") {
        retrying
          .createChatToolCompletion(
            Seq(UserMessage("What was a top Oslo news headline this week? One sentence.")),
            Nil,
            settings = settings.setResponsesTools(Seq(WebSearchTool()))
          )
          .map { r =>
            val text = r.choices.head.message.content.getOrElse("")
            check(text.nonEmpty, "empty answer")
            s"'${text.take(90)}'"
          }
      }

      _ <- section("web search (typed stream)") {
        chat
          .createChatToolCompletionStreamed(
            Seq(UserMessage("What was a top Oslo news headline this week? One sentence.")),
            Nil,
            None,
            settings.setResponsesTools(Seq(WebSearchTool()))
          )
          .assembled
          .map { a =>
            check(a.text.nonEmpty, "empty answer")
            s"text='${a.text.take(80)}' other=${a.other.map(_.kind).distinct}"
          }
      }
    } yield ()

    Try(Await.result(all, 10.minutes))
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    retrying.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
