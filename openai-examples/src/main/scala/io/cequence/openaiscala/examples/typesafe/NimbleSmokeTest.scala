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
  TypeSafeScalaClientException,
  TypeSafeServiceFactory
}
import play.api.libs.json.{Format, Json, Reads}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Walkthrough of Bespoke Labs' Nimble decision models (`nimble-latest`, today `nimble-v4-max`)
 * through `DecisionProviderSettings.bespoke` (`POST https://api.bespokelabs.ai/v1/systemone`,
 * `Authorization: Bearer`): Bespoke's quickstart example (a noul and a described choice), a
 * score, an object state, the typed `decide[T]`, the OpenAI JSON interface with the schema
 * inferred from a case class, OpenAI's Decisions interface (`asOpenAIDecisions` +
 * `createDecision`), and the refusals - a one-option choice, 65 questions (the preset's cap of
 * 64, refused before I/O), an image (the host reads none, so the chat adapter refuses it
 * before I/O) and an unknown model. Needs `BESPOKE_API_KEY`; `model=nimble-v4-max` picks the
 * model. Every section prints PASS/FAIL, the exit code is 1 if any failed. NOT yet run -
 * written from Bespoke's docs (read 2026-11-05) before a key was available.
 */
object NimbleSmokeTest {

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

  // Bespoke's quickstart example
  private val quickstart =
    "The customer was charged twice and asks for a refund. The service still works."

  private val docsQuestions: Map[String, Question] = Map(
    "refund" -> NoulQuestion("Does the customer request a refund?"),
    "department" -> ChoiceQuestion(
      "Which department should handle this request?",
      "billing" -> "Charges, payments and refunds",
      "technical" -> "Software bugs and outages"
    )
  )

  private val severity = ScoreQuestion(
    "How severe is this problem for the customer?",
    "trivial",
    "minor",
    "moderate",
    "major",
    "critical"
  )

  private val billing = NoulQuestion("Is this text about a billing problem?")

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

    val model = args
      .find(_.startsWith("model="))
      .map(_.stripPrefix("model="))
      .getOrElse(DecisionProviderSettings.bespoke.defaultModel)
    val provider = DecisionProviderSettings.bespoke.copy(defaultModel = model)
    println(s"model: $model")

    val service = TypeSafeServiceFactory(provider)
    val asOpenAI = TypeSafeServiceFactory.asOpenAI(provider)
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
          check(models.map(_.name).contains(model), s"listed $models")
          models.map(_.name).mkString(", ")
        }
      }

      _ <- section("Bespoke's quickstart - a noul and a described choice") {
        service.systemOne(quickstart, docsQuestions).map { r =>
          val refund = r.noul("refund").noul
          val department = r.choice("department")
          check(refund >= 0.5, f"refund $refund%.3f")
          check(department.choice == "billing", s"$department")
          f"refund $refund%.3f, department ${department.choice} (${department.confidence}%.2f), usage ${r.usage}, request ${r.requestId}"
        }
      }

      _ <- section("a score question") {
        service.systemOne(quickstart, Map("severity" -> severity)).map { r =>
          val answer = r.score("severity")
          check(answer.legend.size == 5, s"legend ${answer.legend}")
          check(answer.score <= 3.0, s"severity ${answer.score}")
          f"severity ${answer.score}%.2f"
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
            CreateChatCompletionSettings(model)
          )
          .map { routing =>
            check(routing == Routing(Department.billing, refund = true), s"answered $routing")
            routing.toString
          }
      }

      _ <- section("OpenAI's Decisions interface - createDecision") {
        decisions
          .createDecision(
            DecisionInput.Text(quickstart),
            Seq(
              DecisionQuestion.Predicate(
                "Does the customer request a refund?",
                name = Some("refund")
              )
            ),
            CreateDecisionSettings(model = Some(model))
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

      _ <- section("65 questions are refused before I/O (the cap is 64)") {
        refused(
          service.systemOne(
            "x",
            (1 to 65).map(i => s"q$i" -> (NoulQuestion(s"Question $i?"): Question)).toMap
          )
        )(_.isInstanceOf[IllegalArgumentException])
      }

      _ <- section("an image through the chat adapter is refused before I/O") {
        // with a schema, so the image is what is refused
        refused(
          asOpenAI.createChatCompletionWithJSON[Routing](
            Seq(UserSeqMessage(Seq(TextContent("A photo."), ImageURLContent(bluePngDataUrl)))),
            CreateChatCompletionSettings(model)
          )
        )(e =>
          e.isInstanceOf[OpenAIScalaClientException] && e.getMessage.contains("Image content")
        )
      }

      _ <- section("an unknown model is refused") {
        refused(service.systemOne("x", Map("billing" -> billing), model = "nimble-nope"))(
          _.isInstanceOf[TypeSafeScalaClientException]
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
