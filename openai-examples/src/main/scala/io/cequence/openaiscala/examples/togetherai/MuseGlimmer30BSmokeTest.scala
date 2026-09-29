package io.cequence.openaiscala.examples.togetherai

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.RetryHelpers
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatChunk.FinishReason
import io.cequence.openaiscala.domain.response.ChatToolCompletionResponse
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  JsonSchemaDef
}
import io.cequence.openaiscala.examples.ChatCompletionProvider
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import play.api.libs.json.{Format, Json}

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for Meta Muse Glimmer 30B (`meta-models/Muse-Glimmer-30B` on Together AI)
 * through the OpenAI-compatible provider service (live-verified 2026-09-29):
 *
 *   - a reasoning model: its reasoning arrives as `message.reasoning` / `delta.reasoning`
 *     (typed stream `Thinking` chunks) and counts toward `max_tokens`
 *   - function tools, auto and forced, sync and typed streamed - a forced call comes back with
 *     finish_reason `stop`, which the typed stream reports as `tool_calls`; strict json_schema
 *     output; image input
 *   - Fireworks lists it (`accounts/fireworks/models/muse-glimmer-30b`) for on-demand
 *     deployments only (not serverless) - its section runs only against a deployment named in
 *     `FIREWORKS_MUSE_GLIMMER_MODEL` (the model string the deployment is served under)
 *
 * Every section prints PASS/FAIL and the run continues; a section that hits a transient error
 * (Together's dynamic rate limits answer back-to-back calls with 429s) is retried twice. The
 * exit code is 1 if any section failed or the run did not complete. Requires
 * `TOGETHERAI_API_KEY`; the Fireworks section also `FIREWORKS_API_KEY` and
 * `FIREWORKS_MUSE_GLIMMER_MODEL`.
 */
object MuseGlimmer30BSmokeTest extends RetryHelpers {

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
        properties = Seq(
          "country" -> JsonSchema.String(),
          "capital" -> JsonSchema.String()
        ),
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

  private val weatherQuestion = Seq(UserMessage("What's the weather in Paris right now?"))

  // a reasoning model - leave room for the reasoning before the answer
  private val maxTokens = Some(3000)

  // a 64x64 solid red PNG, inline
  private lazy val redPng: String = {
    val image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(new java.awt.Color(220, 20, 20))
    graphics.fillRect(0, 0, 64, 64)
    graphics.dispose()
    val out = new ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    "data:image/png;base64," + Base64.getEncoder.encodeToString(out.toByteArray)
  }

  private def functionCalls(response: ChatToolCompletionResponse): String =
    response.choices.head.message.tool_calls.collect { case (_, fc: FunctionCallSpec) =>
      s"${fc.name}(${fc.arguments})"
    }.mkString(", ") match {
      case ""    => throw new IllegalStateException("no tool call")
      case calls => calls + s" finish=${response.choices.head.finish_reason.getOrElse("-")}"
    }

  private def describe(e: Throwable): String =
    s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("(no message)").take(400)}"

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val together = ChatCompletionProvider.togetherAI
    val model = NonOpenAIModelId.meta_models_muse_glimmer_30b
    val settings = CreateChatCompletionSettings(model = model, max_tokens = maxTokens)
    val failures = new AtomicInteger(0)
    implicit val retrySettings: RetrySettings =
      RetrySettings(maxRetries = 2, delayOffset = 5.seconds)

    def section(
      name: String
    )(
      f: => Future[String]
    ): Future[Unit] = {
      val start = System.currentTimeMillis()
      Try(f.retryOnFailure(failureMessage = Some(name)))
        .fold(Future.failed, identity)
        .transform {
          case Success(msg) =>
            println(s"[PASS] $name (${System.currentTimeMillis() - start} ms): $msg")
            Success(())
          case Failure(e) =>
            failures.incrementAndGet()
            println(s"[FAIL] $name: ${describe(e)}")
            Success(())
        }
    }

    val all = for {
      _ <- section(s"$model: chat completion") {
        together
          .createChatCompletion(
            Seq(UserMessage("Which planet is the largest? Answer in 5 words.")),
            settings
          )
          .map(r => s"content='${r.contentHead}' usage=${r.usage.map(_.total_tokens)}")
      }

      _ <- section(s"$model: streamed chunks") {
        together
          .createChatCompletionStreamed(
            Seq(UserMessage("Count from 1 to 5, comma separated.")),
            settings
          )
          .runWith(Sink.seq)
          .map { chunks =>
            val text = chunks.flatMap(_.choices.headOption.flatMap(_.delta.content)).mkString
            if (text.isEmpty) throw new IllegalStateException("no streamed text")
            s"${chunks.size} chunks, text='$text'"
          }
      }

      _ <- section(s"$model: typed stream (reasoning -> Thinking)") {
        together
          .createChatCompletionStreamedTyped(
            Seq(UserMessage("Is 391 prime? One sentence.")),
            settings
          )
          .assembled
          .map { a =>
            if (a.thinking.isEmpty) throw new IllegalStateException(s"no thinking: $a")
            s"thinking=${a.thinking.length} chars text='${a.text}' finish=${a.finishReason}"
          }
      }

      _ <- section(s"$model: chat tools (auto)") {
        together
          .createChatToolCompletion(weatherQuestion, Seq(weatherTool), settings = settings)
          .map(functionCalls)
      }

      _ <- section(s"$model: forced tool choice") {
        together
          .createChatToolCompletion(
            weatherQuestion,
            Seq(weatherTool),
            responseToolChoice = Some(weatherTool.name),
            settings = settings
          )
          .map(functionCalls)
      }

      _ <- section(s"$model: typed streamed tool call") {
        together
          .createChatToolCompletionStreamed(weatherQuestion, Seq(weatherTool), None, settings)
          .assembled
          .map { a =>
            if (a.toolCalls.isEmpty) throw new IllegalStateException(s"no tool call: $a")
            a.toolCalls.map(c => s"${c.toolName}(${c.arguments})").mkString(", ") +
              s" finish=${a.finishReason}"
          }
      }

      _ <- section(s"$model: typed streamed forced tool call (finishes as tool_calls)") {
        together
          .createChatToolCompletionStreamed(
            weatherQuestion,
            Seq(weatherTool),
            Some(weatherTool.name),
            settings
          )
          .assembled
          .map { a =>
            if (a.toolCalls.isEmpty) throw new IllegalStateException(s"no tool call: $a")
            if (!a.finishReason.contains(FinishReason.tool_calls))
              throw new IllegalStateException(
                s"finish=${a.finishReason} (provider: ${a.providerFinishReason})"
              )
            s"${a.toolCalls.map(_.toolName)} finish=${a.finishReason} (provider: ${a.providerFinishReason
                .getOrElse("-")})"
          }
      }

      _ <- section(s"$model: createChatCompletionWithJSON[Capital] (json_schema)") {
        together
          .createChatCompletionWithJSON[Capital](
            Seq(UserMessage("Capital of Norway?")),
            settings.copy(
              response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
              jsonSchema = Some(capitalSchema)
            )
          )
          .map(c => s"parsed=$c")
      }

      _ <- section(s"$model: image input") {
        together
          .createChatCompletion(
            Seq(
              UserSeqMessage(
                Seq(
                  TextContent("What color is this image? One word."),
                  ImageURLContent(redPng)
                )
              )
            ),
            settings
          )
          .map { r =>
            if (!r.contentHead.toLowerCase.contains("red"))
              throw new IllegalStateException(s"unexpected answer '${r.contentHead}'")
            s"content='${r.contentHead}'"
          }
      }

      // Fireworks serves it on on-demand deployments only - test one when it is named
      _ <- section(s"fireworks ${NonOpenAIModelId.muse_glimmer_30b}: chat completion") {
        (
          sys.env.get("FIREWORKS_API_KEY").filter(_.nonEmpty),
          sys.env.get("FIREWORKS_MUSE_GLIMMER_MODEL").filter(_.nonEmpty)
        ) match {
          case (Some(_), Some(deploymentModel)) =>
            val fireworks = ChatCompletionProvider.fireworks
            fireworks
              .createChatCompletion(
                Seq(UserMessage("Say hi")),
                CreateChatCompletionSettings(deploymentModel, max_tokens = maxTokens)
              )
              .map(r => s"$deploymentModel: content='${r.contentHead}'")
              .andThen { case _ => fireworks.close() }

          case _ =>
            Future.successful(
              "skipped - not serverless on Fireworks; set FIREWORKS_API_KEY and FIREWORKS_MUSE_GLIMMER_MODEL (an on-demand deployment's model string) to test one"
            )
        }
      }
    } yield ()

    // a run that fails or times out before its last section is a failure too
    Try(Await.result(all, 15.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: ${describe(e)}")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    together.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
