package io.cequence.openaiscala.examples.minimax

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.{ChatChunk, ChatCompletionResponse}
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
 * Live walkthrough of MiniMax's chat API (`ChatProviderSettings.minimax`, `MINIMAX_API_KEY`;
 * `china` as an argument for `minimaxChina`) on MiniMax-M3.1-Flash and MiniMax-M3
 * (`models=a,b` for others).
 *
 * Per model: a chat completion whose thinking comes back as the choice's reasoning, never as
 * `<think>` tags in the content (M3 writes them into the content unless `reasoning_split` -
 * the `miniMaxM3` conversion sends it); the efforts as converted (Flash: `none` / `minimal`
 * sent as low; M3: `none` sent as `thinking.type = disabled`, so it must not reason); a tool
 * call answered in a second turn with the reasoning carried;
 * `createChatCompletionWithJSON[Capital]` / `[City]` with the schema inferred (neither model
 * enforces a strict json_schema, so the JSON helper falls back to JSON-object mode); an image;
 * the typed stream (thinking + text) and the OpenAI-shaped stream. Every section prints
 * PASS/FAIL; the exit code is 1 if any failed.
 */
object MiniMaxSmokeTest {

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
      new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    graphics.setColor(java.awt.Color.BLUE)
    graphics.fillRect(0, 0, 256, 256)
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

  private val puzzle = Seq(
    UserMessage(
      "A courier starts at 9:00 and visits 5 shops. Each visit takes 12 minutes, travel " +
        "between shops takes 7, 9, 4 and 11 minutes, and she takes a 15-minute break after " +
        "the third shop. When does she finish? Answer with just the time."
    )
  )

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    def arg(name: String) = args.find(_.startsWith(name + "=")).map(_.drop(name.length + 1))

    val provider =
      if (args.contains("china"))
        ChatProviderSettings.minimaxChina
      else
        ChatProviderSettings.minimax
    val models = arg("models")
      .map(_.split(',').map(_.trim).toSeq)
      .getOrElse(Seq(NonOpenAIModelId.minimax_m3_1_flash, NonOpenAIModelId.minimax_m3))

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

    def reasoningTokens(response: ChatCompletionResponse): Int =
      response.usage
        .flatMap(_.completion_tokens_details)
        .flatMap(_.reasoning_tokens)
        .getOrElse(0)

    def thinkingOf(response: ChatCompletionResponse): String =
      response.choices.head.reasoning.flatMap(_.text).getOrElse("")

