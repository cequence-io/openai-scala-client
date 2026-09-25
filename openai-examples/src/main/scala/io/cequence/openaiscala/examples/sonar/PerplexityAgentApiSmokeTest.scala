package io.cequence.openaiscala.examples.sonar

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.JsonSchema
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import io.cequence.openaiscala.perplexity.domain.agent._
import io.cequence.openaiscala.perplexity.service.{PerplexityRetryable, SonarServiceFactory}
import play.api.libs.json.Json

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test of Perplexity's Agent API through `SonarService` (the successor of the Sonar
 * chat completions API, which Perplexity supports only until 2026-09-27):
 *
 *   - `listAgentModels`
 *   - a preset run with web search: answer text, citations, search results, usage and cost
 *   - structured output (`responseFormat`)
 *   - the typed event stream (search queries / results, text deltas, completed response)
 *   - a custom function round trip (function_call -> function_call_output, replayed input)
 *   - multi-turn with `previousResponseId`
 *   - background mode: submit, retrieve, cancel
 *   - `retrieveAgentResponse` of an unknown id -> None
 *
 * Every section prints PASS/FAIL and the run continues; the exit code is 1 if any failed.
 * Requires `PERPLEXITY_API_KEY` (or `SONAR_API_KEY`). Costs a few cents per run.
 */
object PerplexityAgentApiSmokeTest {

  private case class Capital(
    country: String,
    capital: String
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    val service = SonarServiceFactory()
    val failures = new AtomicInteger(0)

    // the per-response endpoints (cancel, files, a 404 retrieve) are throttled hard - back off on
    // PerplexityRetryable (429, 5xx, timeouts) like a real caller would
    def retrying[T](
      attempt: Int = 1
    )(
      f: => Future[T]
    ): Future[T] =
      f.recoverWith {
        case PerplexityRetryable(e) if attempt < 6 =>
          println(s"  (retry $attempt after: ${e.getMessage.take(80)})")
          akka.pattern.after((10 * attempt).seconds, system.scheduler)(
            retrying(attempt + 1)(f)
          )
      }

    def section(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] = {
      val start = System.currentTimeMillis()
      retrying()(Try(f).fold(Future.failed, identity)).transform {
        case Success(msg) =>
          println(s"[PASS] $name (${System.currentTimeMillis() - start} ms): $msg")
          Success(())
        case Failure(e) =>
          failures.incrementAndGet()
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage.take(500)}")
          Success(())
      }
    }

    def check(
      condition: Boolean,
      message: => String
    ): Unit = if (!condition) throw new IllegalStateException(message)

    val fast = CreateAgentResponseSettings(preset = Some(AgentPreset.fast))

    val weatherTool = AgentTool.Function(
      name = "get_weather",
      description = Some("Get the current weather in a city"),
      parameters = Some(
        JsonSchema.Object(
          properties = Seq("city" -> JsonSchema.String()),
          required = Seq("city")
        )
      )
    )

