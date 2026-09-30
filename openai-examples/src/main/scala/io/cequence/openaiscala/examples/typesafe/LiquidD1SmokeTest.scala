package io.cequence.openaiscala.examples.typesafe

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.domain.{JsonSchema, UserMessage}
import io.cequence.openaiscala.service.OpenAIChatCompletionExtra._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.{
  TypeSafeServiceAdapters,
  TypeSafeServiceFactory
}
import play.api.libs.json.{Format, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * Live walkthrough of Liquid AI's decision model d1 (launched 2026-09-30). Liquid serves it on
 * TypeSafe's System One API, so the typesafe-client talks to it -
 * `TypeSafeServiceFactory.liquid` only points the client at Liquid's host:
 *
 *   - the models Liquid lists (`d1:free`)
 *   - a native call with a noul, a choice and a score question (d1 answers with calibrated
 *     probabilities and zero output tokens)
 *   - the same questions as a JSON schema through the OpenAI interface (`asOpenAI` +
 *     `createChatCompletionWithJSON[T]`)
 *   - with `TYPESAFE_API_KEY` set, the same native call on TypeSafe's Jev, side by side
 *
 * Requires `LIQUID_API_KEY`. On its launch day d1 answered 429 `model_unavailable` for a
 * while, so the calls go through the retry adapter (a 429 is transient there).
 */
object LiquidD1SmokeTest {

  private case class Triage(
    is_complaint: Boolean,
    department: String,
    frustration: Int
  )

  private implicit val triageFormat: Format[Triage] = Json.format[Triage]

  private val state =
    "I have been waiting over three weeks for my order and nobody answers my emails. " +
      "I want my money back."

  private val questions: Map[String, Question] = Map(
    "is_complaint" -> NoulQuestion("Is this message a complaint from the customer?"),
    "department" -> ChoiceQuestion(
      "Which team should handle it?",
      "billing" -> "payments, refunds and invoices",
      "shipping" -> "deliveries and orders",
      "technical" -> "product problems"
    ),
    "frustration" -> ScoreQuestion(
      "How frustrated is the customer?",
      "calm",
      "mildly annoyed",
      "frustrated",
      "furious"
    )
  )

  // the same questions as a closed-vocabulary schema (boolean -> noul, enum -> choice, a small
  // integer range -> score)
  private val triageSchema = JsonSchemaDef(
    name = "triage",
    strict = true,
    structure = Left(
      JsonSchema.Object(
        properties = Seq(
          "is_complaint" -> JsonSchema.Boolean(
            Some("Is this message a complaint from the customer?")
          ),
          "department" -> JsonSchema.String(
            Some("Which team should handle it?"),
            `enum` = Seq("billing", "shipping", "technical")
          ),
          "frustration" -> JsonSchema.Integer(
            Some("How frustrated is the customer, 0 (calm) to 3 (furious)?"),
            minimum = Some(0),
            maximum = Some(3)
          )
        ),
        required = Seq("is_complaint", "department", "frustration")
      )
    )
  )

  private def show(
    label: String,
    response: SystemOneResponse
  ): Unit = {
    val department = response.choice("department")
    val frustration = response.score("frustration")
    println(s"[$label] model ${response.model}, usage ${response.usage}")
    println(f"  is_complaint : ${response.noul("is_complaint").noul}%.3f")
    println(
      f"  department   : ${department.choice} (confidence ${department.confidence}%.3f) " +
        department.ranked.map { case (option, p) => f"$option=$p%.3f" }.mkString(", ")
    )
    println(
      f"  frustration  : ${frustration.score}%.2f of 0..3 (confidence ${frustration.confidence}%.3f)"
    )
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    implicit val scheduler: Scheduler = system.scheduler
    implicit val retrySettings: RetrySettings =
      RetrySettings(maxRetries = 6, delayOffset = 5.seconds)

    val d1 = TypeSafeServiceAdapters.retry(
      TypeSafeServiceFactory.liquid(),
      log = Some(message => println(s"[retry] $message"))
    )
    val d1AsOpenAI = TypeSafeServiceFactory.asOpenAI(d1)
    val jev =
      sys.env.get("TYPESAFE_API_KEY").filter(_.nonEmpty).map(_ => TypeSafeServiceFactory())

    val run = for {
      models <- d1.listModels
      _ = println(s"[models] ${models.map(_.name).mkString(", ")}")

      native <- d1.systemOne(state, questions)
      _ = show("d1", native)

      triage <- d1AsOpenAI.createChatCompletionWithJSON[Triage](
        Seq(UserMessage(state)),
        CreateChatCompletionSettings(model = TypeSafeModelId.liquid_d1_free)
          .withJsonSchema(triageSchema)
      )
      _ = println(s"[d1 via OpenAI] $triage")

      _ <- jev.fold(Future.successful(println("[jev] skipped - no TYPESAFE_API_KEY")))(
        _.systemOne(state, questions).map(show("jev", _))
      )
    } yield ()

    val passed =
      try {
        Await.result(run, 10.minutes)
        true
      } catch {
        case NonFatal(e) =>
          println(s"FAILED: $e")
          false
      }

    d1AsOpenAI.close()
    jev.foreach(_.close())
    Await.result(system.terminate(), 30.seconds)
    println(if (passed) "ALL PASSED" else "FAILED")
    System.exit(if (passed) 0 else 1)
  }
}
