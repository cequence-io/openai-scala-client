package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.examples.typesafe.TypeSafeTypedDecision._
import io.cequence.openaiscala.typesafe.domain.{
  ChoiceQuestion,
  DecisionImage,
  NoulQuestion,
  Question,
  ScoreQuestion
}
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeScalaInvalidRequestException,
  TypeSafeServiceFactory
}
import play.api.libs.json.Json

import java.util.Base64
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Decision models on a local llama.cpp server (`DecisionProviderSettings.llamaCpp`) - no key,
 * TypeSafe's protocol:
 *
 * {{{
 * llama-server -hf LiquidAI/d1-3B-GGUF:Q8_0                    # one model (Liquid AI's Open d1)
 * llama-server --models-dir ./models                            # a router over several
 * }}}
 *
 *   1. the decision models the server lists (a router's chat models are left out), with what
 *      they read
 *   1. a noul, a choice and a score in one call
 *   1. a typed decision (`decide[Triage]`)
 *   1. an image - answered by a model with a projector (`--mmproj`), refused (501, an invalid
 *      request) by one without
 *
 * Args: the model (default: the first one listed - a server of one model ignores it); the
 * server's URL comes from `LLAMA_CPP_BASE_URL` (default `http://127.0.0.1:8080/`), its key, if
 * it was started with one, from `LLAMA_API_KEY`.
 *
 * Live 2026-10-07 with llama.cpp b11476 on two CPU cores, a router over Julia-1 and Laya (both
 * text only - mainline llama.cpp could not load Liquid's d1 yet: "unsupported decision model
 * type: d1"): the three questions in ~160 ms (Julia-1) / ~1.5 s (Laya) once loaded, the first
 * call ~2.5 s (the router loads the model); the image refused (501). With `Laya-Q8_0` all
 * passed (the triage Billing / Payments, ~5.6 s for its six questions); the 168 MB Julia-1
 * answers the plain choice billing 0.99999 but puts the typed triage in Sales.
 */
object LlamaCppDecisionsSmokeTest {

  // a 1 x 1 PNG
  private val pixel = Base64.getDecoder.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNgYPgPAAEDAQAIicLsAAAAAElFTkSuQmCC"
  )

  private val ticket =
    "I was charged twice for my order last week. Please refund the second charge!"

  private val questions: Map[String, Question] = Map(
    "refund" -> NoulQuestion("Does the customer ask for a refund?"),
    "team" -> ChoiceQuestion(
      "Which team should handle this?",
      "billing" -> "Charges, refunds, invoices",
      "technical" -> "App or site faults",
      "sales" -> "Pricing, quotes"
    ),
    "urgency" -> ScoreQuestion("How urgent is this?", "Can wait", "Today", "Blocking now")
  )

  private def timed[T](future: => Future[T]): (T, Long) = {
    val start = System.nanoTime()
    val result = Await.result(future, 5.minutes)
    (result, (System.nanoTime() - start) / 1000000)
  }

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val provider = sys.env
      .get("LLAMA_CPP_BASE_URL")
      .fold(DecisionProviderSettings.llamaCpp)(url =>
        DecisionProviderSettings.llamaCpp.copy(baseUrl = url)
      )
    val llamaCpp = TypeSafeServiceFactory(provider)

    var failures = Seq.empty[String]
    def check(
      label: String,
      ok: Boolean
    ): Unit = if (!ok) failures :+= label

    try {
      // 1. the models
      val (models, _) = timed(llamaCpp.listModels)
      models.foreach(m =>
        println(s"[models] ${m.name} reads ${m.input_modalities.getOrElse(Nil)}")
      )
      check("models: some", models.nonEmpty)
      val model = args.headOption.orElse(models.headOption.map(_.name)).getOrElse("")
      val readsImages =
        models.find(_.name == model).exists(_.input_modalities.exists(_.contains("image")))

      // 2. three questions
      val (answers, ms) = timed(llamaCpp.systemOne(ticket, questions, model))
      println(s"[$model] $ms ms: ${answers.answers} ${answers.usage}")
      check("refund", answers.noul("refund").noul > 0.5)
      check("billing", answers.choice("team").choice == "billing")

      // 3. a typed decision
      val (triage, triageMs) = timed(llamaCpp.decide[Triage](ticket, model))
      println(s"[decide] $triageMs ms: ${triage.value}")
      check("decide: Billing", triage.value.team == Billing)

      // 4. an image
      val photo = Json.arr("A photo from a customer.", DecisionImage(pixel))
      val image = Map("photo" -> NoulQuestion("Is there a photo?"))
      if (readsImages) {
        val (seen, seenMs) = timed(llamaCpp.systemOne(photo, image, model))
        println(s"[image] $seenMs ms: ${seen.answers}")
      } else {
        val refused = Await.result(llamaCpp.systemOne(photo, image, model).failed, 1.minute)
        println(s"[image] refused: ${refused.getMessage}")
        check("image: refused", refused.isInstanceOf[TypeSafeScalaInvalidRequestException])
      }
    } catch {
      case NonFatal(e) =>
        failures :+= s"error: $e"
        e.printStackTrace()
    } finally llamaCpp.close()

    println(if (failures.isEmpty) "ALL PASSED" else s"FAILED: ${failures.mkString("; ")}")
    System.exit(if (failures.isEmpty) 0 else 1)
  }
}
