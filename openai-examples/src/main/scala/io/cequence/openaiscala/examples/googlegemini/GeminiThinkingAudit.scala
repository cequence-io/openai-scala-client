package io.cequence.openaiscala.examples.googlegemini

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.domain.UserMessage
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Success, Try}

/**
 * Runs every `reasoning_effort` against every given Gemini model through the OpenAI adapter
 * (`GeminiServiceFactory.asOpenAI`), so the model -> thinkingLevel / thinkingBudget mapping
 * (`GeminiThinking`) is checked against the live API: prints OK or the error per pair.
 *
 * Run: `GeminiThinkingAudit gemini-3.8-flash gemini-flash-latest ...` (needs
 * `GOOGLE_API_KEY`).
 */
object GeminiThinkingAudit {

  private val efforts = Seq(
    ReasoningEffort.none,
    ReasoningEffort.minimal,
    ReasoningEffort.low,
    ReasoningEffort.medium,
    ReasoningEffort.high,
    ReasoningEffort.xhigh,
    ReasoningEffort.max
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    val service = GeminiServiceFactory.asOpenAI()

    // bounded parallelism (free / low-tier keys rate-limit bursts)
    val parallelism = sys.env.get("AUDIT_PARALLELISM").map(_.toInt).getOrElse(6)
    val pairs = for {
      model <- args.toList
      effort <- efforts
    } yield (model, effort)

    val all = Source(pairs)
      .mapAsync(parallelism) { case (model, effort) =>
        service
          .createChatCompletion(
            Seq(UserMessage("Say hi.")),
            CreateChatCompletionSettings(
              model,
              max_tokens = Some(4000),
              reasoning_effort = Some(effort)
            )
          )
          .transform { result =>
            val status = result.fold(e => s"FAIL ${e.getMessage.take(140)}", _ => "OK")
            println(f"$model%-34s ${effort.toString}%-8s $status")
            Success(())
          }
      }
      .runWith(Sink.ignore)

    Try(Await.result(all, 30.minutes))
    service.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(0)
  }
}
