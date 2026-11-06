package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.decisions.{
  CreateDecisionSettings,
  DecisionInput,
  DecisionQuestion
}
import io.cequence.openaiscala.domain.{
  ImageURLContent,
  JsonSchemaDescription,
  TextContent,
  UserMessage,
  UserSeqMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeScalaNotFoundException,
  TypeSafeServiceFactory
}
import play.api.libs.json.{Format, Json, Reads}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of Sarvam Prism (`sarvam-prism`, Sarvam's System-1 decision model built on
 * Sarvam 105B - English, 10 Indian languages and Hinglish; announced 2026-11-03) through
 * `DecisionProviderSettings.sarvam` (`POST https://api.sarvam.ai/v2/systemone`, the key in an
 * `api-subscription-key` header): Sarvam's docs example (a choice with described options, a
 * score with its legend, a noul), a Hindi state, the typed `decide[T]`, the OpenAI JSON
 * interface with the schema inferred from a case class, OpenAI's Decisions interface
 * (`asOpenAIDecisions` + `createDecision`), and the refusals - a one-option choice and eleven
 * score levels (the client refuses both up front; a raw request would be a 4xx), an image (the
 * host reads none, so the chat adapter refuses it before I/O). Needs `SARVAM_API_KEY`; every
 * section prints PASS/FAIL, the exit code is 1 if any failed. All 11 passed live on 2026-11-04
 * (~0.5 s per call from Europe; a fraction of a cent per run).
 */
object SarvamPrismSmokeTest {

  private case class Triage(
    @JsonSchemaDescription("Does the customer ask for money back?") refund: Boolean,
    @JsonSchemaDescription("The team that should handle the request") team: Team
  )

  private sealed trait Team
  private case object Billing extends Team
  private case object Technical extends Team
  private case object Sales extends Team

  private implicit val teamReads: Reads[Team] = Reads.of[String].map {
    case "Billing"   => Billing
    case "Technical" => Technical
    case _           => Sales
  }
  private implicit val triageReads: Reads[Triage] = Json.reads[Triage]

  object Department extends Enumeration {
    val billing, technical, sales = Value
  }
  private implicit val departmentFormat: Format[Department.Value] = Json.formatEnum(Department)

  case class Routing(
    @JsonSchemaDescription(
      "Which team should handle this request?"
    ) department: Department.Value,
    @JsonSchemaDescription("Does the customer ask for money back?") refund: Boolean
  )
  private implicit val routingFormat: Format[Routing] = Json.format[Routing]

  // Sarvam's docs example
  private val stripe =
    "My Stripe integration stopped working and all checkout payments are failing."

  private val docsQuestions: Map[String, Question] = Map(
    "department" -> ChoiceQuestion(
      "Which team should handle this?",
      "billing" -> "Payments or invoices",
      "sales" -> "New business enquiries",
      "technical" -> "Bugs or outages"
    ),
    "severity" -> ScoreQuestion(
      "Rate the severity.",
      "trivial",
      "minor",
      "moderate",
      "major",
      "critical"
    ),
    "urgent" -> NoulQuestion("Is this urgent and revenue-impacting?")
  )

  private val billing = NoulQuestion("Is this text about a billing problem?")

  // "I was charged twice for my plan and want a refund." in Hindi
  private val hindi = "मुझसे मेरे प्लान के लिए दो बार पैसे काटे गए हैं और मुझे रिफंड चाहिए।"

  private def bluePngDataUrl: String = {
    val image =
      new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(java.awt.Color.BLUE)
    graphics.fillRect(0, 0, 64, 64)
    graphics.dispose()
    val bytes = new java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(image, "png", bytes)
    "data:image/png;base64," + java.util.Base64.getEncoder.encodeToString(bytes.toByteArray)
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    val service = TypeSafeServiceFactory(DecisionProviderSettings.sarvam)
    val asOpenAI = TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.sarvam)
    val decisions = TypeSafeServiceFactory.asOpenAIDecisions(service)
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

    def check(
      condition: Boolean,
      message: => String
    ): Unit = if (!condition) throw new IllegalStateException(message)

    // a refusal expected before or at the API
    def refused(
      call: => Future[_]
    )(
      expected: Throwable => Boolean
    ): Future[String] =
      Try(call).fold(Future.failed, identity).transform {
        case Success(_) => Failure(new IllegalStateException("not refused"))
        case Failure(e) if expected(e) =>
          Success(s"${e.getClass.getSimpleName}: ${e.getMessage.take(140)}")
        case Failure(e) => Failure(e)
      }

