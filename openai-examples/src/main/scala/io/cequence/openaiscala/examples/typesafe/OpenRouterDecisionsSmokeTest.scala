package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
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
  DecisionProviderSettings,
  TypeSafeService,
  TypeSafeServiceFactory
}
import io.cequence.wsclient.service.spi.{TransportSettings, WSClientEngineRegistry}
import play.api.libs.json.{Format, JsString, JsValue, Json}

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Live walkthrough of the decision models on OpenRouter, through one key and TypeSafe's
 * protocol - `TypeSafeServiceFactory(DecisionProviderSettings.openRouter)`:
 *
 *   - the decision models OpenRouter lists (`listModels`, `output_modalities=decisions`)
 *   - the same three questions (a noul, a choice, a score about a product review) on each of
 *     them, with the latency - Respan's Span-01 takes noul questions only, over a text state
 *     or a conversation trace (`{"input": [messages], "output": message}`), so it gets those
 *   - the questions as a JSON schema through the OpenAI interface on d1
 *     (`asOpenAI(DecisionProviderSettings.openRouter)` + `createChatCompletionWithJSON[T]`)
 *   - a blue square as an `image_url` part in the state array - how OpenRouter takes images (a
 *     top-level `images` is a 400) - on Clef Omni, d1 and Luna, and through the OpenAI
 *     interface with the image in the user message (live 2026-10-10: blue at 0.95 / 1.00 /
 * 1.00; Jev, Kev, Tev1, Drex, the Solar Decides and Span-01 read an image as text)
 *   - a latency benchmark of the models that answer (`DecisionModelBenchmark`, one shared
 *     engine; pass `nobench` to skip it)
 *
 * Requires `OPENROUTER_API_KEY`; a run costs a fraction of a cent.
 */
object OpenRouterDecisionsSmokeTest {

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

  // Respan's Span-01 judges with nouls only (plain-string instructions), over a text state or a
  // conversation trace
  private def isSpan(model: String) = model.startsWith("respan/")

  // the models that read an image through OpenRouter (live 2026-10-10)
  private val imageModels = Seq(
    TypeSafeModelId.openrouter_clef_omni,
    TypeSafeModelId.openrouter_liquid_d1,
    TypeSafeModelId.openrouter_gpt_6_luna_decisions
  )

  private case class Square(color: String)

  private implicit val squareFormat: Format[Square] = Json.format[Square]

  private val squareSchema = JsonSchemaDef(
    name = "square",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "color" -> JsonSchema.String(
            Some("Which color is the square?"),
            `enum` = Seq("red", "blue", "green")
          )
        ),
        required = Seq("color")
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

  private val spanQuestions: Map[String, Question] = Map(
    "defect" -> NoulQuestion("Does the review report a product defect?"),
    "praise" -> NoulQuestion("Does the review praise the sound?")
  )

  private def summary(response: SystemOneResponse): String =
    if (response.answers.contains("sentiment")) {
      val sentiment = response.choice("sentiment")
      f"defect ${response.noul("defect").noul}%.3f | ${sentiment.choice} " +
        f"${sentiment.probabilities.getOrElse(sentiment.choice, 0.0)}%.2f | severity " +
        f"${response.score("severity").score}%.2f | ${response.model} | ${response.usage}"
    } else
      f"defect ${response.noul("defect").noul}%.3f | praise ${response.noul("praise").noul}%.3f" +
        s" | ${response.model} | ${response.usage}"

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    val engine = WSClientEngineRegistry(TransportSettings())
    val openRouter =
      TypeSafeServiceFactory.withEngine(engine, DecisionProviderSettings.openRouter)
    val asOpenAI = TypeSafeServiceFactory.asOpenAI(DecisionProviderSettings.openRouter)

    // one service per model on the shared engine, the model as its default
    def serviceFor(model: String): TypeSafeService =
      TypeSafeServiceFactory.withEngine(
        engine,
        DecisionProviderSettings.openRouter.copy(defaultModel = model)
      )

    val run = for {
      models <- openRouter.listModels
      _ = println(s"[models] ${models.size}: ${models.map(_.name).mkString(", ")}")

      answered <- models.foldLeft(Future.successful(Seq.empty[String])) {
        (
          acc,
          model
        ) =>
          acc.flatMap { done =>
            val start = System.nanoTime()
            val call =
              if (isSpan(model.name))
                openRouter.systemOne(JsString(reviewText), spanQuestions, model.name)
              else openRouter.systemOne(review, questions, model.name)
            call.map { response =>
              val ms = (System.nanoTime() - start) / 1000000
              println(f"[${model.name}%-40s] $ms%5d ms | ${summary(response)}")
              done :+ model.name
            }.recover { case NonFatal(e) =>
              println(f"[${model.name}%-40s] FAILED: ${e.getMessage.take(160)}")
              done
            }
          }
      }

      triage <- asOpenAI.createChatCompletionWithJSON[Review](
        Seq(UserMessage(review.toString)),
        CreateChatCompletionSettings(model = TypeSafeModelId.openrouter_liquid_d1)
          .withJsonSchema(reviewSchema)
      )
      _ = println(s"[via OpenAI, d1] $triage")

      // images: an image_url part in the state array, next to the question's text
      blue = png(Color.BLUE)
      square = Json.arr(
        "Which color is the square?",
        Json.obj(
          "type" -> "image_url",
          "image_url" -> Json.obj(
            "url" -> ("data:image/png;base64," + Base64.getEncoder.encodeToString(blue))
          )
        )
      )
      _ <- imageModels.foldLeft(Future.successful(())) {
        (
          acc,
          model
        ) =>
          acc.flatMap { _ =>
            openRouter
              .systemOne(square, colorQuestion, model)
              .map { response =>
                val answer = response.choice("color")
                println(
                  f"[image, $model%-32s] ${answer.choice} ${answer.confidence}%.2f | ${response.usage}"
                )
              }
              .recover { case NonFatal(e) =>
                println(f"[image, $model%-32s] FAILED: ${e.getMessage.take(160)}")
              }
          }
      }
      squareViaOpenAI <- asOpenAI.createChatCompletionWithJSON[Square](
        Seq(
          UserSeqMessage(
            TextContent("Which color is the square?") +: VLMContent.of(blue, "square.png")
          )
        ),
        CreateChatCompletionSettings(model = TypeSafeModelId.openrouter_clef_omni)
          .withJsonSchema(squareSchema)
      )
      _ = println(s"[via OpenAI, image, clef-omni] $squareViaOpenAI")

      _ <-
        if (args.contains("nobench")) Future.successful(())
        else
          DecisionModelBenchmark.run(
            answered
              .filterNot(isSpan)
              .filterNot(_ == TypeSafeModelId.openrouter_jev_1_13)
              .map(model => model.split('/').last -> serviceFor(model)),
            Json.stringify(review),
            questions,
            runs = 5
          )
    } yield answered.size -> models.size

    val passed =
      try {
        val (answered, listed) = Await.result(run, 15.minutes)
        println(s"[summary] $answered of $listed models answered")
        answered > 0
      } catch {
        case NonFatal(e) =>
          println(s"FAILED: $e")
          false
      }

    asOpenAI.close()
    openRouter.close()
    engine.close()
    Await.result(system.terminate(), 30.seconds)
    println(if (passed) "ALL PASSED" else "FAILED")
    System.exit(if (passed) 0 else 1)
  }
}
