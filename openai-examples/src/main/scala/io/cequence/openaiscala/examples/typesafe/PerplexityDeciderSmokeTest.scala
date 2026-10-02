package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{
  JsonSchema,
  TextContent,
  UserMessage,
  UserSeqMessage,
  VLMContent
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.{
  TypeSafeService,
  TypeSafeServiceAdapters,
  TypeSafeServiceFactory
}
import play.api.libs.json.{Format, Json}

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Live walkthrough of Perplexity's decision model `pplx-decider-v1-27b` (launched 2026-10-01).
 * Its Decisions API takes the System One questions and answers - plus images in the state - so
 * the typesafe-client talks to it: `TypeSafeServiceFactory.perplexity` points the client at
 * `POST https://api.perplexity.ai/v1/decisions`.
 *
 *   - the docs' example: a noul, a choice and a score about one product review
 *   - an image in the state (a generated blue square, `DecisionImage(bytes)`): which colour,
 *     with the probabilities
 *   - a phone photo's size (4032 x 3024, ~12,000 tiles of 32 x 32 px) refused up front - the
 *     API would let it time out (504) after about a minute
 *   - the same questions as a JSON schema through the OpenAI interface (`perplexityAsOpenAI` +
 *     `createChatCompletionWithJSON[T]`), and once with the image in the user message
 *     (`VLMContent.of(bytes, "square.png")`)
 *   - with `TYPESAFE_API_KEY` / `LIQUID_API_KEY` set, the same native call on Jev and d1
 *   - a latency benchmark of all three (`DecisionModelBenchmark`; pass `nobench` to skip it)
 *
 * Live results (2026-10-02, the day after the launch):
 *
 *   - answers: the docs' example to the third decimal - defect 0.942, sentiment mixed 0.950,
 *     severity 1.78 of 0..2 (367 input tokens, 3 output); Jev and d1 agree (defect 0.940 /
 *     0.988, mixed 0.940 / 0.869, severity 1.99 / 1.88); the blue square is blue at 0.993 (161
 *     input tokens), through the OpenAI interface too (`Square(blue, 0.9898)`)
 *   - median latency per call (min - max), a kept-alive connection:
 *     {{{
 *     questions   pplx-decider        Jev                 d1 (free tier)
 *       1          213 ms (201 - 217)  243 ms (213 - 255)  837 ms (287 - 1012)
 *       3          210 ms (201 - 225)  246 ms (215 - 262)  295 ms (234 - 560)
 *      10          277 ms (264 - 281)  244 ms (211 - 335)  584 ms (287 - 685)
 *      20          335 ms (326 - 502)  261 ms (224 - 312)  739 ms (516 - 1097)
 *      40          457 ms (447 - 472)  274 ms (222 - 304)  732 ms (421 - 1081)
 *     }}}
 *     the decider takes ~210 ms plus ~7 ms per question beyond three, the fastest of the three
 *     for a few questions; Jev stays flat, d1's free tier varies from run to run. Another run
 *     agreed within ~10 ms; the first one, minutes earlier, had medians of ~1.1 s at one and
 *     three questions (minimum 200 ms) - a slow stretch on Perplexity's side.
 *
 * Requires `PERPLEXITY_API_KEY` (or `SONAR_API_KEY`); input costs $0.04 per million tokens, a
 * run well under a cent.
 */
object PerplexityDeciderSmokeTest {

  private case class Review(
    defect: Boolean,
    sentiment: String,
    severity: Int
  )

  private implicit val reviewFormat: Format[Review] = Json.format[Review]

  private case class Square(
    color: String,
    color_confidence: Double
  )

  private implicit val squareFormat: Format[Square] = Json.format[Square]

  private val review = Json.obj(
    "title" -> "Battery died after two weeks",
    "review" -> "The headphones sound great, but the battery stopped charging after two weeks."
  )

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

  // the same questions as a closed-vocabulary schema
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

  private val squareSchema = JsonSchemaDef(
    name = "square",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "color" -> JsonSchema.String(
            Some("What color is the square?"),
            `enum` = Seq("red", "blue", "green")
          ),
          "color_confidence" -> JsonSchema.Number()
        ),
        required = Seq("color", "color_confidence")
      )
    )
  )

  private val colorQuestion: Map[String, Question] = Map(
    "color" -> ChoiceQuestion.ofLabels("What color is the square?", "red", "blue", "green")
  )

  private def png(
    width: Int,
    height: Int,
    color: Color
  ): Array[Byte] = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(color)
    graphics.fillRect(0, 0, width, height)
    graphics.dispose()
    val out = new ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    out.toByteArray
  }

  private def show(
    label: String,
    response: SystemOneResponse
  ): Unit = {
    val sentiment = response.choice("sentiment")
    val severity = response.score("severity")
    println(
      s"[$label] model ${response.model}, usage ${response.usage}, request ${response.requestId.getOrElse("-")}"
    )
    println(f"  defect    : ${response.noul("defect").noul}%.3f")
    println(
      f"  sentiment : ${sentiment.choice} (confidence ${sentiment.confidence}%.3f) " +
        sentiment.ranked.map { case (option, p) => f"$option=$p%.3f" }.mkString(", ")
    )
    println(
      f"  severity  : ${severity.score}%.2f of 0..2 (confidence ${severity.confidence}%.3f)"
    )
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler
    implicit val retrySettings: RetrySettings =
      RetrySettings(maxRetries = 3, delayOffset = 5.seconds)

    val perplexity = TypeSafeServiceFactory.perplexity()
    val pplx =
      TypeSafeServiceAdapters.retry(perplexity, log = Some(m => println(s"[retry] $m")))
    val pplxAsOpenAI = TypeSafeServiceFactory.asOpenAI(pplx, imageInput = true)

    def keyed(env: String) = sys.env.get(env).exists(_.trim.nonEmpty)
    val jev = if (keyed("TYPESAFE_API_KEY")) Some(TypeSafeServiceFactory()) else None
    val d1 = if (keyed("LIQUID_API_KEY")) Some(TypeSafeServiceFactory.liquid()) else None
    val others: Seq[(String, TypeSafeService)] = jev.map("Jev" -> _).toSeq ++ d1.map("d1" -> _)

    val blue = png(256, 256, new Color(20, 60, 220))

    val run = for {
      models <- pplx.listModels
      _ = println(s"[models] ${models.map(_.name).mkString(", ")}")

      native <- pplx.systemOne(review, questions)
      _ = show("pplx-decider", native)

      square <- pplx.systemOne(
        Json.arr("Which color is the square?", DecisionImage(blue)),
        colorQuestion
      )
      _ = {
        val color = square.choice("color")
        println(
          f"[image] ${color.choice} (confidence ${color.confidence}%.3f) " +
            color.ranked.map { case (option, p) => f"$option=$p%.3f" }.mkString(", ") +
            s", usage ${square.usage}"
        )
      }

      _ =
        try {
          DecisionImage(png(4032, 3024, Color.DARK_GRAY))
          println("[oversized image] NOT refused")
        } catch {
          case e: IllegalArgumentException =>
            println(s"[oversized image] refused: ${e.getMessage}")
        }

      triage <- pplxAsOpenAI.createChatCompletionWithJSON[Review](
        Seq(UserMessage(review.toString)),
        CreateChatCompletionSettings(model = TypeSafeModelId.pplx_decider_v1_27b)
          .withJsonSchema(reviewSchema)
      )
      _ = println(s"[via OpenAI] $triage")

      squareViaOpenAI <- pplxAsOpenAI.createChatCompletionWithJSON[Square](
        Seq(
          UserSeqMessage(
            TextContent("Which color is the square?") +: VLMContent.of(blue, "square.png")
          )
        ),
        CreateChatCompletionSettings(model = TypeSafeModelId.pplx_decider_v1_27b)
          .withJsonSchema(squareSchema)
      )
      _ = println(s"[via OpenAI, image] $squareViaOpenAI")

      _ <- others.foldLeft(Future.successful(())) { case (acc, (label, service)) =>
        acc.flatMap { _ =>
          TypeSafeServiceAdapters
            .retry(service)
            .systemOne(review, questions)
            .map(show(label, _))
        }
      }

      _ <-
        if (args.contains("nobench")) Future.successful(())
        else
          DecisionModelBenchmark.run(
            ("pplx-decider" -> perplexity) +: others,
            Json.stringify(review),
            questions
          )
    } yield ()

    val passed =
      try {
        Await.result(run, 10.minutes)
        true
      } catch {
        case NonFatal(e) =>
          println(s"FAILED: $e")
          false
      }

    pplxAsOpenAI.close()
    others.foreach(_._2.close())
    Await.result(system.terminate(), 30.seconds)
    println(if (passed) "ALL PASSED" else "FAILED")
    System.exit(if (passed) 0 else 1)
  }
}
