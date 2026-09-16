package io.cequence.openaiscala.examples.typesafe

import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.examples.ExampleBase
import io.cequence.openaiscala.typesafe.domain.{ChoiceAnswer, ChoiceQuestion, NoulQuestion}
import io.cequence.openaiscala.typesafe.service.{
  TypeSafeService,
  TypeSafeServiceAdapters,
  TypeSafeServiceFactory
}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * TypeSafe's confidence-gated routing pattern (docs.typesafe.ai/patterns/confidence-routing)
 * on a voice-banking command: the choice answer says WHAT the user wants, its confidence says
 * WHETHER to act - and riskier intents demand a higher bar. Every command is classified in one
 * call that also asks a speculative "is this even a banking request?" noul, so the code, not
 * the model, decides what to do with the result.
 *
 * The service is wrapped with the retry adapter, which backs off on 429 / 529 as TypeSafe
 * asks.
 *
 * Requires `TYPESAFE_API_KEY`.
 */
object TypeSafeConfidenceGatedRouting extends ExampleBase[TypeSafeService] {

  private implicit val retrySettings: RetrySettings =
    RetrySettings(maxRetries = 3, delayOffset = 500.millis)

  override val service: TypeSafeService =
    TypeSafeServiceAdapters.retry(TypeSafeServiceFactory(), log = Some(println))

  private val commands = Seq(
    "What's my balance?",
    "Send two hundred to Maria",
    "Move my savings into the stock thing my cousin mentioned",
    "What's the weather like in Oslo?"
  )

  // the more consequential the action, the more certain we want to be before acting
  private val requiredConfidence = Map(
    "check_balance" -> 0.5,
    "transfer" -> 0.9,
    "invest" -> 0.95
  )

  private val questions = Map(
    "intent" -> ChoiceQuestion(
      "What does the user want to do?",
      "check_balance" -> "See an account balance or recent activity",
      "transfer" -> "Send money to someone or between accounts",
      "invest" -> "Buy, sell or move money into an investment",
      "other" -> "Anything else"
    ),
    "is_banking" -> NoulQuestion("The request is about the user's bank accounts or money")
  )

  override protected def run: Future[_] =
    Future.sequence(commands.map(route)).map(_.foreach(println))

  private def route(command: String): Future[String] =
    service.systemOne(command, questions).map { response =>
      val intent = response.choice("intent")
      val isBanking = response.noul("is_banking").isYes(0.7)

      val decision =
        if (!isBanking) "not a banking request - hand off to general assistant"
        else act(intent)

      f"$command%-62s -> ${intent.choice}%-13s (${intent.confidence}%.2f)  $decision"
    }

  private def act(intent: ChoiceAnswer): String =
    requiredConfidence.get(intent.choice) match {
      case Some(bar) if intent.confidence >= bar => s"ACT: ${intent.choice}"
      case Some(bar) => f"ASK to confirm (${intent.confidence}%.2f < $bar%.2f)"
      case None      => "ASK what they meant"
    }
}
