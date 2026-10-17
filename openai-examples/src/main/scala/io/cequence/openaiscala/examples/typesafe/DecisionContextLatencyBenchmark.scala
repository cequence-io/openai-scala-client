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
import play.api.libs.json.{JsString, Json}

import java.io.{File, PrintWriter}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * How the decision models' latency grows with the size of the state: every model gets the same
 * one-question request (a noul) over a state of ~1k to ~128k tokens - the 19-token test
 * sentence repeated, sized by Jev's count; each model's own count is recorded - timed `runs`
 * times per size after one untimed warm-up per model, paced `pace` ms apart. A model stops at
 * the first size it cannot answer (its context limit, usually) or whose median passes `budget`
 * seconds. Hosts run in parallel, `parallel` models at a time on each; TypeSafe's Jev runs
 * with long context (`DecisionProviderSettings.typeSafeLongContext`), the only way past 33k
 * there.
 *
 * Every host with a key in the environment takes part, as in `DecisionModelsLatencyBenchmark`
 * (TypeSafe, Liquid, Perplexity, OpenAI, Microsoft Foundry, SubQ, OpenRouter's whole decision
 * list, Vercel's free tier) - the others are skipped. Arguments: `sizes=1,2,4,8,16,32,64,128`
 * (k tokens), `runs=3`, `only=<host,...>`, `models=<substring,...>`, `budget=90` (s),
 * `pace=250` (ms), `parallel=3`, `out=<file>` (one JSON line per model and size, for charts).
 * A full run makes ~800 calls over ~20M input tokens - a couple of dollars at most.
 */
object DecisionContextLatencyBenchmark {

  // Jev and SubQ count it as exactly 19 tokens
  private val sentence =
    "The quarterly report lists revenue, costs, staffing, and a customer complaint about a double charge. "

  private val question: Map[String, Question] = Map(
    "billing" -> NoulQuestion("Does the report mention a customer complaint about a charge?")
  )

  private case class Host(
    name: String,
    provider: Option[DecisionProvider],
    apiKey: Option[String],
    models: TypeSafeService => Future[Seq[String]],
    paceMs: Option[Long] = None
  )

  private case class Cell(
    sizeK: Int,
    times: Seq[Long] = Nil,
    failures: Int = 0,
    inputTokens: Option[Int] = None,
    noul: Option[Double] = None,
    error: Option[String] = None
  ) {
    def median: Option[Long] = {
      val sorted = times.sorted
      if (sorted.isEmpty) None else Some(sorted(sorted.size / 2))
    }

    def text: String =
      median match {
        case Some(m) => s"$m" + (if (failures > 0) s" ✗$failures" else "")
        case None    => if (failures > 0) "failed" else "-"
      }
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    def arg(name: String): Option[String] =
      args.find(_.startsWith(name + "=")).map(_.drop(name.length + 1))

    val sizes = arg("sizes")
      .map(_.split(',').map(_.trim.toInt).toSeq)
      .getOrElse(Seq(1, 2, 4, 8, 16, 32, 64, 128))
    val runs = arg("runs").map(_.toInt).getOrElse(3)
    val only = arg("only").map(_.split(',').map(_.trim).toSet)
    val modelFilter = arg("models").map(_.split(',').map(_.trim).toSeq)
    val budgetMs = arg("budget").map(_.toLong * 1000).getOrElse(90000L)
    val paceMs = arg("pace").map(_.toLong).getOrElse(250L)
    val parallel = arg("parallel").map(_.toInt).getOrElse(3)
    val out = arg("out").map(path => new PrintWriter(new File(path), "UTF-8"))

    def env(name: String): Option[String] = sys.env.get(name).map(_.trim).filter(_.nonEmpty)
    def fixed(models: String*): TypeSafeService => Future[Seq[String]] =
      _ => Future.successful(models)
    def listed: TypeSafeService => Future[Seq[String]] = _.listModels.map(_.map(_.name))

    val hosts = Seq(
      Host(
        "typesafe",
        Some(DecisionProviderSettings.typeSafeLongContext),
        env("TYPESAFE_API_KEY"),
        fixed(DecisionModelId.jev_1_13_0, DecisionModelId.jev_1_14_0)
      ),
      Host(
        "liquid",
        Some(DecisionProviderSettings.liquid),
        env("LIQUID_API_KEY"),
        fixed(DecisionModelId.liquid_d1, DecisionModelId.liquid_d1_ultra)
      ),
      Host(
        "perplexity",
        Some(DecisionProviderSettings.perplexity),
        env("PERPLEXITY_API_KEY").orElse(env("SONAR_API_KEY")),
        fixed(DecisionModelId.pplx_decider_v1_2, DecisionModelId.pplx_decider_flash_v1_2)
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
        "subq",
        Some(DecisionProviderSettings.subq),
        env("SUBQ_API_KEY"),
        listed
      ),
      Host(
        "openrouter",
        Some(DecisionProviderSettings.openRouter),
        env("OPENROUTER_API_KEY"),
        listed
      ),
      Host(
        "vercel",
        Some(DecisionProviderSettings.vercel),
        env("AI_GATEWAY_API_KEY"),
        // a free-tier team gets Laya and ScaleDown (5 requests per minute); ScaleDown is timed
        // through OpenRouter
        fixed(DecisionModelId.vercel_laya_free),
        paceMs = Some(13000L)
      )
    )

    // large states take a while to send and to read
    val engine = WSClientEngineRegistry(
      TransportSettings(timeouts =
        Timeouts(requestTimeout = Some(180000), readTimeout = Some(180000))
      )
    )
    val started = System.currentTimeMillis()

    def stateOf(sizeK: Int) = JsString(sentence * math.max(1, sizeK * 1000 / 19))

    def call(
      service: TypeSafeService,
      sizeK: Int,
      pace: Long
    ): Future[Either[String, (Long, Option[Int], Option[Double])]] =
      akka.pattern.after(pace.millis, scheduler)(Future.unit).flatMap { _ =>
        val state = stateOf(sizeK)
        val start = System.nanoTime()
        service
          .systemOne(state, question)
          .map { response =>
            Right(
              (
                (System.nanoTime() - start) / 1000000,
                response.usage.input_tokens,
                response.nouls.get("billing").map(_.noul)
              )
            ): Either[String, (Long, Option[Int], Option[Double])]
          }
          .recover { case NonFatal(e) =>
            Left(s"${e.getClass.getSimpleName}: ${e.getMessage.take(160)}")
          }
      }

    def measure(
      service: TypeSafeService,
      sizeK: Int,
      pace: Long
    ): Future[Cell] =
      (1 to runs).foldLeft(Future.successful(Cell(sizeK))) { case (acc, _) =>
        acc.flatMap { cell =>
          // a size every call of which fails is not tried again and again
          if (cell.failures >= 2 && cell.times.isEmpty) Future.successful(cell)
          else
            call(service, sizeK, pace).map {
              case Right((ms, tokens, noul)) =>
                cell.copy(
                  times = cell.times :+ ms,
                  inputTokens = tokens.orElse(cell.inputTokens),
                  noul = noul.orElse(cell.noul)
                )
              case Left(error) =>
                cell.copy(failures = cell.failures + 1, error = Some(error))
            }
        }
      }

    def write(
      host: String,
      model: String,
      cell: Cell
    ): Unit =
      out.foreach { writer =>
        writer.synchronized {
          writer.println(
            Json.stringify(
              Json.obj(
                "host" -> host,
                "model" -> model,
                "size_k" -> cell.sizeK,
                "times_ms" -> cell.times,
                "median_ms" -> cell.median,
                "input_tokens" -> cell.inputTokens,
                "noul" -> cell.noul,
                "failures" -> cell.failures,
                "error" -> cell.error
              )
            )
          )
          writer.flush()
        }
      }

    def measureModel(
      host: Host,
      service: TypeSafeService,
      model: String,
      pace: Long
    ): Future[Seq[Cell]] =
      // the warm-up, untimed, at the smallest size
      call(service, sizes.head, pace).flatMap { _ =>
        sizes
          .foldLeft(Future.successful((Seq.empty[Cell], false))) { case (acc, sizeK) =>
            acc.flatMap {
              case (cells, true) => Future.successful((cells, true))
              case (cells, false) =>
                measure(service, sizeK, pace).map { cell =>
                  println(
                    f"  ${host.name}%-18s ${model}%-40s ${sizeK}%4dk  ${cell.text}%-10s ${cell.inputTokens
                        .getOrElse("-")}%8s tokens" +
                      cell.error.filter(_ => cell.median.isEmpty).fold("")(e => s"  $e")
                  )
                  write(host.name, model, cell)
                  val stop =
                    cell.median.isEmpty || cell.median.exists(_ > budgetMs)
                  (cells :+ cell, stop)
                }
            }
          }
          .map(_._1)
      }

    // `parallel` models of a host at a time
    def inGroups[A, B](
      items: Seq[A],
      size: Int
    )(
      f: A => Future[B]
    ): Future[Seq[B]] =
      items.grouped(math.max(1, size)).foldLeft(Future.successful(Seq.empty[B])) {
        (
          acc,
          group
        ) =>
          acc.flatMap(done => Future.sequence(group.map(f)).map(done ++ _))
      }

    def runHost(host: Host): Future[Seq[(String, String, Seq[Cell])]] =
      (host.provider, host.apiKey) match {
        case (Some(provider), Some(apiKey)) if only.forall(_.contains(host.name)) =>
          val listing = TypeSafeServiceFactory.withEngine(engine, provider, apiKey)
          host
            .models(listing)
            .map(_.filter(m => modelFilter.forall(_.exists(m.contains))))
            .recover { case NonFatal(e) =>
              println(
                s"[${host.name}] skipped - the listing failed: ${e.getMessage.take(160)}"
              )
              Nil
            }
            .flatMap { models =>
              println(s"[${host.name}] ${models.size} models: ${models.mkString(", ")}")
              val pace = host.paceMs.getOrElse(paceMs)
              // a host paced for its rate limit runs one model at a time
              inGroups(models, if (host.paceMs.isDefined) 1 else parallel) { model =>
                val service = TypeSafeServiceFactory.withEngine(
                  engine,
                  provider.copy(defaultModel = model),
                  apiKey
                )
                measureModel(host, service, model, pace)
                  .map(cells => (host.name, model, cells))
              }
            }
            .map { rows =>
              listing.close()
              rows
            }

        case _ =>
          println(s"[${host.name}] skipped")
          Future.successful(Nil)
      }

    val all = Future.sequence(hosts.map(runHost)).map(_.flatten)

    val rows =
      try Await.result(all, 3.hours)
      catch {
        case NonFatal(e) =>
          println(s"FAILED: $e")
          Nil
      }

    println()
    println(
      s"[latency] median ms per call, $runs timed calls per size; tokens = the model's own count at the largest size answered"
    )
    println("| model | host | " + sizes.map(s => s"${s}k").mkString(" | ") + " | tokens |")
    println("|---|---|" + sizes.map(_ => "---:").mkString("|") + "|---:|")
    rows.sortBy { case (_, _, cells) =>
      cells.find(_.sizeK == 32).flatMap(_.median).getOrElse(Long.MaxValue)
    }.foreach { case (host, model, cells) =>
      val bySize = cells.map(c => c.sizeK -> c).toMap
      val tokens = cells.filter(_.median.isDefined).lastOption.flatMap(_.inputTokens)
      println(
        s"| $model | $host | " +
          sizes.map(s => bySize.get(s).map(_.text).getOrElse("")).mkString(" | ") +
          s" | ${tokens.getOrElse("-")} |"
      )
    }

    val calls = rows.flatMap(_._3).map(c => c.times.size + c.failures).sum + rows.size
    println(
      f"[done] ${rows.size} models, $calls calls in ${(System.currentTimeMillis() - started) / 1000}%d s"
    )

    out.foreach(_.close())
    engine.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(0)
  }
}
