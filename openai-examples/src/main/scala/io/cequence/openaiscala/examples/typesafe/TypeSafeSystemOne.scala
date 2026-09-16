package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.typesafe.domain.{ChoiceQuestion, NoulQuestion, ScoreQuestion}
import io.cequence.openaiscala.typesafe.service.{TypeSafeService, TypeSafeServiceFactory}

import scala.concurrent.Future

/**
 * The TypeSafe quick start: one support ticket, three typed questions (a choice, a score and a
 * yes/no "noul"), all answered in one ~100 ms call with calibrated probabilities.
 *
 * Requires `TYPESAFE_API_KEY` (from https://console.typesafe.ai/settings/keys).
 */
object TypeSafeSystemOne extends ExampleBase[TypeSafeService] {

  override val service: TypeSafeService = TypeSafeServiceFactory()

  private val ticket =
    "Hi, I've been trying to connect my Stripe account for 3 days and it keeps failing. " +
      "I'm losing sales. Please help ASAP."

  override protected def run: Future[_] =
    service
      .systemOne(
        state = ticket,
        questions = Map(
          "department" -> ChoiceQuestion(
            "Which team should handle this",
            "billing" -> "Payment or subscription issues",
            "technical" -> "Bugs or integration problems",
            "sales" -> "Pricing or account questions"
          ),
          "frustration" -> ScoreQuestion(
            "How frustrated the customer appears",
            "Calm, just stating facts",
            "Frustrated but civil",
            "Very angry, strong language"
          ),
          "is_urgent" -> NoulQuestion("The message conveys urgency or time-sensitivity")
        )
      )
      .map { response =>
        val department = response.choice("department")
        val frustration = response.score("frustration")
        val urgent = response.noul("is_urgent")

        println(
          s"model      : ${response.model}  (request ${response.requestId.getOrElse("-")})"
        )
        println(s"department : ${department.choice}  confidence ${department.confidence}")
        department.ranked.foreach { case (label, p) => println(f"  $label%-10s $p%.3f") }
        println(
          f"frustration: ${frustration.score}%.2f / ${frustration.maxLevel} " +
            s"(${frustration.describe(frustration.mostLikelyLevel).getOrElse("?")}), " +
            f"confidence ${frustration.confidence}%.2f"
        )
        println(f"is_urgent  : ${urgent.noul}%.3f -> ${if (urgent.isYes()) "yes" else "no"}")
        println(s"usage      : ${response.usage}")
      }
}
