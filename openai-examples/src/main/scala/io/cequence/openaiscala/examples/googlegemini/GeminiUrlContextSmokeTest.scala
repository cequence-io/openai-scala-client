package io.cequence.openaiscala.examples.googlegemini

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.gemini.domain.response.GenerateContentResponse
import io.cequence.openaiscala.gemini.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.domain.{ChatRole, Content, Part, Tool}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import io.cequence.openaiscala.service.{
  ChatProviderSettings,
  OpenAIChatCompletionServiceFactory
}

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * Live walkthrough of two Gemini changes (`GOOGLE_API_KEY`, a few cents per run):
 *   - the settings Gemini refuses are dropped - a presence / frequency penalty (a 400 on every
 *     model) and more than one candidate (a 400 on every model but 2.x) - through the adapter
 *     (`GeminiServiceFactory.asOpenAI`) and Google's OpenAI-compatible endpoint
 *     (`ChatProviderSettings.gemini`); `n = 2` on Gemini 2.5 still answers two choices;
 *   - the URL context tool (`Tool.UrlContext`): natively, through the adapter's sync call (the
 *     metadata in `originalResponse`) and its typed stream (`Other("urlContextMetadata")`).
 *
 * `models=<id,...>` overrides the models (default: 2.5 Flash, 3.8 Flash, 4 Argon). Every
 * section prints PASS/FAIL; the exit code is 1 if any failed.
 */
object GeminiUrlContextSmokeTest {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher

    val models = args
      .find(_.startsWith("models="))
      .map(_.drop("models=".length).split(',').toSeq)
      .getOrElse(
        Seq(
          NonOpenAIModelId.gemini_2_5_flash,
          "gemini-3.8-flash",
          NonOpenAIModelId.gemini_4_argon
        )
      )

    val native = GeminiServiceFactory()
    val adapter = GeminiServiceFactory.asOpenAI()
    val compatible = OpenAIChatCompletionServiceFactory(ChatProviderSettings.gemini)
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

    // what Gemini refuses: penalties on every model, n > 1 but on 2.x
    def refused(
      model: String,
      n: Int
    ) = CreateChatCompletionSettings(
      model,
      presence_penalty = Some(0.5),
      frequency_penalty = Some(0.5),
      n = Some(n),
      max_tokens = Some(4000)
    )

    def choices(
      model: String,
      count: Int
    ) =
      if (model.startsWith("gemini-2."))
        count
      else
        1

    val urlPrompt = Seq(
      UserMessage(
        "What does https://www.rfc-editor.org/rfc/rfc2606 reserve? One sentence. " +
          "Also try https://this-host-does-not-exist-xyz.invalid/page"
      )
    )

    def settingsSection(model: String) = for {
      _ <- section(s"$model: the adapter drops penalties / extra candidates") {
        adapter.createChatCompletion(arithmetic, refused(model, 2)).map { r =>
          check(r.choices.size == choices(model, 2), s"${r.choices.size} choices")
          check(r.contentHead.contains("391"), s"answered '${r.contentHead}'")
          s"${r.choices.size} choice(s), '${r.contentHead.trim}'"
        }
      }
      _ <- section(s"$model: the OpenAI-compatible endpoint drops them too") {
        compatible.createChatCompletion(arithmetic, refused(model, 2)).map { r =>
          check(r.choices.size == choices(model, 2), s"${r.choices.size} choices")
          check(r.contentHead.contains("391"), s"answered '${r.contentHead}'")
          s"${r.choices.size} choice(s), '${r.contentHead.trim}'"
        }
      }
    } yield ()

    def urlContextSection(model: String) = for {
      _ <- section(s"$model: native generateContent with Tool.UrlContext") {
        native
          .generateContent(
            Seq(Content(Seq(Part.Text(urlPrompt.head.content)), Some(ChatRole.User))),
            GenerateContentSettings(model, tools = Some(Seq(Tool.UrlContext)))
          )
          .map { response =>
            val urls = response.candidates.head.urlContextMetadata.toSeq.flatMap(_.urlMetadata)
            check(urls.exists(_.succeeded), s"fetched $urls")
            check(urls.exists(!_.succeeded), s"no failed fetch reported: $urls")
            urls.map(u => s"${u.retrievedUrl} ${u.urlRetrievalStatus}").mkString("; ")
          }
      }
      _ <- section(s"$model: the adapter's sync call - the metadata in originalResponse") {
        adapter
          .createChatCompletion(
            urlPrompt,
            CreateChatCompletionSettings(model).setGeminiTools(Seq(Tool.UrlContext))
          )
          .map { r =>
            val urls = r.originalResponse.toSeq.flatMap {
              case response: GenerateContentResponse =>
                response.candidates.flatMap(_.urlContextMetadata.toSeq.flatMap(_.urlMetadata))
              case _ => Nil
            }
            check(urls.exists(_.succeeded), s"fetched $urls")
            check(
              r.contentHead.toLowerCase.contains("example"),
              s"answered '${r.contentHead}'"
            )
            s"${urls.size} URL(s), prompt tokens ${r.usage.map(_.prompt_tokens)} (fetched pages included)"
          }
      }
      _ <- section(s"$model: the typed stream - Other(urlContextMetadata) + citations") {
        adapter
          .createChatToolCompletionStreamed(
            urlPrompt,
            Nil,
            None,
            CreateChatCompletionSettings(model).setGeminiTools(Seq(Tool.UrlContext))
          )
          .runWith(Sink.seq)
          .map { chunks =>
            val metadata = chunks.collect { case ChatChunk.Other("urlContextMetadata", raw) =>
              raw
            }
            val citations = chunks.collect { case c: ChatChunk.Citation => c.url }.flatten
            check(metadata.size == 1, s"${metadata.size} metadata chunks")
            check(citations.nonEmpty, "no citations")
            s"${metadata.head.toString.take(160)}, citations $citations"
          }
      }
    } yield ()

    val run = models.foldLeft(Future.successful(())) { case (acc, model) =>
      acc.flatMap(_ => settingsSection(model)).flatMap(_ => urlContextSection(model))
    }

    Try(Await.result(run, 15.minutes)) match {
      case Failure(e) =>
        failures.incrementAndGet()
        println(s"[FAIL] run: $e")
      case Success(_) => ()
    }
    native.close()
    adapter.close()
    compatible.close()
    println(
      if (failures.get == 0)
        "ALL PASSED"
      else
        s"${failures.get} FAILED"
    )
    Await.result(system.terminate(), 30.seconds)
    System.exit(
      if (failures.get == 0)
        0
      else
        1
    )
  }
}
