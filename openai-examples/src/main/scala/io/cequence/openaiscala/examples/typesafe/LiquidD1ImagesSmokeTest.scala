package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{
  ImageURLContent,
  JsonSchema,
  TextContent,
  UserSeqMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain.{
  ChoiceQuestion,
  DecisionImage,
  NoulQuestion,
  TypeSafeModelId
}
import io.cequence.openaiscala.typesafe.service.{
  TypeSafeScalaInvalidRequestException,
  TypeSafeServiceFactory
}
import play.api.libs.json.{JsValue, Json}

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Live walkthrough of Liquid AI's paid `d1`, which reads images (`d1:free` does not) - the
 * provider sends a state's image parts in a top-level `images` array, an `[image n]` marker
 * left where each stood:
 *
 *   1. the models Liquid lists, with what each reads
 *   1. a photo nested in a JSON state - which Liquid itself would read as text
 *   1. two images told apart by their order
 *   1. image content through the OpenAI interface (`liquidAsOpenAI` + `json_schema`)
 *   1. an image for `d1:free` - refused (422, an invalid request)
 *
 * Live 2026-10-07: all answered right in ~330-770 ms; a 64 x 64 image costs 6 input tokens per
 * question ($0.04 / 1M input tokens). Requires `LIQUID_API_KEY` (a key with access to `d1`).
 */
object LiquidD1ImagesSmokeTest {

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

  private def timed[T](future: => Future[T]): (T, Long) = {
    val start = System.nanoTime()
    val result = Await.result(future, 1.minute)
    (result, (System.nanoTime() - start) / 1000000)
  }

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val system = ActorSystem("liquid-d1-images-smoke-test")
    implicit val scheduler: Scheduler = system.scheduler

    val d1 = TypeSafeServiceFactory.liquid(defaultModel = TypeSafeModelId.liquid_d1)
    val d1AsOpenAI =
      TypeSafeServiceFactory.liquidAsOpenAI(defaultModel = TypeSafeModelId.liquid_d1)

    val (red, blue) = (png(Color.RED), png(Color.BLUE))
    val colour = (which: String) =>
      ChoiceQuestion.ofLabels(s"Which colour is the $which image?", "red", "green", "blue")

    var failures = Seq.empty[String]
    def check(
      label: String,
      ok: Boolean
    ): Unit = if (!ok) failures :+= label

    try {
      // 1. the models
      val (models, _) = timed(d1.listModels)
      models.foreach(m =>
        println(s"[models] ${m.name} reads ${m.input_modalities.getOrElse(Nil)}")
      )
      check(
        "models: d1 reads images",
        models.exists(m => m.name == "d1" && m.input_modalities.exists(_.contains("image")))
      )

      // 2. a photo nested in a JSON state
      val (nested, nestedMs) = timed(
        d1.systemOne(
          Json.obj("note" -> "A photo from a customer.", "photo" -> DecisionImage(blue)),
          Map("colour" -> colour("only"))
        )
      )
      println(s"[nested] $nestedMs ms: ${nested.answers} ${nested.usage}")
      check("nested: blue", nested.choice("colour").choice == "blue")
      check("nested: confident", nested.choice("colour").confidence > 0.9)

      // 3. two images by their order
      val (two, twoMs) = timed(
        d1.systemOne(
          Json.arr("Two photos.", DecisionImage(red), DecisionImage(blue)),
          Map("first" -> colour("first"), "second" -> colour("second"))
        )
      )
      println(s"[two] $twoMs ms: ${two.answers}")
      check("two: first red", two.choice("first").choice == "red")
      check("two: second blue", two.choice("second").choice == "blue")

      // 4. image content through the OpenAI interface
      val schema = JsonSchemaDef(
        "photo",
        strict = true,
        JsonSchema.Object(
          Seq(
            "colour" -> JsonSchema.String(
              Some("Which colour dominates the photo?"),
              `enum` = Seq("red", "green", "blue")
            ),
            "is_blank" -> JsonSchema.Boolean(Some("Is the photo a single plain colour?"))
          ),
          required = Seq("colour", "is_blank")
        )
      )
      val (json, jsonMs) = timed(
        d1AsOpenAI.createChatCompletionWithJSON[JsValue](
          Seq(
            UserSeqMessage(
              Seq(
                TextContent("A photo from a customer."),
                ImageURLContent(
                  "data:image/png;base64," + Base64.getEncoder.encodeToString(red)
                )
              )
            )
          ),
          CreateChatCompletionSettings(TypeSafeModelId.liquid_d1, jsonSchema = Some(schema))
        )
      )
      println(s"[OpenAI interface] $jsonMs ms: $json")
      check("OpenAI interface: red", (json \ "colour").asOpt[String].contains("red"))

      // 5. d1:free reads no images
      val refused = Await.result(
        d1.systemOne(
          Json.arr("A photo.", DecisionImage(blue)),
          Map("photo" -> NoulQuestion("Is there a photo?")),
          TypeSafeModelId.liquid_d1_free
        ).failed,
        1.minute
      )
      println(s"[d1:free] ${refused.getMessage}")
      check("d1:free: refused", refused.isInstanceOf[TypeSafeScalaInvalidRequestException])
    } catch {
      case NonFatal(e) =>
        failures :+= s"error: $e"
        e.printStackTrace()
    } finally {
      d1.close()
      d1AsOpenAI.close()
      system.terminate()
    }

    println(if (failures.isEmpty) "ALL PASSED" else s"FAILED: ${failures.mkString("; ")}")
    System.exit(if (failures.isEmpty) 0 else 1)
  }
}
