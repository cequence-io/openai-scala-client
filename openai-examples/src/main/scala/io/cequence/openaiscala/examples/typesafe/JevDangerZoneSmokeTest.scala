package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.decisions.{
  CreateDecisionSettings,
  DecisionAnswer,
  DecisionChoice,
  DecisionContent,
  DecisionInput,
  DecisionMessage,
  DecisionQuestion,
  DecisionValue
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ImageURLContent,
  JsonSchemaDescription,
  TextContent,
  UserSeqMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeScalaApiUsageException,
  TypeSafeScalaTokenCountExceededException,
  TypeSafeServiceFactory
}
import play.api.libs.json.{Format, JsString, Json, Reads}

import java.awt.image.BufferedImage
import java.awt.{Color, Polygon}
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of TypeSafe's "JevDay" release (2026-10-14): Jev 1.14 (`jev-1.14.0`, now
 * `jev-preview`) and the "danger zone" features - images (Jev 1.13 and 1.14; sent as
 * TypeSafe's assets with the opt-in header, both by the client itself), the box question (Jev
 * 1.14) and long context (`DecisionProviderSettings.typeSafeLongContext`). Needs
 * `TYPESAFE_API_KEY`.
 *
 * The models, an image in the state on both builds, a reference picture in a question's
 * instructions, options defined by example pictures, boxes around two shapes (and a low
 * confidence for an absent one), a box refused by Jev 1.13, `decide[T]` on an image, the
 * OpenAI interface with an image (the schema inferred from a case class), a `BoundingBox`
 * field answered as a box by the JSON schema adapter and by `decide[T]`, OpenAI's Decisions
 * interface on Jev with an `input_image` and with long context, a too-small image refused
 * before sending, a ~100k-token state with long context (and refused without it) and a few
 * multilingual tickets on both builds. Every section prints PASS/FAIL; the exit code is 1 if
 * any failed (~$0.01 per run, mostly the long state).
 */
object JevDangerZoneSmokeTest {

  sealed trait ShapeKind
  case object Circle extends ShapeKind
  case object Square extends ShapeKind
  case object Triangle extends ShapeKind

  // used by the Json.reads macro below (an unused-private check would not see it)
  implicit val shapeKindReads: Reads[ShapeKind] = Reads.of[String].map {
    case "Circle" => Circle
    case "Square" => Square
    case _        => Triangle
  }

  case class Shape(
    @JsonSchemaDescription("Is the shape in the image red?") red: Boolean,
    @JsonSchemaDescription("Which shape is in the image?") kind: ShapeKind
  )
  private implicit val shapeReads: Reads[Shape] = Json.reads[Shape]

  case class Colour(@JsonSchemaDescription("Is the shape in the photo red?") red: Boolean)
  private implicit val colourFormat: Format[Colour] = Json.format[Colour]

  // a BoundingBox field is asked as a box question - by the JSON schema adapter and decide[T]
  case class Detection(
    @JsonSchemaDescription("the red rectangle in the image") red: BoundingBox,
    @JsonSchemaDescription("Is there a red rectangle in the image?") visible: Boolean
  )
  private implicit val detectionFormat: Format[Detection] = Json.format[Detection]

