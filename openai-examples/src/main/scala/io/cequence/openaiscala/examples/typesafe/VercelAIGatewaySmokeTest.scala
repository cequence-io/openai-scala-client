package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{JsonSchema, UserMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeScalaInvalidRequestException,
  TypeSafeScalaNotFoundException,
  TypeSafeScalaUnauthorizedException,
  TypeSafeServiceFactory
}
import play.api.libs.json.{Format, JsValue, Json}

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
 * Live walkthrough of the decision models on Vercel AI Gateway, through its
 * TypeSafe-compatible API (`https://ai-gateway.vercel.sh/typesafe/v1/systemone`, an AI Gateway
 * key in `AI_GATEWAY_API_KEY`): the models it lists, the same review on each of them, a blue
 * square on d1, the OpenAI interface with a JSON schema, and the gateway's own errors. Every
 * section prints PASS / FAIL / SKIP; the exit code is 1 if any failed.
 *
 * A free-tier team (a card on file, no credits bought) serves Convai Innovations' Laya only,
 * at 5 requests per minute per region - the calls here are paced 13 s apart (`pace=<ms>` to
 * change it; `pace=0` with paid credits) - and answers the other models with a 403
 * `no_providers_available` "Free tier users do not have access to this model", reported as
 * SKIP. With paid credits Jev, d1 (and its images), Microsoft-Decision-1 and Luna run too.
 *
 * Live 2026-10-10 (free tier): 6 models listed; Laya answered the review (defect 0.83,
 * negative, severity 1.74 of 0..2) in 0.5-1.4 s and the review schema through the OpenAI
 * interface; an unknown model is a 404 `model_not_found`, a one-level score a 400
 * `invalid_request` ("Too small: expected array to have >=2 items") - both classified; the
 * paid models were skipped.
 */
object VercelAIGatewaySmokeTest {

  private case class Review(
    defect: Boolean,
    sentiment: String,
    severity: Int
  )

  private implicit val reviewFormat: Format[Review] = Json.format[Review]

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

