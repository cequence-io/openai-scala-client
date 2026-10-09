package io.cequence.openaiscala.examples.mistral

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits._
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory
}
import play.api.libs.json.Json

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live smoke test for Mistral's citations (docs.mistral.ai/studio/conversations/citations):
 * the model grounds its answer on documents a tool returned and answers `content` as a LIST of
 * chunks - text and `reference` chunks (`{"type": "reference", "reference_ids": [...]}`). A
 * stream delivers a reference as a delta of its own (Medium 3.5 / Magistral, live 2026-10-09),
 * which 1.4.0 and the 2026-10-08 reader failed with `error.expected.jsstring`, killing the
 * whole stream; now:
 *
 *   - sync: the text chunks are the content (the references have no field on
 *     `AssistantToolMessage` and are dropped)
 *   - typed stream: `Text` for the text, `ChatChunk.Other("content.reference", chunk)` for
 *     each reference - nothing fails, nothing is dropped
 *
 * The documented flow: a `get_information` tool call already answered with references in the
 * history, so the model answers right away. Every section prints PASS/FAIL; the exit code is 1
 * if any failed. Requires `MISTRAL_API_KEY`.
 */
object MistralCitationsSmokeTest {

  private val tool = FunctionTool(
    name = "get_information",
    description = Some("Get information from external source."),
    parameters = JsonSchema.Object(properties = Seq.empty, required = Seq.empty)
  )

  private val references = Json.stringify(
    Json.obj(
      "0" -> Json.obj(
        "url" -> "https://en.wikipedia.org/wiki/2024_Nobel_Peace_Prize",
        "title" -> "2024 Nobel Peace Prize",
        "snippets" -> Json.arr(
          Json.arr(
            "The 2024 Nobel Peace Prize was awarded to Nihon Hidankyo for their activism " +
              "against nuclear weapons."
          )
        ),
        "source" -> "wikipedia"
      ),
      "1" -> Json.obj(
        "url" -> "https://en.wikipedia.org/wiki/Climate_Change",
        "title" -> "Climate Change",
        "snippets" -> Json.arr(
          Json.arr(
            "Present-day climate change includes both global warming and its wider effects."
          )
        ),
        "source" -> "wikipedia"
      )
    )
  )

  private val callId = "3DHY8663m"

  private val messages = Seq(
    SystemMessage(
      "Answer the user by providing references to the source of the information using the " +
        "tool available."
    ),
    UserMessage("Who won the Nobel Peace Prize in 2024?"),
    AssistantToolMessage(
      content = Some(""),
      tool_calls = Seq(callId -> FunctionCallSpec("get_information", "{}"))
    ),
    ToolMessage(Some(references), callId, "get_information")
  )

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    val mistral =
      OpenAIChatCompletionServiceFactory.withStreaming(ChatProviderSettings.mistral)
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

    def settings(model: String) =
      CreateChatCompletionSettings(model = model, max_tokens = Some(400))

    // Medium 3.5 streams the reference as a delta of its own; Small 4 and Large 3 next to text
    val models =
      Seq(NonOpenAIModelId.mistral_medium_latest, NonOpenAIModelId.mistral_small_latest)

    val all = models.foldLeft(Future.unit) {
      (
        acc,
        model
      ) =>
        for {
          _ <- acc
          _ <- section(s"$model: sync answer with references") {
            mistral
              .createChatToolCompletion(messages, Seq(tool), settings = settings(model))
              .map { response =>
                val text = response.choices.head.message.content.getOrElse("")
                if (!text.contains("Nihon Hidankyo")) sys.error(s"text='$text'")
                s"'${text.take(120)}...'"
              }
          }
          _ <- section(s"$model: typed stream completes, reference chunks as Other") {
            // the model emits a reference chunk only some of the time (live 2026-10-09: a raw
            // stream had none), so up to three streams are read; each must complete with the
            // answer - a stream with a reference used to fail with error.expected.jsstring
            def attempt(n: Int): Future[String] =
              mistral
                .createChatToolCompletionStreamed(messages, Seq(tool), None, settings(model))
                .runWith(Sink.seq)
                .flatMap { chunks =>
                  val text = chunks.collect { case ChatChunk.Text(t) => t }.mkString
                  val references = chunks.collect {
                    case ChatChunk.Other(kind, raw) if kind == "content.reference" => raw
                  }
                  if (!text.contains("Nihon Hidankyo")) sys.error(s"text='$text'")
                  if (references.nonEmpty)
                    Future.successful(
                      s"attempt $n: ${references.size} reference chunk(s) - " +
                        references.map(_.toString.take(80)).mkString("; ")
                    )
                  else if (n < 3) attempt(n + 1)
                  else
                    Future.successful(s"$n streams completed, none carried a reference chunk")
                }
            attempt(1)
          }
        } yield ()
    }

    Try(Await.result(all, 5.minutes)).failed.foreach { e =>
      failures.incrementAndGet()
      println(s"[FAIL] the run did not complete: $e")
    }
    println(if (failures.get == 0) "ALL PASSED" else s"${failures.get} FAILED")

    mistral.close()
    Await.result(system.terminate(), 30.seconds)
    System.exit(if (failures.get == 0) 0 else 1)
  }
}
