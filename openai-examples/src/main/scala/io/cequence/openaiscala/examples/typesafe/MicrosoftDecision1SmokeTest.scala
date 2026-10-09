package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.domain.JsonSchemaDescription
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import play.api.libs.json.{Json, Reads}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of Microsoft-Decision-1 on a Microsoft Foundry deployment (public preview
 * since 2026-10-09): Microsoft's launch example - which support team handles a request - as a
 * choice question, a noul, the typed `decide[T]`, and the latency per call. The model speaks
 * TypeSafe's System One protocol at `<endpoint>/v1/systemone` with a `Bearer` key, so
 * `TypeSafeServiceFactory.microsoftFoundry` serves it like Jev.
 *
 * Deploy Microsoft-Decision-1 in your Foundry subscription first and set `FOUNDRY_BASE_URL`
 * (the endpoint, without `/v1/systemone`) and `FOUNDRY_API_KEY`; `FOUNDRY_MODEL` overrides the
 * model / deployment id (`microsoft-decision-1` by default). Every section prints PASS/FAIL;
 * the exit code is 1 if any failed. The launch post says to confirm the route and header in
 * the Foundry quickstart - a 404 here means the deployment's path differs: `copy` the
 * provider.
 */
object MicrosoftDecision1SmokeTest {

  private case class Triage(
    @JsonSchemaDescription("Does the customer ask for money back?") refund: Boolean,
    @JsonSchemaDescription("The team that should handle the request") team: Team
  )

  private sealed trait Team
  private case object Billing extends Team
  private case object Technical extends Team
  private case object Account extends Team

  private implicit val teamReads: Reads[Team] = Reads.of[String].map {
    case "Billing"   => Billing
    case "Technical" => Technical
    case _           => Account
  }
  private implicit val triageReads: Reads[Triage] = Json.reads[Triage]

  // Microsoft's example: three requests, their expected teams
  private val examples = Seq(
    "I was charged twice." -> "billing",
    "The integration crashes during checkout." -> "technical",
    "I cannot sign in after resetting my password." -> "account"
  )

  private val team = ChoiceQuestion(
    "Which team should handle this customer support request? Select exactly one team based " +
      "on the primary problem the customer needs resolved.",
    "billing" -> "Charges, invoices, refunds, or subscription payments",
    "technical" -> "Software errors, bugs, or integration failures",
    "account" -> "Sign-in, password, or account-access problems"
  )

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val model = sys.env.getOrElse("FOUNDRY_MODEL", TypeSafeModelId.microsoft_decision_1)
    val service = TypeSafeServiceFactory.microsoftFoundry(defaultModel = model)
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
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage}")
          Success(())
      }
    }

    val all = for {
      _ <- examples.foldLeft(Future.unit) { case (acc, (text, expected)) =>
        acc.flatMap(_ =>
          section(s"routing: '$text' -> $expected") {
            service.systemOne(text, Map("team" -> team)).map { response =>
              val answer = response.choice("team")
              if (answer.choice != expected) sys.error(s"chose ${answer.choice}")
              f"${answer.choice} (confidence ${answer.confidence}%.2f), " +
                s"usage ${response.usage}, request ${response.requestId.getOrElse("-")}"
            }
          }
        )
      }
      _ <- section("a noul: is a refund requested?") {
        service
          .systemOne(
            "I was charged twice and want my money back.",
            Map("refund" -> NoulQuestion("Does the customer ask for money back?"))
          )
          .map(r => f"refund ${r.noul("refund").noul}%.3f")
      }
      _ <- section("decide[Triage] - the typed decision") {
        service.decide[Triage]("I was charged twice and want my money back.").map { decision =>
          s"${decision.value}, refund ${decision.noul("refund").noul}%.3f"
        }
      }
    } yield ()

    Try(Await.result(all, 3.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: $e")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    service.close()
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