    val all = for {
      _ <- section("listAgentModels") {
        service.listAgentModels.map { models =>
          check(models.nonEmpty, "no models")
          s"${models.size} models, e.g. ${models.take(4).map(_.id).mkString(", ")}"
        }
      }

      _ <- section("createAgentResponse (fast preset, web search)") {
        service
          .createAgentResponse(
            AgentInput("What is the capital of Norway? One sentence."),
            fast
          )
          .map { r =>
            check(r.status == AgentResponseStatus.completed, s"status ${r.status}")
            check(r.outputText.nonEmpty, "empty answer")
            val cost = r.usage.flatMap(_.cost).map(c => f"$$${c.totalCost}%.4f").getOrElse("?")
            s"model=${r.model} text='${r.outputText.take(80)}' citations=${r.citations.size} " +
              s"searchResults=${r.searchResults.size} tokens=${r.usage.map(_.totalTokens)} cost=$cost " +
              s"items=${r.output.map(_.getClass.getSimpleName).mkString(",")}"
          }
      }

      _ <- section("structured output (responseFormat)") {
        service
          .createAgentResponse(
            AgentInput("Capital of Sweden?"),
            fast.copy(
              responseFormat = Some(
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
            )
          )
          .map { r =>
            val json = Json.parse(r.outputText)
            val capital = Capital((json \ "country").as[String], (json \ "capital").as[String])
            check(capital.capital.toLowerCase.contains("stockholm"), s"unexpected $capital")
            s"parsed=$capital"
          }
      }

      _ <- section("createAgentResponseStreamed") {
        service
          .createAgentResponseStreamed(AgentInput("Capital of Denmark? One sentence."), fast)
          .runWith(Sink.seq)
          .map { events =>
            val text = events.collect { case d: AgentStreamEvent.OutputTextDelta =>
              d.delta
            }.mkString
            val completed =
              events.collect { case c: AgentStreamEvent.ResponseCompleted =>
                c.response
              }.flatten
            val unknown = events.collect { case u: AgentStreamEvent.Unknown =>
              u.`type`
            }.distinct
            check(text.nonEmpty, "no text deltas")
            check(completed.nonEmpty, "no response.completed")
            check(
              events.map(_.sequenceNumber) == events.map(_.sequenceNumber).sorted,
              "sequence numbers out of order"
            )
            s"${events.size} events (${events.map(_.getClass.getSimpleName).distinct.mkString(", ")}) " +
              s"text='${text.take(60)}' unknown=$unknown"
          }
      }

      _ <- section("custom function round trip") {
        val question = AgentInputItem.Message.user("What's the weather in Oslo right now?")
        val settings = CreateAgentResponseSettings(
          model = Some("openai/gpt-5.4-mini"),
          maxOutputTokens = Some(1000),
          tools = Seq(weatherTool)
        )
        for {
          first <- service.createAgentResponse(AgentInput(question), settings)
          call = first.functionCalls.headOption.getOrElse(
            throw new IllegalStateException(s"no function call: ${first.output}")
          )
          second <- service.createAgentResponse(
            AgentInput(
              question,
              AgentInputItem
                .FunctionCall(call.callId, call.name, call.arguments, call.thoughtSignature),
              AgentInputItem
                .FunctionCallOutput(call.callId, """{"temperature_c": 12, "sky": "cloudy"}""")
            ),
            settings
          )
        } yield {
          check(
            second.outputText.contains("12"),
            s"answer ignores the tool output: ${second.outputText}"
          )
          s"call=${call.name}(${call.arguments}) answer='${second.outputText.take(80)}'"
        }
      }

      _ <- section("multi-turn via previousResponseId") {
        for {
          first <- service.createAgentResponse(
            AgentInput("My name is Ada. Just say ok."),
            fast
          )
          second <- service.createAgentResponse(
            AgentInput("What is my name?"),
            fast.copy(previousResponseId = Some(first.id))
          )
        } yield {
          check(second.outputText.contains("Ada"), s"lost context: ${second.outputText}")
          s"answer='${second.outputText.take(60)}' previous=${second.previousResponseId}"
        }
      }

      _ <- section("background: submit, retrieve, cancel") {
        for {
          submitted <- service.createAgentResponse(
            AgentInput("Write a detailed survey of grid-scale battery storage trends."),
            CreateAgentResponseSettings(
              preset = Some(AgentPreset.medium),
              background = Some(true)
            )
          )
          retrieved <- retrying()(service.retrieveAgentResponse(submitted.id))
          cancelled <- retrying()(service.cancelAgentResponse(submitted.id))
        } yield {
          check(retrieved.exists(_.id == submitted.id), "retrieve did not find it")
          s"submitted status=${submitted.status} retrieved status=${retrieved.map(_.status)} " +
            s"cancel=${cancelled.status}"
        }
      }

      _ <- section("retrieveAgentResponse of an unknown id -> None") {
        service.retrieveAgentResponse("resp_does_not_exist").map { r =>
          check(r.isEmpty, s"expected None, got $r")
          "None"
        }
      }
    } yield ()

    Try(Await.result(all, 15.minutes))
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    service.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
