package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.domain.decisions._
import io.cequence.openaiscala.service.{OpenAIDecisionsService, OpenAIServiceFactory}
import io.cequence.openaiscala.typesafe.domain.{
  ChoiceQuestion,
  NoulQuestion,
  Question,
  ScoreQuestion,
  SystemOneResponse
}
import io.cequence.openaiscala.typesafe.service.{
  DecisionProviderSettings,
  TypeSafeService,
  TypeSafeServiceFactory
}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * One piece of code, two decision APIs, two hosts - switched by construction alone:
 *
 *   - code written against OpenAI's Decisions API (`OpenAIDecisionsService.createDecision`) on
 *     OpenAI (`OpenAIServiceFactory()`), on TypeSafe's Jev
 *     (`TypeSafeServiceFactory.asOpenAIDecisions(DecisionProviderSettings.typeSafe)` - the
 *     questions translated to System One's) and on OpenAI again through the typesafe-client
 *     (`asOpenAIDecisions(DecisionProviderSettings.openAI)` - sent as they are)
 *   - code written against TypeSafe's System One (`TypeSafeService.systemOne`) on Jev and on
 *     OpenAI (`TypeSafeServiceFactory(DecisionProviderSettings.openAI)` - translated to
 *     OpenAI's)
 *
 * Requires `OPENAI_SCALA_CLIENT_API_KEY` and `TYPESAFE_API_KEY`; a run costs a fraction of a
 * cent.
 */
object DecisionApiSwitchSmokeTest {

  private val ticket =
    "I was charged twice for my order last week. Please refund the second charge as soon as possible!"

  // OpenAI's terms: named questions plus an unnamed one with boolean values
  private val openAIQuestions = Seq(
    DecisionQuestion.Predicate("Does the customer ask for a refund?", Some("refund")),
    DecisionQuestion.Choice(
      "Which team should handle the ticket?",
      Seq(
        DecisionChoice("billing", "Payments, invoices, refunds."),
        DecisionChoice("technical", "Problems using the product."),
        DecisionChoice("sales", "Pricing, quotes, upgrades.")
      ),
      Some("team")
    ),
    DecisionQuestion.Score(
      "How urgent is the ticket?",
      Seq(DecisionLevel("low"), DecisionLevel("medium"), DecisionLevel("high")),
      Some("urgency")
    ),
    DecisionQuestion.Choice(
      "Is the customer angry?",
      Seq(DecisionChoice(true), DecisionChoice(false))
    )
  )

  // System One's terms
  private val systemOneQuestions: Map[String, Question] = Map(
    "refund" -> NoulQuestion("Does the customer ask for a refund?"),
    "team" -> ChoiceQuestion(
      "Which team should handle the ticket?",
      "billing" -> "Payments, invoices, refunds.",
      "technical" -> "Problems using the product.",
      "sales" -> "Pricing, quotes, upgrades."
    ),
    "urgency" -> ScoreQuestion("How urgent is the ticket?", "low", "medium", "high")
  )

  // the code written against each API, unaware of the host
  private def askOpenAIStyle(
    decisions: OpenAIDecisionsService
  ): Future[Decision] =
    decisions.createDecision(DecisionInput.Text(ticket), openAIQuestions)

  private def askSystemOneStyle(service: TypeSafeService): Future[SystemOneResponse] =
    service.systemOne(ticket, systemOneQuestions)

  private def timed[T](future: => Future[T]): (T, Long) = {
    val start = System.nanoTime()
    val result = Await.result(future, 1.minute)
    (result, (System.nanoTime() - start) / 1000000)
  }

  def main(args: Array[String]): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val openAIStyle: Seq[(String, OpenAIDecisionsService)] = Seq(
      "OpenAI (OpenAIServiceFactory)" -> OpenAIServiceFactory(),
      "Jev (asOpenAIDecisions)" ->
        TypeSafeServiceFactory.asOpenAIDecisions(DecisionProviderSettings.typeSafe),
      "OpenAI (asOpenAIDecisions)" ->
        TypeSafeServiceFactory.asOpenAIDecisions(DecisionProviderSettings.openAI)
    )
    val systemOneStyle: Seq[(String, TypeSafeService)] = Seq(
      "Jev (TypeSafeServiceFactory)" -> TypeSafeServiceFactory(
        DecisionProviderSettings.typeSafe
      ),
      "OpenAI (TypeSafeServiceFactory)" -> TypeSafeServiceFactory(
        DecisionProviderSettings.openAI
      )
    )

    var failures = Seq.empty[String]
    def check(
      label: String,
      ok: Boolean
    ): Unit = if (!ok) failures :+= label

    try {
      println("OpenAI's Decisions API interface:")
      openAIStyle.foreach { case (host, decisions) =>
        val (decision, ms) = timed(askOpenAIStyle(decisions))
        println(
          f"  $host%-32s $ms%4d ms  model ${decision.model}, ${decision.usage.map(_.inputTokens)} input tokens"
        )
        decision.answers.foreach(answer => println(s"      $answer"))

        check(
          s"$host: refund",
          decision.answer("refund").exists {
            case DecisionAnswer.Predicate(_, p) => p > 0.5
            case _                              => false
          }
        )
        check(
          s"$host: billing",
          decision.answer("team").exists {
            case answer: DecisionAnswer.Choice =>
              answer.choice == DecisionValue.Text("billing")
            case _ => false
          }
        )
        // the unnamed question: in its place, without a name, its values booleans
        check(
          s"$host: the unnamed boolean choice",
          decision.answers.lift(3).exists {
            case answer: DecisionAnswer.Choice =>
              answer.name.isEmpty && answer.choice.isInstanceOf[DecisionValue.Bool]
            case _ => false
          }
        )
      }

      println("TypeSafe's System One interface:")
      systemOneStyle.foreach { case (host, service) =>
        val (response, ms) = timed(askSystemOneStyle(service))
        println(f"  $host%-32s $ms%4d ms  model ${response.model}, ${response.usage}")
        println(
          f"      refund ${response.noul("refund").noul}%.2f, team ${response.choice("team").choice}, " +
            f"urgency ${response.score("urgency").score}%.2f"
        )
        check(s"$host: refund", response.noul("refund").noul > 0.5)
        check(s"$host: billing", response.choice("team").choice == "billing")
      }
    } catch {
      case NonFatal(e) =>
        failures :+= s"error: $e"
        e.printStackTrace()
    } finally {
      openAIStyle.foreach(_._2.close())
      systemOneStyle.foreach(_._2.close())
    }

    println(if (failures.isEmpty) "ALL PASSED" else s"FAILED: ${failures.mkString("; ")}")
    System.exit(if (failures.isEmpty) 0 else 1)
  }
}