  private val reviewSchema = JsonSchemaDef(
    name = "review",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "defect" -> JsonSchema.Boolean(Some("Does the review report a product defect?")),
          "sentiment" -> JsonSchema.String(
            Some("What is the overall sentiment of the review?"),
            `enum` = Seq("positive", "mixed", "negative")
          ),
          "severity" -> JsonSchema.Integer(
            Some("How severe is the reported problem, 0 (cosmetic) to 2 (product unusable)?"),
            minimum = Some(0),
            maximum = Some(2)
          )
        ),
        required = Seq("defect", "sentiment", "severity")
      )
    )
  )

  private val colorQuestion: Map[String, Question] = Map(
    "color" -> ChoiceQuestion(
      "Which color is the square?",
      "red" -> "A red square",
      "blue" -> "A blue square",
      "green" -> "A green square"
    )
  )

  private def png(colour: Color): Array[Byte] = {
    val image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(colour)
    graphics.fillRect(0, 0, 64, 64)
    graphics.dispose()
    val out = new ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    out.toByteArray
  }

  private def summary(response: SystemOneResponse): String = {
    val sentiment = response.choice("sentiment")
    f"defect ${response.noul("defect").noul}%.3f | ${sentiment.choice} " +
      f"${sentiment.probabilities.getOrElse(sentiment.choice, 0.0)}%.2f | severity " +
      f"${response.score("severity").score}%.2f | ${response.model} | ${response.usage} | " +
      s"request ${response.requestId.getOrElse("-")}"
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    val pace = args.find(_.startsWith("pace=")).map(_.drop(5).toLong).getOrElse(13000L).millis
    val service = TypeSafeServiceFactory(DecisionProviderSettings.vercel)
    val asOpenAI = TypeSafeServiceFactory.asOpenAI(service)
    val failures = new AtomicInteger(0)

    // the free tier allows 5 requests per minute, so every call waits its turn
    def paced[T](f: => Future[T]): Future[T] =
      akka.pattern.after(pace, scheduler)(Future.successful(())).flatMap(_ => f)

    def section(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] = {
      val start = System.currentTimeMillis()
      Try(f).fold(Future.failed, identity).transform {
        case Success(msg) if msg.startsWith("SKIP") =>
          println(s"[SKIP] $name: ${msg.drop(5).trim}")
          Success(())
        case Success(msg) =>
          println(s"[PASS] $name (${System.currentTimeMillis() - start} ms): $msg")
          Success(())
        case Failure(e) =>
          failures.incrementAndGet()
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage.take(300)}")
          Success(())
      }
    }

    // a model the team's tier does not serve is reported, not failed
    def orPaidTier[T](f: Future[String]): Future[String] =
      f.recover {
        case e: TypeSafeScalaUnauthorizedException
            if e.errorType.contains("no_providers_available") =>
          "SKIP paid tier - " + e.getMessage.take(120)
      }

    val all = for {
      models <- section("listModels - the gateway's decision models") {
        service.listModels.map { models =>
          if (models.isEmpty) sys.error("no models listed")
          models.map(m => s"${m.name} (${m.release_date})").mkString(", ")
        }
      }.flatMap(_ => service.listModels.map(_.map(_.name)))

      _ <- section(s"the review on ${DecisionModelId.vercel_laya_free}") {
        paced(service.systemOne(review, questions, DecisionModelId.vercel_laya_free)).map {
          response =>
            if (response.noul("defect").noul < 0.5) sys.error("no defect?")
            summary(response)
        }
      }

      _ <- models.filterNot(_ == DecisionModelId.vercel_laya_free).foldLeft(Future.unit) {
        (
          acc,
          model
        ) =>
          acc.flatMap { _ =>
            section(s"the review on $model") {
              orPaidTier(paced(service.systemOne(review, questions, model)).map(summary))
            }
          }
      }

      _ <- section(
        s"a blue square on ${DecisionModelId.vercel_liquid_d1} - an image_url part in the state"
      ) {
        val square = Json.arr(
          "Which color is the square?",
          Json.obj(
            "type" -> "image_url",
            "image_url" -> Json.obj(
              "url" -> ("data:image/png;base64," + Base64.getEncoder
                .encodeToString(png(Color.BLUE)))
            )
          )
        )
        orPaidTier(
          paced(
            service.systemOne(square, colorQuestion, DecisionModelId.vercel_liquid_d1)
          ).map { response =>
            val answer = response.choice("color")
            if (answer.choice != "blue") sys.error(s"saw ${answer.choice}")
            f"blue ${answer.confidence}%.2f, usage ${response.usage}"
          }
        )
      }

      _ <- section("the OpenAI interface - createChatCompletionWithJSON on laya-free") {
        paced(
          asOpenAI.createChatCompletionWithJSON[Review](
            Seq(UserMessage(review.toString)),
            CreateChatCompletionSettings(model = DecisionModelId.vercel_laya_free)
              .withJsonSchema(reviewSchema)
          )
        ).map { triage =>
          if (!triage.defect) sys.error(s"no defect? $triage")
          triage.toString
        }
      }

      _ <- section("an unknown model - a 404 model_not_found, classified") {
        paced(service.systemOne("x", questions, "nope/nope"))
          .map(_ => "answered?!")
          .recover { case e: TypeSafeScalaNotFoundException =>
            s"refused: ${e.errorType.getOrElse("-")} - ${e.getMessage.take(100)}"
          }
          .map(msg => if (msg.startsWith("answered")) sys.error(msg) else msg)
      }

      _ <- section("a one-level score - the gateway's 400 invalid_request, classified") {
        paced(
          service.systemOne(
            "x",
            Map("s" -> ScoreQuestion("How much?", "one")),
            DecisionModelId.vercel_laya_free
          )
        ).map(_ => "answered?!")
          .recover { case e: TypeSafeScalaInvalidRequestException =>
            s"refused: ${e.errorType.getOrElse("-")} - ${e.getMessage.take(100)}"
          }
          .map(msg => if (msg.startsWith("answered")) sys.error(msg) else msg)
      }
    } yield ()

    Try(Await.result(all, 15.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: $e")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    asOpenAI.close()
    service.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
