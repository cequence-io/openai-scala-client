package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import play.api.libs.json.{JsString, JsValue, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Where should an OpenAI-style SYSTEM message go when a chat completion is mapped onto System
 * One: into the state, or into every question's instructions? The system prompt below carries
 * COUNTER-INTUITIVE routing rules (Stripe -> sales, invoices -> technical), so a mapping that
 * fails to convey them scores badly. Each variant runs the same 8 tickets with 2 and with 8
 * questions; we compare accuracy, mean confidence and input tokens per call.
 *
 * Results 2026-09-17 (jev-1.13.0):
 * {{{
 * variant                                    questions  dept  churn  conf  tokens
 * no-system (control)                            2       4/8   7/8   0.98   324
 * state: role/content log                        2       8/8   8/8   0.90   453
 * state: role/content log                        8       8/8   8/8   0.91   530
 * state: {instructions, message}                 2       8/8   8/8   0.93   433
 * state: {instructions, message}                 8       8/8   8/8   0.94   510
 * question: system + description (text)          2       7/8   8/8   0.91   518
 * question: system + description (text)          8       7/8   8/8   0.91  1177
 * question: {context, question} (json)           2       7/8   8/8   0.93   542
 * question: {context, question} (json)           8       7/8   8/8   0.94  1273
 * }}}
 * The system message belongs in the STATE: it is read at least as well there (8/8 vs 7/8),
 * costs a flat ~110 tokens instead of ~110 per question, and a named-field object edges out
 * the role/content log on confidence and tokens.
 */
object TypeSafeMessageMappingBenchmark {

  // deliberately COUNTER-INTUITIVE, so a mapping that loses the system message scores badly
  private val system =
    "You triage support tickets. Special routing this quarter: anything mentioning Stripe " +
      "goes to 'sales' (the partnerships desk owns the Stripe relationship), invoice and VAT " +
      "problems go to 'technical' (the invoicing system is being migrated), everything else " +
      "about charges or refunds goes to 'billing', and API or webhook errors go to " +
      "'technical'. Mark is_churn_risk true ONLY when the customer explicitly says they will " +
      "cancel or leave - losing sales or frustration alone is not churn risk."

  // ticket, expected department, expected churn risk; the third ticket is a JSON payload
  private val tickets: Seq[(JsValue, String, Boolean)] = Seq(
    (
      JsString("My Stripe webhook keeps failing and I'm losing sales every hour."),
      "sales",
      false
    ),
    (JsString("I was charged twice for order A-104. Refund the duplicate."), "billing", false),
    (
      Json.obj("subject" -> "Stripe payout", "body" -> "When does my Stripe payout arrive?"),
      "sales",
      false
    ),
    (
      JsString("If you can't fix these API errors by Friday I'm cancelling my account."),
      "technical",
      true
    ),
    (
      JsString("Your invoice shows the wrong VAT number, please reissue it."),
      "technical",
      false
    ),
    (JsString("I need the invoice for March resent to accounting."), "technical", false),
    (JsString("The API key I generated returns 401 on every call."), "technical", false),
    (
      JsString("I'm moving to a competitor next month. Refund this month's charge."),
      "billing",
      true
    )
  )

  private val departmentDescription = "Which team should handle this"
  private val churnDescription = "The customer is at risk of churning"

  private val filler = Seq(
    "mentions_money" -> "The message mentions an amount of money",
    "is_polite" -> "The customer is polite",
    "has_deadline" -> "The customer states a deadline",
    "is_question" -> "The message is a question rather than a report",
    "mentions_order" -> "An order id is mentioned",
    "needs_reply_today" -> "This should be answered today"
  )

  private def questions(
    instructions: String => JsValue,
    extra: Int
  ): Map[String, Question] =
    Map[String, Question](
      "department" -> ChoiceQuestion(
        criteria = scala.collection.immutable.ListMap(
          "billing" -> None,
          "technical" -> None,
          "sales" -> None
        ),
        instructions = Some(instructions(departmentDescription))
      ),
      "is_churn_risk" -> NoulQuestion(Some(instructions(churnDescription)))
    ) ++ filler.take(extra).map { case (name, d) =>
      name -> (NoulQuestion(Some(instructions(d))): Question)
    }

  private case class Variant(
    name: String,
    state: JsValue => JsValue,
    instructions: String => JsValue
  )

  private val variants = Seq(
    Variant("no-system (control)", ticket => ticket, d => JsString(d)),
    Variant(
      "state: role/content log (current adapter)",
      ticket =>
        Json.arr(
          Json.obj("role" -> "system", "content" -> system),
          Json.obj("role" -> "user", "content" -> ticket)
        ),
      d => JsString(d)
    ),
    Variant(
      "state: {instructions, message}",
      ticket => Json.obj("instructions" -> system, "message" -> ticket),
      d => JsString(d)
    ),
    Variant(
      "question: system + description (text)",
      ticket => ticket,
      d => JsString(s"$system\n\n$d")
    ),
    Variant(
      "question: {context, question} (json)",
      ticket => ticket,
      d => Json.obj("context" -> system, "question" -> d)
    )
  )

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val service = TypeSafeServiceFactory()

    val run = Future.traverse(for (v <- variants; extra <- Seq(0, 6)) yield (v, extra)) {
      case (variant, extra) =>
        Future
          .traverse(tickets) { case (ticket, department, churn) =>
            service
              .systemOne(variant.state(ticket), questions(variant.instructions, extra))
              .map { r =>
                val d = r.choice("department")
                val c = r.noul("is_churn_risk")
                (
                  d.choice == department,
                  c.isYes() == churn,
                  d.confidence,
                  r.usage.input_tokens.getOrElse(0),
                  s"${d.choice.take(4)}/${if (c.isYes()) "C" else "-"}"
                )
              }
          }
          .map((variant, extra) -> _)
    }

    try {
      val results = Await.result(run, 5.minutes)
      println(f"${"variant"}%-45s questions  dept  churn  conf   tokens  answers")
      results.foreach { case ((variant, extra), rows) =>
        val dept = rows.count(_._1)
        val churn = rows.count(_._2)
        val conf = rows.map(_._3).sum / rows.size
        val tokens = rows.map(_._4).sum / rows.size
        println(
          f"${variant.name}%-45s ${2 + extra}%2d         $dept/8   $churn/8    $conf%.2f   $tokens%5d   ${rows.map(_._5).mkString(" ")}"
        )
      }
    } finally service.close()
  }
}
