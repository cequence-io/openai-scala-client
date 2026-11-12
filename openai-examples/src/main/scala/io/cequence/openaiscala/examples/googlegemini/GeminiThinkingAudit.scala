package io.cequence.openaiscala.examples.googlegemini

import io.cequence.openaiscala.FutureHelpers
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
    implicit val ec: ExecutionContext = ExecutionContext.global

    val service = GeminiServiceFactory.asOpenAI()

    // bounded parallelism (free / low-tier keys rate-limit bursts)
    val parallelism = sys.env.get("AUDIT_PARALLELISM").map(_.toInt).getOrElse(6)
    val pairs = for {
      model <- args.toList
      effort <- efforts
    } yield (model, effort)

    val all = FutureHelpers.parallelize(pairs, Some(parallelism)) { case (model, effort) =>
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

    Try(Await.result(all, 30.minutes))
    service.close()
    System.exit(0)
  }
}