  private def png(image: BufferedImage): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    ImageIO.write(image, "png", bytes)
    bytes.toByteArray
  }

  private def canvas(
    width: Int,
    height: Int
  )(
    draw: java.awt.Graphics2D => Unit
  ): Array[Byte] = {
    val image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(Color.WHITE)
    graphics.fillRect(0, 0, width, height)
    draw(graphics)
    graphics.dispose()
    png(image)
  }

  private val redCircle = canvas(512, 512) { g =>
    g.setColor(Color.RED); g.fillOval(128, 128, 256, 256)
  }
  private val blueSquare = canvas(512, 512) { g =>
    g.setColor(Color.BLUE); g.fillRect(128, 128, 256, 256)
  }
  private val greenTriangle = canvas(512, 512) { g =>
    g.setColor(Color.GREEN.darker)
    g.fillPolygon(new Polygon(Array(256, 128, 384), Array(128, 384, 384), 3))
  }
  // a red rectangle at (left 0.25, top 0.5, right 0.5, bottom 0.8) and a blue circle at (0.7,
  // 0.133, 0.875, 0.367) on an 800 x 600 canvas
  private val twoShapes = canvas(800, 600) { g =>
    g.setColor(Color.RED); g.fillRect(200, 300, 200, 180)
    g.setColor(Color.BLUE); g.fillOval(560, 80, 140, 140)
  }

  private val sentence =
    "The quarterly report lists revenue, costs, staffing, and a customer complaint about a double charge. "

  def main(args: Array[String]): Unit = {
    System.setProperty("java.awt.headless", "true")
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler

    val jev = TypeSafeServiceFactory(DecisionProviderSettings.typeSafe)
    val longContext = TypeSafeServiceFactory(DecisionProviderSettings.typeSafeLongContext)
    val asOpenAI = TypeSafeServiceFactory.asOpenAI()
    val openAIDecisions =
      TypeSafeServiceFactory.asOpenAIDecisions(DecisionProviderSettings.typeSafe)
    val openAILongContext =
      TypeSafeServiceFactory.asOpenAIDecisions(DecisionProviderSettings.typeSafeLongContext)
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

    def refused(
      call: => Future[_]
    )(
      expected: Throwable => Boolean
    ): Future[String] =
      Try(call).fold(Future.failed, identity).transform {
        case Success(_) => Failure(new IllegalStateException("not refused"))
        case Failure(e) if expected(e) =>
          Success(s"${e.getClass.getSimpleName}: ${e.getMessage.take(170)}")
        case Failure(e) => Failure(e)
      }

    def near(
      box: BoundingBox,
      expected: BoundingBox
    ) =
      Seq(
        box.top - expected.top,
        box.bottom - expected.bottom,
        box.left - expected.left,
        box.right - expected.right
      ).forall(d => math.abs(d) <= 0.05)

    val shapeQuestions = Map[String, Question](
      "red" -> NoulQuestion("Is the shape in `photo` red?"),
      "shape" -> ChoiceQuestion(
        "What shape is in `photo`?",
        "circle" -> "A circle",
        "square" -> "A square",
        "triangle" -> "A triangle"
      )
    )

    val run = for {
      _ <- section("the models - jev-preview is Jev 1.14, jev-latest still 1.13") {
        Future
          .sequence(
            Seq(
              DecisionModelId.jev_preview,
              DecisionModelId.jev_1_14_0,
              DecisionModelId.jev_latest
            ).map(model =>
              jev
                .systemOne("x", Map("q" -> NoulQuestion("Is this a test?")), model)
                .map(model -> _.model)
            )
          )
          .map { resolved =>
            check(resolved.take(2).forall(_._2 == "jev-1.14.0"), s"resolved $resolved")
            resolved.map { case (asked, answered) => s"$asked -> $answered" }.mkString(", ")
          }
      }

      _ <- section("an image in the state - Jev 1.13 and 1.14") {
        Future
          .sequence(Seq(DecisionModelId.jev_1_13_0, DecisionModelId.jev_1_14_0).map { model =>
            jev
              .systemOne(Json.obj("photo" -> DecisionImage(redCircle)), shapeQuestions, model)
              .map(model -> _)
          })
          .map { results =>
            results.foreach { case (model, r) =>
              check(r.noul("red").noul >= 0.9, s"$model red ${r.noul("red").noul}")
              check(
                r.choice("shape").choice == "circle",
                s"$model ${r.choice("shape").choice}"
              )
            }
            results.map { case (model, r) =>
              f"$model: red ${r.noul("red").noul}%.2f, ${r.choice("shape").choice} (${r
                  .choice("shape")
                  .confidence}%.2f), ${r.usage.input_tokens.getOrElse(0)} tokens"
            }.mkString("; ")
          }
      }

      _ <- section("a reference picture in a question's instructions") {
        jev
          .systemOne(
            Json.obj("photo" -> DecisionImage(redCircle)),
            Map(
              "same_as_circle" -> NoulQuestion(
                Some(
                  Json.arr(
                    "Is the shape in `photo` the same as in this picture:",
                    DecisionImage(redCircle)
                  )
                ),
                None
              ),
              "same_as_square" -> NoulQuestion(
                Some(
                  Json.arr(
                    "Is the shape in `photo` the same as in this picture:",
                    DecisionImage(blueSquare)
                  )
                ),
                None
              )
            ),
            DecisionModelId.jev_1_14_0
          )
          .map { r =>
            check(
              r.noul("same_as_circle").noul > r.noul("same_as_square").noul,
              "no difference"
            )
            check(
              r.noul("same_as_square").noul < 0.5,
              s"square ${r.noul("same_as_square").noul}"
            )
            f"same as the circle ${r.noul("same_as_circle").noul}%.2f, as the square ${r
                .noul("same_as_square")
                .noul}%.2f, ${r.usage.input_tokens.getOrElse(0)} tokens"
          }
      }

      _ <- section("options defined by example pictures") {
        jev
          .systemOne(
            Json.obj("photo" -> DecisionImage(greenTriangle)),
            Map(
              "which" -> ChoiceQuestion(
                ListMap(
                  "red_circle" -> Some(DecisionImage(redCircle)),
                  "blue_square" -> Some(DecisionImage(blueSquare)),
                  "green_triangle" -> Some(DecisionImage(greenTriangle))
                ),
                Some(JsString("Which example looks like `photo`?"))
              )
            ),
            DecisionModelId.jev_1_14_0
          )
          .map { r =>
            check(
              r.choice("which").choice == "green_triangle",
              s"chose ${r.choice("which").choice}"
            )
            f"${r.choice("which").choice} (${r.choice("which").confidence}%.2f)"
          }
      }

      _ <- section("boxes - Jev 1.14 finds two shapes, and is unsure about an absent one") {
        jev
          .systemOne(
            Json.obj("photo" -> DecisionImage(twoShapes)),
            Map(
              "red_box" -> BoxQuestion("the red rectangle in `photo`"),
              "blue_box" -> BoxQuestion("the blue circle in `photo`"),
              "green_box" -> BoxQuestion("the green triangle in `photo`"),
              "has_green" -> NoulQuestion("Is there a green triangle in `photo`?")
            ),
            DecisionModelId.jev_1_14_0
          )
          .map { r =>
            val red = r.box("red_box")
            val blue = r.box("blue_box")
            check(near(red.box, BoundingBox(0.5, 0.8, 0.25, 0.5)), s"red $red")
            check(near(blue.box, BoundingBox(0.133, 0.367, 0.7, 0.875)), s"blue $blue")
            check(r.noul("has_green").noul < 0.2, s"has_green ${r.noul("has_green").noul}")
            def show(a: BoxAnswer) =
              f"(${a.box.top}%.2f, ${a.box.bottom}%.2f, ${a.box.left}%.2f, ${a.box.right}%.2f) ${a.confidence
                  .getOrElse(-1d)}%.2f"
            s"red ${show(red)}, blue ${show(blue)}, green ${show(r.box("green_box"))}, has_green ${r.noul("has_green").noul}"
          }
      }

      _ <- section("a box question on Jev 1.13 - refused by the API, the client saying why") {
        refused(
          jev.systemOne(
            Json.obj("photo" -> DecisionImage(twoShapes)),
            Map("red_box" -> BoxQuestion("the red rectangle in `photo`")),
            DecisionModelId.jev_1_13_0
          )
        )(e =>
          e.isInstanceOf[TypeSafeScalaApiUsageException] &&
            e.getMessage.contains("box questions need Jev 1.14")
        )
      }

      _ <- section("decide[Shape] on an image") {
        jev.decide[Shape](Json.obj("photo" -> DecisionImage(redCircle))).map { decision =>
          check(decision.value == Shape(red = true, Circle), s"decided ${decision.value}")
          f"${decision.value}, red ${decision.noul("red").noul}%.2f"
        }
      }

      _ <- section(
        "the OpenAI interface with an image - createChatCompletionWithJSON[Colour]"
      ) {
        val dataUrl =
          "data:image/png;base64," + java.util.Base64.getEncoder.encodeToString(redCircle)
        asOpenAI
          .createChatCompletionWithJSON[Colour](
            Seq(UserSeqMessage(Seq(TextContent("A photo."), ImageURLContent(dataUrl)))),
            CreateChatCompletionSettings(DecisionModelId.jev_latest)
          )
          .map { colour =>
            check(colour.red, s"answered $colour")
            colour.toString
          }
      }

      _ <- section("the JSON schema adapter - a BoundingBox field is a box question") {
        val dataUrl =
          "data:image/png;base64," + java.util.Base64.getEncoder.encodeToString(twoShapes)
        asOpenAI
          .createChatCompletionWithJSON[Detection](
            Seq(UserSeqMessage(Seq(TextContent("A photo."), ImageURLContent(dataUrl)))),
            CreateChatCompletionSettings(DecisionModelId.jev_1_14_0)
          )
          .map { detection =>
            check(detection.visible, s"not visible: $detection")
            check(
              near(detection.red, BoundingBox(0.5, 0.8, 0.25, 0.5)),
              s"box ${detection.red}"
            )
            detection.toString
          }
      }

      _ <- section("decide[Detection] - a typed box") {
        jev
          .decide[Detection](
            Json.obj("photo" -> DecisionImage(twoShapes)),
            DecisionModelId.jev_1_14_0
          )
          .map { decision =>
            check(
              near(decision.value.red, BoundingBox(0.5, 0.8, 0.25, 0.5)),
              s"${decision.value}"
            )
            f"${decision.value}, box confidence ${decision.response.box("red").confidence.getOrElse(-1d)}%.2f"
          }
      }

      _ <- section("OpenAI's Decisions interface - an input_image on Jev") {
        val dataUrl =
          "data:image/png;base64," + java.util.Base64.getEncoder.encodeToString(redCircle)
        openAIDecisions
          .createDecision(
            DecisionInput.Messages(
              Seq(
                DecisionMessage(
                  Seq(
                    DecisionContent.InputText("A photo."),
                    DecisionContent.InputImage(dataUrl)
                  )
                )
              )
            ),
            Seq(
              DecisionQuestion.Predicate("Is the shape in the photo red?", Some("red")),
              DecisionQuestion.Choice(
                "Which shape is in the photo?",
                Seq(
                  DecisionChoice("circle", "A circle"),
                  DecisionChoice("square", "A square")
                ),
                Some("shape")
              )
            ),
            CreateDecisionSettings(Some(DecisionModelId.jev_1_14_0))
          )
          .map { decision =>
            val red = decision.answer("red").collect { case p: DecisionAnswer.Predicate =>
              p.probability
            }
            val shape =
              decision.answer("shape").collect { case c: DecisionAnswer.Choice => c.choice }
            check(red.exists(_ >= 0.9), s"red $red")
            check(shape.contains(DecisionValue.Text("circle")), s"shape $shape")
            s"red ${red.getOrElse(-1d)}, shape ${shape.getOrElse("-")}, model ${decision.model}"
          }
      }

      _ <- section("OpenAI's Decisions interface - long context on Jev") {
        openAILongContext
          .createDecision(
            DecisionInput.Text(sentence * 3000), // ~57k tokens
            Seq(
              DecisionQuestion
                .Predicate("Does the text mention a billing problem?", Some("billing"))
            )
          )
          .map { decision =>
            val tokens = decision.usage.map(_.inputTokens).getOrElse(0)
            check(tokens > 50000, s"only $tokens tokens")
            s"answered over $tokens tokens"
          }
      }

      _ <- section("a 64 x 64 image - refused before sending (Jev never resizes)") {
        refused(
          jev.systemOne(
            Json.obj("photo" -> DecisionImage(canvas(64, 64)(_ => ()))),
            shapeQuestions
          )
        )(_.isInstanceOf[IllegalArgumentException])
      }

      longState = JsString(sentence * 5250) // ~100k tokens

      _ <- section("a ~100k-token state with long context") {
        longContext
          .systemOne(
            longState,
            Map("billing" -> NoulQuestion("Does the state mention a billing problem?"))
          )
          .map { r =>
            val tokens = r.usage.input_tokens.getOrElse(0)
            check(tokens > 90000, s"only $tokens tokens")
            f"billing ${r.noul("billing").noul}%.2f over $tokens tokens"
          }
      }

      _ <- section("the same state without long context - over the 33k-token default") {
        refused(
          jev.systemOne(
            longState,
            Map("billing" -> NoulQuestion("Does the state mention a billing problem?"))
          )
        )(_.isInstanceOf[TypeSafeScalaTokenCountExceededException])
      }

      _ <- section("multilingual tickets - Jev 1.13 and 1.14") {
        val tickets = Seq(
          "Mir wurde mein Abo zweimal berechnet, ich möchte mein Geld zurück." -> "billing",
          "請求が二重になっています。返金してください。" -> "billing",
          "应用程序一打开就崩溃，我无法使用。" -> "technical",
          "Chciałbym kupić 50 licencji dla mojej firmy. Jaka jest cena?" -> "sales"
        )
        val team = Map[String, Question](
          "team" -> ChoiceQuestion(
            "Which team should handle this customer message?",
            "billing" -> "Charges, invoices, refunds",
            "technical" -> "Bugs, login, crashes",
            "sales" -> "Pricing, quotes, new purchases"
          )
        )
        Future
          .sequence(for {
            model <- Seq(DecisionModelId.jev_1_13_0, DecisionModelId.jev_1_14_0)
            (text, expected) <- tickets
          } yield jev.systemOne(text, team, model).map { r =>
            (
              model,
              r.choice("team").choice == expected,
              r.choice("team").probabilityOf(expected)
            )
          })
          .map { results =>
            check(results.forall(_._2), s"misrouted: $results")
            results
              .groupBy(_._1)
              .map { case (model, rs) =>
                f"$model: ${rs.count(_._2)}/${rs.size}, min p ${rs.map(_._3).min}%.2f"
              }
              .mkString("; ")
          }
      }
    } yield ()

    Try(Await.result(run, 10.minutes))
    jev.close()
    longContext.close()
    asOpenAI.close()
    openAIDecisions.close()
    openAILongContext.close()
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
