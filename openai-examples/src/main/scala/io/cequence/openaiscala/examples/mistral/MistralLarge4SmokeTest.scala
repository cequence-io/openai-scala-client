package io.cequence.openaiscala.examples.mistral

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef,
  ReasoningEffort
}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory
}
import play.api.libs.json.{Format, Json}

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
 * Live smoke test for Mistral Large 4 (`mistral-large-4`, public preview since 2026-10-06)
 * through the OpenAI-compatible chat completions API - covering what the client adapts for it:
 *
 *   - it reasons by default and then answers `content` as a list of thinking / text chunks,
 *     read as the text (sync) and as `Thinking` + `Text` (typed stream)
 *   - `reasoning_effort` only `none` / `high`: the others are mapped (`low` -> `high`,
 *     `minimal` -> `none`)
 *   - tools, json_schema structured output and images
 *
 * Every section prints PASS/FAIL and the run continues; the exit code is 1 if any failed.
 * Requires `MISTRAL_API_KEY`.
 */
object MistralLarge4SmokeTest {

  private case class Capital(
    country: String,
    capital: String
  )

  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  private val capitalSchema = JsonSchemaDef(
    name = "capital",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq("country" -> JsonSchema.String(), "capital" -> JsonSchema.String()),
        required = Seq("country", "capital")
      )
    )
  )

  private val weatherTool = FunctionTool(
    name = "get_weather",
    description = Some("Get the current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String(description = Some("City name"))),
      required = Seq("city")
    )
  )

  // a 64 x 64 blue PNG (a single pixel reads as black)
  private val blueSquare = {
    val image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(Color.BLUE)
    graphics.fillRect(0, 0, 64, 64)
    graphics.dispose()
    val out = new ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    "data:image/png;base64," + Base64.getEncoder.encodeToString(out.toByteArray)
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val mistral =
      OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.mistral)
    val model = NonOpenAIModelId.mistral_large_4
    val settings = CreateChatCompletionSettings(model = model, max_tokens = Some(2000))
    val arithmetic = Seq(UserMessage("What is 17*23? Answer with the number only."))
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

    def answer(
      effort: Option[ReasoningEffort]
    ) =
      mistral.createChatCompletion(arithmetic, settings.copy(reasoning_effort = effort)).map {
        r =>
          if (!r.contentHead.contains("391")) sys.error(s"wrong answer: '${r.contentHead}'")
          s"'${r.contentHead}', ${r.usage.map(_.completion_tokens)} output tokens"
      }

    val all = for {
      _ <- section(s"$model: reasoning by default (chunked content)")(answer(None))
      _ <- section(s"$model: reasoning_effort=none (plain content)")(
        answer(Some(ReasoningEffort.none))
      )
      _ <- section(s"$model: reasoning_effort=low -> high")(answer(Some(ReasoningEffort.low)))

      _ <- section(s"$model: typed stream (Thinking + Text)") {
        mistral.createChatCompletionStreamedTyped(arithmetic, settings).runWith(Sink.seq).map {
          chunks =>
            val thinking = chunks.collect { case ChatChunk.Thinking(t) => t }.mkString
            val text = chunks.collect { case ChatChunk.Text(t) => t }.mkString
            if (thinking.isEmpty || !text.contains("391"))
              sys.error(s"thinking=${thinking.length} chars, text='$text'")
            s"thinking=${thinking.length} chars, text='$text'"
        }
      }

      _ <- section(s"$model: OpenAI-shaped stream") {
        mistral.createChatCompletionStreamed(arithmetic, settings).runWith(Sink.seq).map {
          chunks =>
            val text = chunks.flatMap(_.choices.headOption.flatMap(_.delta.content)).mkString
            if (!text.contains("391")) sys.error(s"text='$text'")
            s"${chunks.size} chunks, text='$text'"
        }
      }

      _ <- section(s"$model: tool call") {
        mistral
          .createChatToolCompletion(
            Seq(UserMessage("What's the weather in Paris right now?")),
            Seq(weatherTool),
            settings = settings
          )
          .map(
            _.choices.head.message.tool_calls.collect { case (_, call: FunctionCallSpec) =>
              s"${call.name}(${call.arguments})"
            } match {
              case Nil   => sys.error("no tool call")
              case calls => calls.mkString(", ")
            }
          )
      }

      _ <- section(s"$model: createChatCompletionWithJSON[Capital] (json_schema)") {
        mistral
          .createChatCompletionWithJSON[Capital](
            Seq(UserMessage("Capital of Norway?")),
            settings.copy(
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(c => s"parsed=$c")
      }

      _ <- section(s"$model: an image (reasoning off)") {
        mistral
          .createChatCompletion(
            Seq(
              UserSeqMessage(
                Seq(
                  TextContent("Which colour is this image? One word."),
                  ImageURLContent(blueSquare)
                )
              )
            ),
            settings.copy(reasoning_effort = Some(ReasoningEffort.none))
          )
          .map { r =>
            if (!r.contentHead.toLowerCase.contains("blue")) sys.error(s"'${r.contentHead}'")
            s"'${r.contentHead}'"
          }
      }
    } yield ()

    Try(Await.result(all, 10.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: $e")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    mistral.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
