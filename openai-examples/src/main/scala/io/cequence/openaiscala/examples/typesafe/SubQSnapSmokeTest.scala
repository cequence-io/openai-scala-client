package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
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
  TypeSafeScalaInvalidRequestException,
  TypeSafeScalaTokenCountExceededException,
  TypeSafeServiceFactory
}
import play.api.libs.json.{Format, JsString, Json, Reads}

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of SubQ Snap (`subq-snap-preview`, Subquadratic's long-context decision
 * model, public preview since 2026-10-12) through `DecisionProviderSettings.subq`: SubQ's docs
 * example (a noul gate, the owning team as a choice, a score with its legend), an object
 * state, the typed `decide[T]`, the OpenAI interface with the schema inferred from a case
 * class, images (as SubQ's `{mediaType, base64}` objects - lifted out of the state, and from a
 * chat message), a ~95k-token state, its token limit (295,957 tokens) and two refusals. Needs
 * `SUBQ_API_KEY`; every section prints PASS/FAIL, the exit code is 1 if any failed (~$0.02 per
 * run, mostly the long states).
 */
object SubQSnapSmokeTest {

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

  // the same decision through the OpenAI interface - no schema in the settings: it is
  // inferred from the case class
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

  case class Square(@JsonSchemaDescription("Is the image a solid blue square?") blue: Boolean)
  private implicit val squareFormat: Format[Square] = Json.format[Square]

  // SubQ's docs example
  private val patient =
    "Patient with asthma reports a sore throat, fever since yesterday, and shortness of breath walking to the kitchen this morning."

  private val docsQuestions: Map[String, Question] = Map(
    "escalate" -> NoulQuestion(
      "Should a nurse see this chat before the patient books anything?",
      "Breathing trouble, chest pain, or a flare of a known condition",
      "Stable symptoms the patient can book for themselves"
    ),
    "queue" -> ChoiceQuestion(
      "Which team should own this chat?",
      "nurse_line" -> "Needs a nurse to assess symptoms today",
      "primary_care" -> "Book with the patient's own doctor",
      "pharmacy" -> "Refill or medication question only",
      "front_desk" -> "Anything else"
    ),
    "acuity" -> ScoreQuestion(
      "How soon should someone respond?",
      "Within a week",
      "Within a day",
      "Within an hour"
    )
  )

  private val billing = NoulQuestion("Is this text about a billing problem?")

  private def bluePngDataUrl: String = {
    val image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(Color.BLUE)
    graphics.fillRect(0, 0, 64, 64)
    graphics.dispose()
    val bytes = new ByteArrayOutputStream()
    ImageIO.write(image, "png", bytes)
    "data:image/png;base64," + Base64.getEncoder.encodeToString(bytes.toByteArray)
  }

  // SubQ counts this 15-word sentence as exactly 19 tokens, plus 87 for one question (live
  // 2026-10-12: every answered request from 1 to 15,572 sentences matched 19 * n + 87)
  private def report(words: Int) = {
    val sentence =
      "The quarterly report lists revenue, costs, staffing, and a customer complaint about a double charge. "
    sentence * (words / sentence.split(' ').length + 1)
  }

  def main(args: Array[String]): Unit = {
    // the JSON helper retries on a scheduler
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    val service = TypeSafeServiceFactory(DecisionProviderSettings.subq)
    val asOpenAI = TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.subq)
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

    // a refusal the API is expected to answer with
    def refused[E <: Throwable](
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
      _ <- section("the models - a fixed list, its /v1/models may list the chat model too") {
        service.listModels.map { models =>
          check(models.map(_.name) == Seq("subq-snap-preview"), s"listed $models")
          models
            .map(m => s"${m.name} (${m.input_modalities.getOrElse(Nil).mkString("+")})")
            .mkString
        }
      }

      _ <- section(
        "SubQ's docs example - a noul gate, the owning team, a score with its legend"
      ) {
        service.systemOne(patient, docsQuestions).map { r =>
          val escalate = r.noul("escalate").noul
          val queue = r.choice("queue")
          val acuity = r.score("acuity")
          check(escalate >= 0.5, f"escalate $escalate%.3f")
          check(queue.choice == "nurse_line", s"queue ${queue.choice}")
          check(acuity.score >= 1.0, s"acuity ${acuity.score}")
          f"escalate $escalate%.3f, queue ${queue.choice} (confidence ${queue.confidence}%.2f), acuity ${acuity.score}%.2f, usage ${r.usage}"
        }
      }

      _ <- section("an object state") {
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
            CreateChatCompletionSettings(DecisionProviderSettings.subq.defaultModel)
          )
          .map { routing =>
            check(routing == Routing(Department.billing, refund = true), s"answered $routing")
            routing.toString
          }
      }

      png = bluePngDataUrl

      _ <- section("an image in the state - sent as a {mediaType, base64} object") {
        service
          .systemOne(
            Json.arr("A photo.", DecisionImage.fromDataUrl(png)),
            Map("blue" -> NoulQuestion("Is the image a solid blue square?"))
          )
          .map { r =>
            check(r.noul("blue").noul >= 0.9, s"blue ${r.noul("blue").noul}")
            f"blue ${r.noul("blue").noul}%.3f, usage ${r.usage}"
          }
      }

      _ <- section("an image through the OpenAI interface") {
        asOpenAI
          .createChatCompletionWithJSON[Square](
            Seq(UserSeqMessage(Seq(TextContent("A photo."), ImageURLContent(png)))),
            CreateChatCompletionSettings(DecisionProviderSettings.subq.defaultModel)
          )
          .map { square =>
            check(square.blue, s"answered $square")
            square.toString
          }
      }

      _ <- section("a long state - ~95k tokens") {
        service.systemOne(JsString(report(75000)), Map("billing" -> billing)).map { r =>
          val tokens = r.usage.input_tokens.getOrElse(0)
          check(tokens > 90000, s"only $tokens tokens")
          f"billing ${r.noul("billing").noul}%.3f over $tokens tokens"
        }
      }

      _ <- section("over its context - a ~300k-token state is a token-count error") {
        refused(service.systemOne(JsString(report(240000)), Map("billing" -> billing))) {
          _.isInstanceOf[TypeSafeScalaTokenCountExceededException]
        }
      }

      _ <- section("an unknown model - its chat model subq-preview - is a 422") {
        refused(
          service.systemOne(JsString("x"), Map("billing" -> billing), model = "subq-preview")
        )(_.isInstanceOf[TypeSafeScalaInvalidRequestException])
      }

      _ <- section("a question name over 100 characters is a 422") {
        refused(service.systemOne("x", Map(("q" * 101) -> billing)))(
          _.isInstanceOf[TypeSafeScalaInvalidRequestException]
        )
      }
    } yield ()

    Try(Await.result(run, 10.minutes))
    service.close()
    asOpenAI.close()
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
