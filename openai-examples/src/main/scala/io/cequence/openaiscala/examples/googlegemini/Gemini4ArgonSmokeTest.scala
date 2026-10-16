package io.cequence.openaiscala.examples.googlegemini

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.{AssembledChatCompletion, ChatChunk}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  ReasoningEffort
}
import io.cequence.openaiscala.gemini.domain.Tool
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.InferredJsonSchema
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import play.api.libs.json.{Format, Json}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of Gemini 4 Argon (`gemini-4-argon`, on the Gemini API since 2026-10-13)
 * through the OpenAI adapter (`GeminiServiceFactory.asOpenAI`, `GOOGLE_API_KEY`): a chat
 * completion, the reasoning efforts (thinking levels LOW / MEDIUM / HIGH - `none` and
 * `minimal` go as LOW, since MINIMAL is a 400), `createChatCompletionWithJSON` with the schema
 * inferred from a case class, a tool call answered in a second turn, an image, the typed
 * stream with its thinking, Google Search grounding and a `max_tokens` over the listed 32,768
 * output tokens (`group=basics`); then the typed chunk stream (`group=stream`): its chunk
 * sequence and assembly, a tool loop streamed in both turns (the second carries the call's
 * thought signature back, which Gemini 3+ requires), two parallel calls, a forced tool, Google
 * Search citations, code execution, json_schema and the OpenAI-shaped stream. `model=<id>`
 * runs another Gemini model. Every section prints PASS/FAIL; the exit code is 1 if any failed
 * (a few cents per run).
 */
object Gemini4ArgonSmokeTest {

  case class Capital(
    @JsonSchemaDescription("The capital city") capital: String,
    @JsonSchemaDescription("The country") country: String
  )
  private implicit val capitalFormat: Format[Capital] = Json.format[Capital]

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
    System.setProperty("java.awt.headless", "true")
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val scheduler: akka.actor.Scheduler = system.scheduler
    implicit val ec: ExecutionContext = system.dispatcher

    val model = args
      .find(_.startsWith("model="))
      .map(_.drop("model=".length))
      .getOrElse(NonOpenAIModelId.gemini_4_argon)

    val group = args.find(_.startsWith("group=")).map(_.drop("group=".length))

    val service = GeminiServiceFactory.asOpenAI()
    val settings = CreateChatCompletionSettings(model, max_tokens = Some(8000))
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

    def answer(settings: CreateChatCompletionSettings) =
      service.createChatCompletion(arithmetic, settings).map { r =>
        check(r.contentHead.contains("391"), s"answered '${r.contentHead}'")
        s"'${r.contentHead.trim}', usage ${r.usage.map(u => (u.prompt_tokens, u.completion_tokens))}"
      }

    def basics = for {
      _ <- section(s"$model: chat completion")(answer(settings))

      _ <- Future.sequence(
        Seq(
          ReasoningEffort.none,
          ReasoningEffort.minimal,
          ReasoningEffort.medium,
          ReasoningEffort.max
        ).map { effort =>
          section(s"$model: reasoning_effort=$effort")(
            answer(settings.copy(reasoning_effort = Some(effort)))
          )
        }
      )

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

      _ <- section(s"$model: typed stream with thinking (effort high)") {
        service
          .createChatCompletionStreamedTyped(
            Seq(
              UserMessage(
                "A bat and a ball cost $1.10 in total. The bat costs $1.00 more than the ball. How much does the ball cost? Answer with the amount only."
              )
            ),
            settings.copy(reasoning_effort = Some(ReasoningEffort.high))
          )
          .runWith(Sink.seq)
          .map { chunks =>
            val text = chunks.collect { case ChatChunk.Text(t) => t }.mkString
            val thinking = chunks.collect { case ChatChunk.Thinking(t) => t }.mkString
            check(text.contains("0.05") || text.contains("5 cents"), s"text='$text'")
            s"text='${text.trim}', thinking ${thinking.length} chars, ${chunks.size} chunks"
          }
      }

      _ <- section(s"$model: Google Search grounding") {
        service
          .createChatCompletion(
            Seq(
              UserMessage("Which Gemini model did Google release most recently? One sentence.")
            ),
            settings.setGeminiTools(Seq(Tool.GoogleSearch))
          )
          .map { r =>
            check(r.contentHead.nonEmpty, "no answer")
            s"'${r.contentHead.trim.take(120)}'"
          }
      }

      _ <- section(s"$model: max_tokens over the listed 32,768 output tokens") {
        answer(settings.copy(max_tokens = Some(100000)))
      }
    } yield ()