    def walk(model: String): Future[Unit] = {
      val settings = CreateChatCompletionSettings(model, max_tokens = Some(8000))
      val isFlash =
        io.cequence.openaiscala.service.adapter.ChatCompletionSettingsConversions
          .isMiniMaxFlash(model)

      def solve(effort: Option[ReasoningEffort]) =
        service.createChatCompletion(puzzle, settings.copy(reasoning_effort = effort))

      def describe(response: ChatCompletionResponse) =
        s"'${response.contentHead.trim}', reasoning ${reasoningTokens(response)} tokens, " +
          s"thinking ${thinkingOf(response).length} chars"

      def thoughtApart(response: ChatCompletionResponse): Unit = {
        check(!response.contentHead.contains("<think>"), "<think> tags in the content")
        check(
          reasoningTokens(response) == 0 || thinkingOf(response).nonEmpty,
          s"reasoned ${reasoningTokens(response)} tokens, but no thinking came back"
        )
      }

      val efforts =
        if (isFlash)
          Seq(ReasoningEffort.none, ReasoningEffort.minimal, ReasoningEffort.low)
        else
          Seq(ReasoningEffort.low, ReasoningEffort.max)

      for {
        _ <- section(s"$model: chat completion - the thinking apart from the answer") {
          solve(None).map { r =>
            thoughtApart(r)
            check(r.contentHead.contains("10:46"), s"answered '${r.contentHead}'")
            check(reasoningTokens(r) > 0, "did not reason")
            describe(r)
          }
        }

        _ <- efforts.foldLeft(Future.unit) {
          (
            acc,
            effort
          ) =>
            acc.flatMap(_ =>
              section(s"$model: reasoning_effort=$effort - converted as it needs") {
                solve(Some(effort)).map { r =>
                  thoughtApart(r)
                  check(r.contentHead.nonEmpty, "no answer")
                  describe(r)
                }
              }
            )
        }

        _ <-
          if (isFlash)
            Future.unit
          else
            section(s"$model: reasoning_effort=none - sent as thinking disabled") {
              solve(Some(ReasoningEffort.none)).map { r =>
                thoughtApart(r)
                check(reasoningTokens(r) == 0, s"reasoned ${reasoningTokens(r)} tokens")
                check(thinkingOf(r).isEmpty, "thinking came back")
                describe(r)
              }
            }

        _ <- section(s"$model: a tool call, answered in a second turn with the reasoning") {
          val ask = Seq(
            UserMessage(
              "Anna lives in Oslo, Ben in Rome, Cleo in Paris. The party is hosted by whoever " +
                "lives furthest south. Work out the host city, then call the tool for its weather."
            )
          )
          service.createChatToolCompletion(ask, Seq(weather), settings = settings).flatMap {
            first =>
              val message = first.choices.head.message
              val calls = message.tool_calls.collect { case (id, call: FunctionCallSpec) =>
                id -> call
              }
              check(calls.nonEmpty, s"no tool call: '${message.content.getOrElse("")}'")
              check(
                !message.content.exists(_.contains("<think>")),
                "<think> tags in the content"
              )
              val results = calls.map { case (id, call) =>
                ToolMessage(Some("""{"temperature_c": 18, "sky": "sunny"}"""), id, call.name)
              }
              service
                .createChatToolCompletion(
                  ask ++ first.historyMessages ++ results,
                  Seq(weather),
                  settings = settings
                )
                .map { second =>
                  val text = second.choices.head.message.content.getOrElse("")
                  check(text.nonEmpty, "no answer after the tool result")
                  val carried = first.reasoningMessage.flatMap(_.text).fold(0)(_.length)
                  s"${calls.map(c => c._2.name + c._2.arguments).mkString(", ")}, " +
                    s"$carried chars of thinking carried -> '${text.take(80)}'"
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

        _ <- section(s"$model: createChatCompletionWithJSON[City], an Option field") {
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
              thoughtApart(r)
              check(r.contentHead.toLowerCase.contains("blue"), s"answered '${r.contentHead}'")
              s"'${r.contentHead.trim}'"
            }
        }

        _ <- section(s"$model: typed stream - thinking, then the text") {
          service.createChatCompletionStreamedTyped(puzzle, settings).runToSeq.map { chunks =>
            val text = chunks.collect { case ChatChunk.Text(t) => t }.mkString
            val thinking = chunks.collect { case ChatChunk.Thinking(t) => t }.mkString
            val usage = chunks.collect { case ChatChunk.Usage(u) => u }.lastOption
            check(text.contains("10:46"), s"text='$text'")
            check(!text.contains("<think>"), "<think> tags in the text")
            check(thinking.nonEmpty, "no thinking")
            s"text='${text.trim}', thinking ${thinking.length} chars, ${chunks.size} chunks, " +
              s"usage ${usage.map(u => (u.prompt_tokens, u.completion_tokens))}"
          }
        }

        _ <- section(s"$model: OpenAI-shaped stream") {
          service.createChatCompletionStreamed(puzzle, settings).runToSeq.map { chunks =>
            val deltas = chunks.flatMap(_.choices.headOption.map(_.delta))
            val text = deltas.flatMap(_.content).mkString
            val thinking = deltas.flatMap(_.reasoningText).mkString
            check(text.contains("10:46"), s"text='$text'")
            check(!text.contains("<think>"), "<think> tags in the text")
            s"text='${text.trim}', reasoning deltas ${thinking.length} chars"
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
    Try(Await.result(run, 20.minutes))
    service.close()
    println(
      if (failures.get == 0)
        "ALL PASSED"
      else
        s"${failures.get} FAILED"
    )
    System.exit(
      if (failures.get == 0)
        0
      else
        1
    )
  }
}
