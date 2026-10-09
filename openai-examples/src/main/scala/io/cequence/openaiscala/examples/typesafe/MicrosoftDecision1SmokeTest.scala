package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{JsonSchema, JsonSchemaDescription, UserMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeScalaTokenCountExceededException,
  TypeSafeServiceFactory
}
import play.api.libs.json.{Format, Json, Reads}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of Microsoft-Decision-1 on a Microsoft Foundry resource (public preview
 * since 2026-10-09): the resource's deployments of the model, Microsoft's launch example -
 * which support team handles a request - as a choice question, a noul, a score with its
 * legend, an object state, the typed `decide[T]`, the OpenAI interface with a JSON schema, and
 * the limits the host enforces. The model speaks TypeSafe's System One protocol at
 * `<endpoint>/providers/microsoft/v1/systemone` with a `Bearer` key, so
 * `TypeSafeServiceFactory.microsoftFoundry` serves it like Jev.
 *
 * Deploy Microsoft-Decision-1 in your Foundry resource first - under a name of your own
 * (Foundry proposes `Microsoft-Decision-1`, which Azure's reserved-word rule refuses) - and
 * set `FOUNDRY_BASE_URL` (the resource endpoint, `https://<resource>.services.ai.azure.com`)
 * and `FOUNDRY_API_KEY`; `FOUNDRY_MODEL` is the deployment name (`decision-1` by default).
 * Every section prints PASS/FAIL; the exit code is 1 if any failed.
 *
 * Live 2026-10-09 (Sweden Central, the deployment `decision-1`, all 10 sections passed): the
 * three requests routed right at 0.997-0.998 confidence in 0.2-0.7 s each (~80 input tokens),
 * refund 0.999, "outrageous" scored 1.62 of 0..2 (legend calm < annoyed < furious),
 * `decide[Triage]` = `Triage(true, Billing)` (2.8 s - the first typed call derives the
 * schema), `Routing(billing, true)` through the OpenAI interface in 0.4 s; 128 nouls answered
 * in 0.6 s (2,610 input tokens: ~20 per noul, the state read once); a 165k-token request is a
 * 422 token-count error (1.2 s) and 256 questions are refused before I/O.
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

  // the same decision as a JSON schema, through the OpenAI interface
  private case class Routing(
    team: String,
    refund: Boolean
  )
  private implicit val routingFormat: Format[Routing] = Json.format[Routing]

  private val routingSchema = JsonSchemaDef(
    name = "routing",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "team" -> JsonSchema.String(
            Some("Which team should handle this customer support request?"),
            `enum` = Seq("billing", "technical", "account")
          ),
          "refund" -> JsonSchema.Boolean(Some("Does the customer ask for money back?"))
        ),
        required = Seq("team", "refund")
      )
    )
  )

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

  private val refund = NoulQuestion("Does the customer ask for money back?")

  private val anger = ScoreQuestion(
    "How angry is the customer?",
    "calm",
    "annoyed",
    "furious"
  )

  def main(args: Array[String]): Unit = {
    // the JSON helper retries on a scheduler
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    val deployment =
      sys.env.getOrElse("FOUNDRY_MODEL", DecisionProviderSettings.FoundryDefaultDeployment)
    val service = TypeSafeServiceFactory.microsoftFoundry(deployment = deployment)
    val asOpenAI = TypeSafeServiceFactory.asOpenAI(service)
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
      _ <- section(
        s"listModels - the resource's deployments of the model, '$deployment' among them"
      ) {
        service.listModels.map { models =>
          if (!models.exists(_.name == deployment))
            sys.error(s"deployment '$deployment' not listed: ${models.map(_.name)}")
          models.map(m => s"${m.name} (${m.description}, ${m.release_date})").mkString("; ")
        }
      }
      _ <- examples.foldLeft(Future.unit) { case (acc, (text, expected)) =>
        acc.flatMap(_ =>
          section(s"routing: '$text' -> $expected") {
            service.systemOne(text, Map("team" -> team)).map { response =>
              val answer = response.choice("team")
              if (answer.choice != expected) sys.error(s"chose ${answer.choice}")
              f"${answer.choice} (confidence ${answer.confidence}%.3f), model ${response.model}, " +
                s"usage ${response.usage}, request ${response.requestId.getOrElse("-")}"
            }
          }
        )
      }
      _ <- section("a noul and a score (legend 0..2) in one request") {
        service
          .systemOne(
            "I was charged twice and want my money back. This is outrageous!",
            Map("refund" -> refund, "anger" -> anger)
          )
          .map { r =>
            val score = r.score("anger")
            if (r.noul("refund").noul < 0.5) sys.error("no refund requested?")
            if (score.score < 1.5) sys.error(f"anger ${score.score}%.2f - not furious?")
            f"refund ${r.noul("refund").noul}%.3f, anger ${score.score}%.2f of 0..${score.maxLevel} " +
              s"(legend ${score.legend.toSeq.sortBy(_._1).map(_._2).mkString(" < ")}), usage ${r.usage}"
          }
      }
      _ <- section("an object state - {instructions, message}") {
        service
          .systemOne(
            Json.obj(
              "instructions" -> "Classify the support ticket.",
              "message" -> "I cannot sign in after resetting my password."
            ),
            Map("team" -> team)
          )
          .map { r =>
            val answer = r.choice("team")
            if (answer.choice != "account") sys.error(s"chose ${answer.choice}")
            f"${answer.choice} (${answer.confidence}%.3f)"
          }
      }
      _ <- section("decide[Triage] - the typed decision") {
        service.decide[Triage]("I was charged twice and want my money back.").map { decision =>
          if (decision.value != Triage(refund = true, Billing))
            sys.error(s"decided ${decision.value}")
          f"${decision.value}, refund ${decision.noul("refund").noul}%.3f"
        }
      }
      _ <- section("the OpenAI interface - createChatCompletionWithJSON with a schema") {
        asOpenAI
          .createChatCompletionWithJSON[Routing](
            Seq(UserMessage("I was charged twice and want my money back.")),
            CreateChatCompletionSettings(model = deployment).withJsonSchema(routingSchema),
            jsonSchemaModels = Seq(deployment)
          )
          .map { routing =>
            if (routing != Routing("billing", refund = true)) sys.error(s"answered $routing")
            routing.toString
          }
      }
      _ <- section("128 noul questions in one request") {
        val questions =
          (1 to 128).map(i => s"q$i" -> NoulQuestion(s"Is statement $i about billing?"))
        service.systemOne("I was charged twice.", questions.toMap).map { r =>
          if (r.answers.size != 128) sys.error(s"${r.answers.size} answers")
          s"${r.answers.size} answers, usage ${r.usage}"
        }
      }
      _ <- section("the token limit - a ~165k-token request is a token-count exception") {
        val state =
          "The customer wrote a very long message about being charged twice. " * 10000
        service
          .systemOne(state, Map("refund" -> refund))
          .map(r => s"accepted?! usage ${r.usage}")
          .recover { case e: TypeSafeScalaTokenCountExceededException =>
            s"refused: ${e.getMessage.take(140)}"
          }
          .map { msg =>
            if (msg.startsWith("accepted")) sys.error(msg) else msg
          }
      }
      _ <- section("256 questions are refused before any I/O") {
        val questions =
          (1 to 256).map(i => s"q$i" -> NoulQuestion(s"Is statement $i about billing?"))
        Future
          .fromTry(Try(service.systemOne("x", questions.toMap)))
          .flatten
          .map(_ => "accepted?!")
          .recover { case e: IllegalArgumentException => s"refused: ${e.getMessage}" }
          .map(msg => if (msg.startsWith("accepted")) sys.error(msg) else msg)
      }
    } yield ()

    Try(Await.result(all, 5.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: $e")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    service.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
