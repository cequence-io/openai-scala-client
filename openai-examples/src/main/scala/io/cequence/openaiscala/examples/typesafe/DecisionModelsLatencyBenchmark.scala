package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.decisions.CreateDecisionSettings
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeService,
  TypeSafeServiceFactory
}
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import io.cequence.wsclient.service.ws.Timeouts
import play.api.libs.json.{JsString, JsValue, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * The execution time of every decision model this library reaches, side by side: the same
 * review with 1, 3, 10, 20 and 40 questions (a noul, a choice and a score, topped up with
 * nouls - Span-01 takes nouls only), timed calls one after another on one shared engine, a row
 * per model with the median (min - max) per call, the input tokens billed at 1 and 40
 * questions (how a host reads the state: once, or once per question) and the first noul's
 * probability as a sanity check that the model answered the question.
 *
 * Every host with a key in the environment takes part - TypeSafe (`TYPESAFE_API_KEY`), Liquid
 * (`LIQUID_API_KEY`), Perplexity (`PERPLEXITY_API_KEY` / `SONAR_API_KEY`), OpenAI
 * (`OPENAI_SCALA_CLIENT_API_KEY`), Microsoft Foundry (`FOUNDRY_API_KEY` + `FOUNDRY_BASE_URL`,
 * the deployments of `FOUNDRY_MODEL`'s model), OpenRouter (`OPENROUTER_API_KEY`, every
 * decision model it lists) and a local llama.cpp server when one answers at
 * `http://127.0.0.1:8080/` - the others are reported as skipped. Arguments: `runs=<n>` timed
 * calls per cell (5, plus an untimed warm-up), `counts=1,3,10,20,40`, `only=<host,...>`,
 * `models=<substring,...>`, `budget=<seconds>` - a model whose call took longer skips the
 * larger counts (15), `pace=<ms>` between calls (250; Foundry's deployments are paced to their
 * 50 requests per minute). A full run makes ~700 calls and costs a few cents at most.
 *
 * Live 2026-10-09 (24 models on 6 hosts, 640 calls in 10 minutes; the table is in
 * `docs/decision-models.md`): Cloudflare's Clef Omni, Jev, OpenAI's Luna (directly), Liquid's
 * d1, Span-01 and Microsoft-Decision-1 answer 1-10 questions in 0.2-0.3 s and stay flat to 40,
 * reading the state once (Luna bills it per question); Perplexity's decider, Mercury Decide,
 * Clef, Tev1 (at most 32 questions) and the Solar Decides slow with the count - Solar Decide
 * by ~2 s per question; Liquid's free tier and OpenRouter's `:free` ids throttle.
 */
object DecisionModelsLatencyBenchmark {

  private val reviewText =
    "The headphones sound great, but the battery stopped charging after two weeks."

  private val review: JsValue =
    Json.obj("title" -> "Battery died after two weeks", "review" -> reviewText)

  private val questions: Map[String, Question] = Map(
    "defect" -> NoulQuestion("Does the review report a product defect?"),
    "sentiment" -> ChoiceQuestion(
      "What is the overall sentiment of the review?",
      "positive" -> "Mostly satisfied",
      "mixed" -> "Praise and complaints in one review",
      "negative" -> "Mostly dissatisfied"
    ),
    "severity" -> ScoreQuestion(
      "How severe is the reported problem?",
      "Cosmetic",
      "Inconvenient",
      "Product unusable"
    )
  )

  // Respan's Span-01 judges with noul questions only, over a text state (an object state is a
  // 400 unless it is a conversation trace)
  private val noulsOnly: Map[String, Question] = Map("defect" -> questions("defect"))
  private val reviewAsText: JsValue = JsString(s"Battery died after two weeks\n\n$reviewText")

  private def isSpan(model: String): Boolean = model.toLowerCase.contains("span")

  private case class Host(
    name: String,
    provider: Option[DecisionProvider],
    apiKey: Option[String],
    models: TypeSafeService => Future[Seq[String]],
    paceMs: Option[Long] = None
  )

  private case class Cell(
    times: Seq[Long] = Nil,
    failures: Int = 0,
    inputTokens: Option[Int] = None,
    defect: Option[Double] = None,
    error: Option[String] = None
  ) {
    def median: Option[Long] = {
      val sorted = times.sorted
      if (sorted.isEmpty) None else Some(sorted(sorted.size / 2))
    }

    def text: String =
      median match {
        case Some(m) =>
          val failed = if (failures > 0) s" ✗$failures" else ""
          s"$m (${times.min}-${times.max})$failed"
        case None => if (failures > 0) "failed" else "-"
      }
  }

  private case class Row(
    host: String,
    model: String,
    cells: Map[Int, Cell],
    skippedFrom: Option[Int]
  ) {
    def sortKey: Long =
      cells.get(10).flatMap(_.median).getOrElse(Long.MaxValue / 2) +
        (if (cells.values.forall(_.median.isEmpty)) Long.MaxValue / 2 else 0L)
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    def arg(name: String): Option[String] =
      args.find(_.startsWith(name + "=")).map(_.drop(name.length + 1))

    val runs = arg("runs").map(_.toInt).getOrElse(5)
    val counts =
      arg("counts").map(_.split(',').map(_.trim.toInt).toSeq).getOrElse(Seq(1, 3, 10, 20, 40))
    val only = arg("only").map(_.split(',').map(_.trim).toSet)
    val modelFilter = arg("models").map(_.split(',').map(_.trim).toSeq)
    val budgetMs = arg("budget").map(_.toLong * 1000).getOrElse(15000L)
    val paceMs = arg("pace").map(_.toLong).getOrElse(250L)

    def env(name: String): Option[String] = sys.env.get(name).map(_.trim).filter(_.nonEmpty)
    def fixed(models: String*): TypeSafeService => Future[Seq[String]] =
      _ => Future.successful(models)
    def listed: TypeSafeService => Future[Seq[String]] = _.listModels.map(_.map(_.name))

    val hosts = Seq(
      Host(
        "typesafe",
        Some(DecisionProviderSettings.typeSafe),
        env("TYPESAFE_API_KEY"),
        fixed(TypeSafeModelId.jev_latest, TypeSafeModelId.jev_preview)
      ),
      Host(
        "liquid",
        Some(DecisionProviderSettings.liquid),
        env("LIQUID_API_KEY"),
        fixed(TypeSafeModelId.liquid_d1_free, TypeSafeModelId.liquid_d1)
      ),
      Host(
        "perplexity",
        Some(DecisionProviderSettings.perplexity),
        env("PERPLEXITY_API_KEY").orElse(env("SONAR_API_KEY")),
        fixed(TypeSafeModelId.pplx_decider_v1_1_27b)
      ),
      Host(
        "openai",
        Some(DecisionProviderSettings.openAI),
        env("OPENAI_SCALA_CLIENT_API_KEY").orElse(env("OPENAI_API_KEY")),
        fixed(CreateDecisionSettings.DefaultModel)
      ),
      Host(
        "microsoft-foundry",
        env("FOUNDRY_BASE_URL").map(
          DecisionProviderSettings.microsoftFoundry(
            _,
            env("FOUNDRY_MODEL").getOrElse(DecisionProviderSettings.FoundryDefaultDeployment)
          )
        ),
        env("FOUNDRY_API_KEY"),
        listed,
        // the deployment's default rate limit is 50 requests per minute
        paceMs = Some(1250L)
      ),
      Host(
        "openrouter",
        Some(DecisionProviderSettings.openRouter),
        env("OPENROUTER_API_KEY"),
        listed
      ),
      Host(
        "llama.cpp",
        Some(DecisionProviderSettings.llamaCpp),
        Some(env("LLAMA_API_KEY").getOrElse("")),
        listed
      )
    )

    // Solar Decide takes ~47 s for 40 questions - well over the default engine timeouts
    val engine = WSClientEngineRegistry(
      TransportSettings(timeouts =
        Timeouts(requestTimeout = Some(180000), readTimeout = Some(180000))
      )
    )
    val started = System.currentTimeMillis()

    def questionsFor(
      model: String,
      n: Int
    ): Map[String, Question] =
      DecisionModelBenchmark.questionsOf(if (isSpan(model)) noulsOnly else questions, n)

    // `runs` timed calls after an untimed warm-up, paced; a failed call is counted, not timed
    def measure(
      service: TypeSafeService,
      model: String,
      n: Int,
      pace: Long
    ): Future[Cell] =
      (0 to runs).foldLeft(Future.successful(Cell())) { case (acc, call) =>
        acc.flatMap { cell =>
          akka.pattern.after(pace.millis, scheduler)(Future.successful(())).flatMap { _ =>
            val start = System.nanoTime()
            service
              .systemOne(if (isSpan(model)) reviewAsText else review, questionsFor(model, n))
              .map { response =>
                val ms = (System.nanoTime() - start) / 1000000
                cell.copy(
                  times = if (call == 0) cell.times else cell.times :+ ms,
                  inputTokens = response.usage.input_tokens.orElse(cell.inputTokens),
                  defect = cell.defect.orElse(
                    response.nouls.get("defect").map(_.noul)
                  )
                )
              }
              .recover { case NonFatal(e) =>
                cell.copy(
                  failures = cell.failures + 1,
                  error = Some(s"${e.getClass.getSimpleName}: ${e.getMessage.take(160)}")
                )
              }
          }
        }
      }

    def measureModel(
      host: Host,
      service: TypeSafeService,
      model: String,
      pace: Long
    ): Future[Row] =
      counts.foldLeft(Future.successful(Row(host.name, model, Map(), None))) {
        (
          acc,
          n
        ) =>
          acc.flatMap { row =>
            if (row.skippedFrom.isDefined) Future.successful(row)
            else
              measure(service, model, n, pace).map { cell =>
                println(f"  ${host.name}%-18s ${model}%-40s ${n}%3d q  ${cell.text}")
                val overBudget = cell.times.exists(_ > budgetMs)
                val dead = cell.median.isEmpty && n == counts.head
                row.copy(
                  cells = row.cells + (n -> cell),
                  skippedFrom =
                    if (overBudget || dead) counts.dropWhile(_ <= n).headOption else None
                )
              }
          }
      }

    def runHost(host: Host): Future[Seq[Row]] =
      (host.provider, host.apiKey) match {
        case (Some(provider), Some(apiKey)) if only.forall(_.contains(host.name)) =>
          val listing = TypeSafeServiceFactory.withEngine(engine, provider, apiKey)
          host
            .models(listing)
            .map(_.filter(m => modelFilter.forall(_.exists(m.contains))))
            .recover { case NonFatal(e) =>
              println(
                s"[${host.name}] skipped - the model listing failed: ${e.getMessage.take(160)}"
              )
              Nil
            }
            .flatMap { models =>
              if (models.isEmpty) println(s"[${host.name}] no models to time")
              else println(s"[${host.name}] ${models.size} models: ${models.mkString(", ")}")
              models.foldLeft(Future.successful(Seq.empty[Row])) {
                (
                  acc,
                  model
                ) =>
                  acc.flatMap { rows =>
                    val service = TypeSafeServiceFactory.withEngine(
                      engine,
                      provider.copy(defaultModel = model),
                      apiKey
                    )
                    measureModel(host, service, model, host.paceMs.getOrElse(paceMs))
                      .map(rows :+ _)
                  }
              }
            }
            .map { rows =>
              listing.close()
              rows
            }

        case _ =>
          val why =
            if (only.exists(!_.contains(host.name))) "not selected"
            else if (host.provider.isEmpty) "FOUNDRY_BASE_URL not set"
            else "no key in the environment"
          println(s"[${host.name}] skipped - $why")
          Future.successful(Nil)
      }

    val all = hosts.foldLeft(Future.successful(Seq.empty[Row])) {
      (
        acc,
        host
      ) =>
        acc.flatMap(rows => runHost(host).map(rows ++ _))
    }

    val rows =
      try Await.result(all, 90.minutes)
      catch {
        case NonFatal(e) =>
          println(s"FAILED: $e")
          Nil
      }

    // the table, the fastest at 10 questions first
    println()
    println(
      s"[latency] median ms per call (min-max), $runs timed calls each, ✗ = failed calls; " +
        s"tokens = input tokens billed at ${counts.head} / ${counts.last} questions"
    )
    println(
      "| model | host | " + counts.map(n => s"$n q").mkString(" | ") +
        " | tokens | defect |"
    )
    println("|---|---|" + counts.map(_ => "---:").mkString("|") + "|---|---:|")
    rows.sortBy(_.sortKey).foreach { row =>
      val cells = counts.map { n =>
        row.cells.get(n).map(_.text).getOrElse {
          if (row.skippedFrom.exists(_ <= n)) "skipped" else "-"
        }
      }
      val tokens = Seq(counts.head, counts.last)
        .map(n => row.cells.get(n).flatMap(_.inputTokens).map(_.toString).getOrElse("-"))
        .mkString(" / ")
      val defect =
        row.cells.values.flatMap(_.defect).headOption.map(d => f"$d%.2f").getOrElse("-")
      println(
        s"| ${row.model} | ${row.host} | ${cells.mkString(" | ")} | $tokens | $defect |"
      )
    }

    val problems =
      rows.flatMap(row => row.cells.values.flatMap(_.error).headOption.map(row.model -> _))
    if (problems.nonEmpty) {
      println()
      println("[errors] the first failure per model:")
      problems.foreach { case (model, error) => println(s"  $model: $error") }
    }

    val calls = rows.flatMap(_.cells.values).map(c => c.times.size + c.failures + 1).sum
    println()
    println(
      f"[done] ${rows.size} models, $calls calls in ${(System.currentTimeMillis() - started) / 1000}%d s"
    )

    engine.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (rows.exists(_.cells.values.exists(_.median.isDefined))) 0 else 1)
  }
}
