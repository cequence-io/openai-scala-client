package io.cequence.openaiscala.examples.typesafe

import akka.actor.Scheduler
import io.cequence.openaiscala.typesafe.domain.{NoulQuestion, Question}
import io.cequence.openaiscala.typesafe.service.TypeSafeService

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * The latency benchmark of the decision-model smoke tests (`LiquidD1SmokeTest`,
 * `PerplexityDeciderSmokeTest`): the same request with 1, 3, 10, 20 and 40 questions, timed
 * calls one after another on each service's pooled connection, the median (min - max) per call
 * in a column per service.
 */
object DecisionModelBenchmark {

  val QuestionCounts: Seq[Int] = Seq(1, 3, 10, 20, 40)

  /** The first `n` questions, topped up with nouls about the message when there are fewer. */
  def questionsOf(
    questions: Map[String, Question],
    n: Int
  ): Map[String, Question] =
    questions.take(n) ++ (0 until math.max(0, n - questions.size)).map { i =>
      s"mentions_$i" -> NoulQuestion(
        s"Does the message raise point number $i (time, money, quality, communication, ...)?"
      )
    }

  /**
   * Sequential calls 250 ms apart (Liquid's free tier throttles bursts) - the first one warms
   * the connection up and is not counted; a failed call is counted, not timed.
   */
  def timeCalls(
    service: TypeSafeService,
    state: String,
    questions: Map[String, Question],
    runs: Int
  )(
    implicit ec: ExecutionContext,
    scheduler: Scheduler
  ): Future[(Seq[Long], Int)] =
    (0 to runs).foldLeft(Future.successful((Seq.empty[Long], 0))) { case (acc, call) =>
      acc.flatMap { case (times, failures) =>
        akka.pattern.after(250.millis, scheduler)(Future.successful(())).flatMap { _ =>
          val start = System.nanoTime()
          service
            .systemOne(state, questions)
            .map { _ =>
              val ms = (System.nanoTime() - start) / 1000000
              (if (call == 0) times else times :+ ms, failures)
            }
            .recover { case NonFatal(_) => (times, failures + 1) }
        }
      }
    }

  /** `median ms (min - max)`, plus the failed calls. */
  def summary(result: (Seq[Long], Int)): String = {
    val (times, failures) = result
    val sorted = times.sorted
    val failed = if (failures > 0) s", $failures failed" else ""
    if (sorted.isEmpty) s"no successful call$failed"
    else f"${sorted(sorted.size / 2)}%5d ms (${sorted.head}%d - ${sorted.last}%d)$failed"
  }

  /**
   * Prints a table: a row per question count, a column per service - the services one after
   * another, so they never compete for the same moment.
   */
  def run(
    services: Seq[(String, TypeSafeService)],
    state: String,
    questions: Map[String, Question],
    runs: Int = 8
  )(
    implicit ec: ExecutionContext,
    scheduler: Scheduler
  ): Future[Unit] = {
    println(s"[latency] median per call (min - max), $runs timed calls each")
    println(
      "  questions   " + services.map { case (label, _) => f"$label%-26s" }.mkString("  ")
    )

    QuestionCounts.foldLeft(Future.successful(())) {
      (
        acc,
        n
      ) =>
        acc.flatMap { _ =>
          services
            .foldLeft(Future.successful(Seq.empty[String])) { case (cells, (_, service)) =>
              cells.flatMap { done =>
                timeCalls(service, state, questionsOf(questions, n), runs).map(
                  done :+ summary(_)
                )
              }
            }
            .map(cells => println(f"  $n%9d   " + cells.map(c => f"$c%-26s").mkString("  ")))
        }
    }
  }
}