    // every kind of chunk the typed streams brought, an unmapped part as `Other(kind)`
    val seenChunks = scala.collection.mutable.LinkedHashSet.empty[String]

    // the typed chunk stream
    def streamed(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool] = Nil,
      toolChoice: Option[String] = None,
      settings: CreateChatCompletionSettings = settings
    ): Future[(Seq[ChatChunk], AssembledChatCompletion)] =
      service
        .createChatToolCompletionStreamed(messages, tools, toolChoice, settings)
        .runWith(Sink.seq)
        .map { chunks =>
          seenChunks.synchronized(seenChunks ++= chunks.map {
            case ChatChunk.Other(kind, _) => s"Other($kind)"
            case chunk                    => chunk.getClass.getSimpleName
          })
          chunks -> chunks.foldLeft(AssembledChatCompletion())(_ add _)
        }

    def kinds(chunks: Seq[ChatChunk]) =
      chunks.map(_.getClass.getSimpleName).distinct.mkString(", ")

    // the tool results of a streamed turn's client calls, every city 7 degrees
    def answered(assembled: AssembledChatCompletion): Seq[BaseMessage] =
      assembled.toAssistantToolMessage +: assembled.clientToolCalls.map { call =>
        ToolMessage(
          Some("""{"temperature_c": 7, "sky": "light rain"}"""),
          call.callId,
          call.toolName
        )
      }

    def streamGroup = for {
      _ <- section(s"$model: typed stream - Start, Text, Finish(stop), Usage, assembled") {
        streamed(arithmetic).map { case (chunks, assembled) =>
          check(chunks.headOption.exists(_.isInstanceOf[ChatChunk.Start]), kinds(chunks))
          check(assembled.text.contains("391"), s"text='${assembled.text}'")
          check(assembled.finishReason.contains(ChatChunk.FinishReason.stop), s"$assembled")
          check(assembled.usage.isDefined, "no usage")
          s"model ${assembled.model.getOrElse("-")}, text='${assembled.text.trim}', usage ${assembled.usage
              .map(u => (u.prompt_tokens, u.completion_tokens))}, chunks: ${kinds(chunks)}"
        }
      }

      _ <- section(s"$model: typed tool stream - a call, then the second turn streamed too") {
        val ask = Seq(UserMessage("What's the weather in Oslo right now?"))
        streamed(ask, Seq(weather)).flatMap { case (chunks, first) =>
          check(first.clientToolCalls.nonEmpty, s"no tool call: ${kinds(chunks)}")
          check(
            first.finishReason.contains(ChatChunk.FinishReason.tool_calls),
            s"finish ${first.finishReason}"
          )
          streamed(ask ++ answered(first), Seq(weather)).map { case (_, second) =>
            check(second.text.nonEmpty, "no answer after the tool result")
            check(second.finishReason.contains(ChatChunk.FinishReason.stop), s"$second")
            s"${first.clientToolCalls.map(c => s"${c.toolName}${c.arguments}").mkString(", ")} " +
              s"(signed: ${first.toolCallSignatures.size}) -> '${second.text.trim.take(80)}'"
          }
        }
      }

      _ <- section(s"$model: typed tool stream - two parallel calls, answered in one turn") {
        val ask =
          Seq(UserMessage("What's the weather in Oslo and in Paris right now? Look both up."))
        streamed(ask, Seq(weather)).flatMap { case (_, first) =>
          check(first.clientToolCalls.size == 2, s"calls ${first.clientToolCalls}")
          streamed(ask ++ answered(first), Seq(weather)).map { case (_, second) =>
            check(
              second.text.contains("Oslo") && second.text.contains("Paris"),
              s"text='${second.text}'"
            )
            s"${first.clientToolCalls.map(_.arguments).mkString(", ")} (signed: ${first.toolCallSignatures.size}) -> '${second.text.trim
                .take(80)}'"
          }
        }
      }

      _ <- section(s"$model: typed tool stream - a forced tool") {
        streamed(Seq(UserMessage("Say hi.")), Seq(weather), Some("get_weather")).map {
          case (_, assembled) =>
            check(
              assembled.clientToolCalls.map(_.toolName) == Seq("get_weather"),
              s"$assembled"
            )
            s"${assembled.clientToolCalls.map(c => s"${c.toolName}${c.arguments}").mkString}, finish ${assembled.finishReason
                .getOrElse("-")}"
        }
      }

      _ <- section(s"$model: typed stream - Google Search citations") {
        streamed(
          Seq(
            UserMessage("Which Gemini model did Google release most recently? One sentence.")
          ),
          settings = settings.setGeminiTools(Seq(Tool.GoogleSearch))
        ).map { case (chunks, assembled) =>
          check(assembled.text.nonEmpty, "no answer")
          check(assembled.citations.nonEmpty, s"no citations: ${kinds(chunks)}")
          s"${assembled.citations.size} citations (${assembled.citations.flatMap(_.title).distinct.take(3).mkString(", ")}), " +
            s"web searches ${assembled.webSearches.flatMap(_.queries).mkString("; ")}, '${assembled.text.trim
                .take(80)}'"
        }
      }

      _ <- section(s"$model: typed stream - code execution") {
        streamed(
          Seq(UserMessage("Compute the 30th Fibonacci number (F0 = 0) by running code.")),
          settings = settings.setGeminiTools(Seq(Tool.CodeExecution))
        ).map { case (chunks, assembled) =>
          check(assembled.codeExecutions.nonEmpty, s"no code execution: ${kinds(chunks)}")
          check(assembled.codeExecutionResults.nonEmpty, "no code execution result")
          check(
            assembled.text.replace(",", "").contains("832040"),
            s"text='${assembled.text}'"
          )
          s"${assembled.codeExecutions.size} run(s), output '${assembled.codeExecutionResults.flatMap(_.output).mkString.trim.take(40)}', '${assembled.text.trim
              .take(60)}'"
        }
      }

      _ <- section(s"$model: typed stream - json_schema") {
        streamed(
          Seq(UserMessage("The capital of Norway?")),
          settings = settings.copy(
            response_format_type = Some(ChatCompletionResponseFormatType.json_schema),
            jsonSchema = InferredJsonSchema[Capital].jsonSchemaDef
          )
        ).map { case (_, assembled) =>
          val capital = Json.parse(assembled.text).as[Capital]
          check(capital.capital.contains("Oslo"), s"parsed $capital")
          s"parsed=$capital"
        }
      }

      _ <- section(s"$model: OpenAI-shaped stream - createChatCompletionStreamed") {
        service.createChatCompletionStreamed(arithmetic, settings).runWith(Sink.seq).map {
          chunks =>
            val text = chunks.flatMap(_.choices.flatMap(_.delta.content)).mkString
            val finish = chunks.flatMap(_.choices.flatMap(_.finish_reason)).lastOption
            check(text.contains("391"), s"text='$text'")
            s"text='${text.trim}', ${chunks.size} chunks, finish ${finish.getOrElse("-")}"
        }
      }
    } yield ()

    val run = (group match {
      case Some("basics") => basics
      case Some("stream") => streamGroup
      case _              => basics.flatMap(_ => streamGroup)
    }).map { _ =>
      if (seenChunks.nonEmpty)
        println(s"[INFO] typed chunk kinds seen: ${seenChunks.mkString(", ")}")
    }

    Try(Await.result(run, 15.minutes))
    service.close()
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
