package io.cequence.openaiscala.examples.meta

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory
}
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of Meta's Muse Wonder and Muse Spark - on the Meta Model API
 * (`target=meta`, `MODEL_API_KEY`, ids `muse-wonder-1.0` / `muse-spark-1.3-contributor`) or on
 * OpenRouter (`target=openrouter`, the default, `OPENROUTER_API_KEY`, ids `meta/muse-wonder` /
 * `meta/muse-spark-1.3-contributor`; OpenRouter gates Meta's models behind an 18+ age
 * confirmation in the account's settings - a 403 until it is done) - and of the open-weight
 * Muse Spark Open (2026-10-15) on Together (`target=together`, `TOGETHERAI_API_KEY`),
 * Fireworks (`target=fireworks`, `FIREWORKS_API_KEY`) and Vercel's AI Gateway
 * (`target=vercel`, `AI_GATEWAY_API_KEY`). `models=a,b` runs other ids, e.g.
 * `models=tencent/hy4-preview` on OpenRouter.
 *
 * Per model: a chat completion, the always-on reasoning (Muse models only: `none`, `minimal`
 * and `max`, each converted as the model and its host need - see
 * `ChatCompletionSettingsConversions.muse` / `museSparkOpen`), a tool call answered in a
 * second turn, `createChatCompletionWithJSON[Capital]` with the schema inferred from the case
 * class (strict) and `[City]` with an `Option` field (non-strict), an image, and the typed
 * stream (Muse Spark Open's reasoning arrives as thinking). Every section prints PASS/FAIL;
 * the exit code is 1 if any failed.
 */
object MuseSmokeTest {

  case class Capital(
    @JsonSchemaDescription("The capital city") capital: String,
    @JsonSchemaDescription("The country") country: String
  )
  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

  // an optional field makes the inferred schema non-strict
  case class City(
    city: String,
    @JsonSchemaDescription("A well-known nickname, if the city has one") nickname: Option[
      String
    ]
  )
  private implicit val cityFormat: Format[City] = Json.format[City]

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

  private val weather = FunctionTool(
    name = "get_weather",
    description = Some("The current weather in a city"),
    parameters = JsonSchema.Object(
      properties = Seq("city" -> JsonSchema.String()),
      required = Seq("city")
    )
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    def arg(name: String) = args.find(_.startsWith(name + "=")).map(_.drop(name.length + 1))

    val target = arg("target").getOrElse("openrouter")
    val (provider, defaultModels) = target match {
      case "meta" =>
        ChatProviderSettings.meta -> Seq(
          NonOpenAIModelId.muse_wonder_1_0,
          NonOpenAIModelId.muse_spark_1_3_contributor
        )
      case "openrouter" =>
        ChatProviderSettings.openRouter -> Seq(
          NonOpenAIModelId.openrouter_muse_wonder,
          NonOpenAIModelId.openrouter_muse_spark_1_3_contributor
        )
      case "together" =>
        ChatProviderSettings.togetherAI -> Seq(
          NonOpenAIModelId.meta_models_muse_spark_open_816b_a42b
        )
      case "fireworks" =>
        ChatProviderSettings.fireworks -> Seq(
          "accounts/fireworks/models/" + NonOpenAIModelId.muse_spark_open
        )
      case "vercel" =>
        ChatProviderSettings.vercel -> Seq(NonOpenAIModelId.openrouter_muse_spark_open)
      case other =>
        sys.error(s"target=$other - use meta, openrouter, together, fireworks or vercel")
    }
    val models = arg("models").map(_.split(',').map(_.trim).toSeq).getOrElse(defaultModels)

    val service = OpenAIChatCompletionServiceFactory.withStreaming(provider)
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
          println(s"[FAIL] $name: ${e.getClass.getSimpleName}: ${e.getMessage.take(300)}")
          Success(())
      }
    }

    def check(
      condition: Boolean,
      message: => String
    ): Unit = if (!condition) sys.error(message)

    val arithmetic = Seq(UserMessage("What is 17*23? Answer with the number only."))

    def walk(model: String): Future[Unit] = {
      val settings = CreateChatCompletionSettings(model, max_tokens = Some(4000))
      val conversions =
        io.cequence.openaiscala.service.adapter.ChatCompletionSettingsConversions
      val isSparkOpen = conversions.isMuseSparkOpen(model)
      val isMuse = conversions.isMuseReasoning(model) || isSparkOpen

      def answer(effort: Option[ReasoningEffort]) =
        service
          .createChatCompletion(arithmetic, settings.copy(reasoning_effort = effort))
          .map { r =>
            check(r.contentHead.contains("391"), s"answered '${r.contentHead}'")
            s"'${r.contentHead.trim}', usage ${r.usage.map(u => (u.prompt_tokens, u.completion_tokens))}"
          }

      for {
        _ <- section(s"$model: chat completion")(answer(None))

        _ <-
          if (isMuse)
            Seq(ReasoningEffort.none, ReasoningEffort.minimal, ReasoningEffort.max).foldLeft(
              Future.unit
            ) {
              (
                acc,
                effort
              ) =>
                acc.flatMap(_ =>
                  section(s"$model: reasoning_effort=$effort - converted as it needs")(
                    answer(Some(effort))
                  )
                )
            }
          else Future.unit

        _ <- section(s"$model: a tool call, answered in a second turn") {
          val ask = Seq(UserMessage("What's the weather in Oslo right now?"))
          service.createChatToolCompletion(ask, Seq(weather), settings = settings).flatMap {
            first =>
              val message = first.choices.head.message
              val calls = message.tool_calls.collect { case (id, call: FunctionCallSpec) =>
                id -> call
              }
              check(calls.nonEmpty, s"no tool call: '${message.content.getOrElse("")}'")
              val results = calls.map { case (id, call) =>
                ToolMessage(
                  Some("""{"temperature_c": 7, "sky": "light rain"}"""),
                  id,
                  call.name
                )
              }
              service
                .createChatToolCompletion(
                  ask ++ (message +: results),
                  Seq(weather),
                  settings = settings
                )
                .map { second =>
                  val text = second.choices.head.message.content.getOrElse("")
                  check(text.nonEmpty, "no answer after the tool result")
                  s"${calls.map(_._2.name).mkString(", ")} -> '${text.take(80)}'"
                }
          }
        }

        _ <- section(s"$model: createChatCompletionWithJSON[Capital], the schema inferred") {
          service
            .createChatCompletionWithJSON[Capital](
              Seq(UserMessage("The capital of Norway?")),
              settings
            )
            .map { capital =>
              check(capital.capital.contains("Oslo"), s"parsed $capital")
              s"parsed=$capital"
            }
        }

        _ <- section(
          s"$model: createChatCompletionWithJSON[City], an Option field - non-strict"
        ) {
          service
            .createChatCompletionWithJSON[City](
              Seq(UserMessage("Tell me about the capital of Norway.")),
              settings
            )
            .map { city =>
              check(city.city.contains("Oslo"), s"parsed $city")
              s"parsed=$city"
            }
        }

        _ <- section(s"$model: an image") {
          service
            .createChatCompletion(
              Seq(
                UserSeqMessage(
                  Seq(
                    TextContent("What color is this square? Answer with one word."),
                    ImageURLContent(bluePngDataUrl)
                  )
                )
              ),
              settings
            )
            .map { r =>
              check(r.contentHead.toLowerCase.contains("blue"), s"answered '${r.contentHead}'")
              s"'${r.contentHead.trim}'"
            }
        }

        _ <- section(s"$model: typed stream") {
          service
            .createChatCompletionStreamedTyped(arithmetic, settings)
            .runWith(Sink.seq)
            .map { chunks =>
              val text = chunks.collect { case ChatChunk.Text(t) => t }.mkString
              val thinking = chunks.collect { case ChatChunk.Thinking(t) => t }.mkString
              check(text.contains("391"), s"text='$text'")
              // the open weights return their reasoning - the Meta Model API keeps it private
              check(!isSparkOpen || thinking.nonEmpty, "no thinking")
              s"text='${text.trim}', thinking ${thinking.length} chars, ${chunks.size} chunks"
            }
        }
      } yield ()
    }

    val run = models.foldLeft(Future.unit)(
      (
        acc,
        model
      ) => acc.flatMap(_ => walk(model))
    )
    Try(Await.result(run, 15.minutes))
    service.close()
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