    val run = for {
      _ <- section("the models - a fixed list") {
        service.listModels.map { models =>
          check(models.map(_.name) == Seq("sarvam-prism"), s"listed $models")
          models.map(_.name).mkString(", ")
        }
      }

      _ <- section("Sarvam's docs example - a choice, a score with its legend, a noul") {
        service.systemOne(stripe, docsQuestions).map { r =>
          val department = r.choice("department")
          val severity = r.score("severity")
          val urgent = r.noul("urgent").noul
          check(
            department.choice == "technical" || department.choice == "billing",
            s"$department"
          )
          check(severity.legend.size == 5, s"legend ${severity.legend}")
          check(severity.score >= 2.5, s"severity ${severity.score}")
          check(urgent >= 0.5, f"urgent $urgent%.3f")
          f"department ${department.choice} (${department.confidence}%.2f), severity ${severity.score}%.2f, urgent $urgent%.3f, usage ${r.usage}, request ${r.requestId}"
        }
      }

      _ <- section("an object state (rendered as compact JSON)") {
        service
          .systemOne(
            Json.obj("ticket" -> "I was charged twice for my plan.", "plan" -> "pro"),
            Map("billing" -> billing)
          )
          .map { r =>
            check(r.noul("billing").noul >= 0.5, s"billing ${r.noul("billing").noul}")
            f"billing ${r.noul("billing").noul}%.3f"
          }
      }

      _ <- section("a Hindi state") {
        service.systemOne(hindi, Map("billing" -> billing)).map { r =>
          check(r.noul("billing").noul >= 0.5, s"billing ${r.noul("billing").noul}")
          f"billing ${r.noul("billing").noul}%.3f"
        }
      }

      _ <- section("decide[Triage] - the typed decision") {
        service.decide[Triage]("I was charged twice and want my money back.").map { decision =>
          check(decision.value == Triage(refund = true, Billing), s"decided ${decision.value}")
          f"${decision.value}, refund ${decision.noul("refund").noul}%.3f"
        }
      }

      _ <- section(
        "the OpenAI interface - createChatCompletionWithJSON[Routing], the schema inferred"
      ) {
        asOpenAI
          .createChatCompletionWithJSON[Routing](
            Seq(UserMessage("I was charged twice and want my money back.")),
            CreateChatCompletionSettings(DecisionProviderSettings.sarvam.defaultModel)
          )
          .map { routing =>
            check(routing == Routing(Department.billing, refund = true), s"answered $routing")
            routing.toString
          }
      }

      _ <- section("OpenAI's Decisions interface - createDecision") {
        decisions
          .createDecision(
            DecisionInput.Text(stripe),
            Seq(
              DecisionQuestion.Predicate(
                "Is this urgent and revenue-impacting?",
                name = Some("urgent")
              )
            ),
            CreateDecisionSettings(model = Some(DecisionProviderSettings.sarvam.defaultModel))
          )
          .map { decision =>
            check(decision.answers.nonEmpty, "no answers")
            decision.answers.toString.take(160)
          }
      }

      _ <- section("a choice of one option is refused") {
        refused(
          service.systemOne(
            "x",
            Map("q" -> ChoiceQuestion.ofLabels("Which team?", "billing"))
          )
        )(_ => true)
      }

      _ <- section("eleven score levels are refused") {
        refused(
          service.systemOne(
            "x",
            Map("q" -> ScoreQuestion("How much?", (1 to 11).map(_.toString): _*))
          )
        )(_ => true)
      }

      _ <- section("an image through the chat adapter is refused before I/O") {
        // with a schema, so the image is what is refused
        refused(
          asOpenAI.createChatCompletionWithJSON[Routing](
            Seq(UserSeqMessage(Seq(TextContent("A photo."), ImageURLContent(bluePngDataUrl)))),
            CreateChatCompletionSettings(DecisionProviderSettings.sarvam.defaultModel)
          )
        )(e =>
          e.isInstanceOf[OpenAIScalaClientException] && e.getMessage.contains("Image content")
        )
      }

      _ <- section("an unknown model is a 404") {
        refused(service.systemOne("x", Map("billing" -> billing), model = "sarvam-nope"))(
          _.isInstanceOf[TypeSafeScalaNotFoundException]
        )
      }
    } yield ()

    Try(Await.result(run, 5.minutes))
    service.close()
    asOpenAI.close()
    println(
      if (failures.get == 0)
        "ALL PASSED"
      else
        s"${failures.get} FAILED"
    )
    Await.result(system.terminate(), 30.seconds)
    System.exit(
      if (failures.get == 0)
        0
      else
        1
    )
  }
}
