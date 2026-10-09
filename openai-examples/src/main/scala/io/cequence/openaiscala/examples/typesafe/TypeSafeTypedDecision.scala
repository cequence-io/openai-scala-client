package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.domain.{JsonSchemaDescription, JsonSchemaRange}
import io.cequence.openaiscala.typesafe.domain.Decision
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
import io.cequence.openaiscala.typesafe.service.TypeSafeServiceFactory
import io.cequence.wsclient.JsonUtil
import play.api.libs.json.{Format, Json}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal

/**
 * A case class decided by TypeSafe's Jev in one call (`DecisionServiceExtra.decide[T]`): each
 * field becomes a question - a yes/no for the `Boolean`, a choice for the enum, a 1-5 scale
 * for the ranged `Int`, one yes/no per option for the `Seq` of an enum - and the answers come
 * back as a `Triage`, with the probabilities behind each field. The enums' values carry
 * descriptions, which the questions read.
 *
 * Requires `TYPESAFE_API_KEY`; a run costs a fraction of a cent.
 */
object TypeSafeTypedDecision {

  // the values' descriptions go into the field's description (a JSON schema enum has no place
  // for them), where both an LLM and a decision model read them
  sealed trait Team
  @JsonSchemaDescription("Payments, invoices, refunds") case object Billing extends Team
  @JsonSchemaDescription("Problems using the product") case object Technical extends Team
  @JsonSchemaDescription("Pricing, quotes, upgrades, new accounts") case object Sales
      extends Team

  sealed trait Topic
  @JsonSchemaDescription("Charges, refunds, payment methods") case object Payments
      extends Topic
  @JsonSchemaDescription("Signing in, passwords, accounts") case object Login extends Topic
  @JsonSchemaDescription("Delivery of physical orders") case object Shipping extends Topic

  implicit val teamFormat: Format[Team] = JsonUtil.enumFormat[Team](Billing, Technical, Sales)
  implicit val topicFormat: Format[Topic] =
    JsonUtil.enumFormat[Topic](Payments, Login, Shipping)

  @JsonSchemaDescription("A support ticket, triaged")
  case class Triage(
    @JsonSchemaDescription("Does the customer ask for a refund?") refund: Boolean,
    @JsonSchemaDescription("Which team should handle the ticket?") team: Team,
    @JsonSchemaDescription("How urgent is the ticket, from 1 (it can wait) to 5 (right now)?")
    @JsonSchemaRange(1, 5)
    urgency: Int,
    @JsonSchemaDescription("Which topics does the ticket mention?") topics: Seq[Topic]
  )

  implicit val triageFormat: Format[Triage] = Json.format[Triage]

  private case class Ticket(
    text: String,
    expectedTeam: Team
  )

  private val tickets = Seq(
    Ticket(
      "I was charged twice for my order last week. Please refund the second charge as soon as possible!",
      Billing
    ),
    Ticket(
      "Hi, I can't log in since yesterday - the password reset email never arrives. Not urgent, but annoying.",
      Technical
    ),
    Ticket(
      "We're a team of 40 and would like a quote for the business plan, billed yearly.",
      Sales
    )
  )

  private def describe(decision: Decision[Triage]): String = {
    def percent(p: Double) = f"${p * 100}%.0f%%"
    val teams = decision.choice("team").ranked.map { case (team, p) => s"$team ${percent(p)}" }
    val urgency = decision.score("urgency")
    val topics = Seq(Payments, Login, Shipping).map { topic =>
      s"$topic ${percent(decision.noul(s"topics.[$topic]").noul)}"
    }

    s"${decision.value}\n" +
      s"    refund ${percent(decision.noul("refund").noul)} | team ${teams.mkString(", ")}\n" +
      s"    urgency levels ${urgency.levels
          .map(level => percent(urgency.probabilities.getOrElse(level, 0d)))
          .mkString(" / ")}" +
      s" | topics ${topics.mkString(", ")} | ${decision.model}, ${decision.usage}"
  }

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val service = TypeSafeServiceFactory()

    val passed =
      try {
        tickets.map { ticket =>
          val start = System.nanoTime()
          val decision = Await.result(service.decide[Triage](ticket.text), 1.minute)
          val ms = (System.nanoTime() - start) / 1000000
          val ok = decision.value.team == ticket.expectedTeam
          println(s"[${if (ok) "OK  " else "MISS"}] $ms ms | ${describe(decision)}")
          ok
        }.forall(identity)
      } catch {
        case NonFatal(e) =>
          println(s"FAILED: $e")
          false
      } finally service.close()

    println(if (passed) "ALL PASSED" else "FAILED")
    System.exit(if (passed) 0 else 1)
  }
}
