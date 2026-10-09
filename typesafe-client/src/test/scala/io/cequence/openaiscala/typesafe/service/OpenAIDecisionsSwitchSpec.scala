package io.cequence.openaiscala.typesafe.service

import akka.actor.{ActorSystem, Scheduler}
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.service.OpenAIDecisionsService
import io.cequence.openaiscala.{OpenAIScalaClientException, OpenAIScalaRateLimitException}
import io.cequence.openaiscala.domain.decisions.{Decision => OpenAIDecision, _}
import io.cequence.openaiscala.domain.responsesapi.UsageInfo
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.impl.{
  OpenAIDecisionsOverTypeSafe,
  OpenAIToSystemOne,
  SystemOneToOpenAI
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsString, JsValue, Json}

import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * OpenAI's Decisions API interface on a System One service (`asOpenAIDecisions`): the
 * questions and the input translated, the answers back as OpenAI gives them, the failures
 * repacked.
 */
class OpenAIDecisionsSwitchSpec extends AnyWordSpec with Matchers {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private def await[T](f: Future[T]): T = Await.result(f, 10.seconds)

  private val ticket = "I was charged twice for my order. Please refund the second charge!"

  private val questions = Seq(
    DecisionQuestion.Predicate("Does the customer ask for a refund?", Some("refund")),
    DecisionQuestion.Choice(
      "Which team?",
      Seq(DecisionChoice("billing", "Payments, refunds."), DecisionChoice("sales")),
      Some("team")
    ),
    // unnamed, with boolean values
    DecisionQuestion.Choice("Is it urgent?", Seq(DecisionChoice(true), DecisionChoice(false))),
    DecisionQuestion.Score(
      "How urgent?",
      Seq(DecisionLevel("low", Some("It can wait.")), DecisionLevel("high")),
      Some("urgency")
    )
  )

  private val answers: Map[String, Answer] = Map(
    "refund" -> NoulAnswer(0.97),
    "team" -> ChoiceAnswer("billing", 0.9, Map("sales" -> 0.05, "billing" -> 0.95)),
    "question_3" -> ChoiceAnswer("true", 0.4, Map("true" -> 0.7, "false" -> 0.3)),
    "urgency" -> ScoreAnswer(
      0.8,
      0.5,
      Map(
        0 -> Json.obj("label" -> "low", "description" -> "It can wait."),
        1 -> JsString("high")
      ),
      Map(1 -> 0.8, 0 -> 0.2)
    )
  )

  "asOpenAIDecisions over a System One service" should {

    "ask the questions in System One's terms and give the answers back as OpenAI does" in {
      val jev = new RecordingTypeSafeService(answers)
      val decisions = TypeSafeServiceFactory.asOpenAIDecisions(jev)

      val decision = await(decisions.createDecision(DecisionInput.Text(ticket), questions))

      jev.lastState shouldBe Some(JsString(ticket))
      // no model in the settings: the service's default
      jev.lastModel shouldBe Some("jev-latest")
      jev.lastQuestions shouldBe Map(
        "refund" -> NoulQuestion("Does the customer ask for a refund?"),
        "team" -> ChoiceQuestion(
          ListMap("billing" -> Some(JsString("Payments, refunds.")), "sales" -> None),
          Some(JsString("Which team?"))
        ),
        "question_3" -> ChoiceQuestion.ofLabels("Is it urgent?", "true", "false"),
        "urgency" -> ScoreQuestion(
          Seq(Json.obj("label" -> "low", "description" -> "It can wait."), JsString("high")),
          Some(JsString("How urgent?"))
        )
      )

      decision shouldBe OpenAIDecision(
        "jev-1.13.0",
        Seq(
          DecisionAnswer.Predicate(Some("refund"), 0.97),
          DecisionAnswer.Choice(
            Some("team"),
            DecisionValue.Text("billing"),
            // in the question's order
            Seq(
              ChoiceProbability(DecisionValue.Text("billing"), 0.95),
              ChoiceProbability(DecisionValue.Text("sales"), 0.05)
            ),
            0.9
          ),
          // the boolean values as asked, the name as asked (none)
          DecisionAnswer.Choice(
            None,
            DecisionValue.Bool(true),
            Seq(
              ChoiceProbability(DecisionValue.Bool(true), 0.7),
              ChoiceProbability(DecisionValue.Bool(false), 0.3)
            ),
            0.4
          ),
          DecisionAnswer.Score(
            Some("urgency"),
            0.8,
            Seq(LevelProbability(0, "low", 0.2), LevelProbability(1, "high", 0.8)),
            0.5
          )
        ),
        Some(UsageInfo(inputTokens = 300, outputTokens = 40, totalTokens = 340)),
        // the host's request id comes along
        Some("req-1")
      )

      // an explicit model goes as it is
      await(
        decisions.createDecision(
          DecisionInput.Text(ticket),
          questions.take(1),
          CreateDecisionSettings(model = Some("jev-preview"))
        )
      )
      jev.lastModel shouldBe Some("jev-preview")
    }

    "key unnamed questions by their position, around the names given" in {
      OpenAIToSystemOne
        .asked(
          Seq(
            DecisionQuestion.Predicate("A?", Some("question_2")),
            DecisionQuestion.Predicate("B?"),
            DecisionQuestion.Predicate("C?", Some(""))
          )
        )
        .map(_.key) shouldBe Seq("question_2", "question_2_2", "question_3")
    }

    "map a message's texts and images, and several messages" in {
      val png = "data:image/png;base64,AAAA"
      OpenAIToSystemOne.state(
        DecisionInput.of(DecisionContent.InputText("Look."), DecisionContent.InputText("Now."))
      ) shouldBe JsString("Look.\nNow.")

      OpenAIToSystemOne.state(
        DecisionInput.of(
          DecisionContent.InputText("Look."),
          DecisionContent.InputImage(png, Some(DecisionImageDetail.high))
        )
      ) shouldBe Json.arr("Look.", DecisionImage.part(png))

      OpenAIToSystemOne.state(
        DecisionInput.Messages(
          Seq(
            DecisionMessage(Seq(DecisionContent.InputText("First."))),
            DecisionMessage(Seq(DecisionContent.InputText("Second.")))
          )
        )
      ) shouldBe Json.arr("First.", "Second.")
    }

    "refuse up front what System One cannot ask, as an OpenAI client error" in {
      val jev = new RecordingTypeSafeService(answers)
      val decisions = TypeSafeServiceFactory.asOpenAIDecisions(jev)
      def refusal(
        input: DecisionInput,
        asked: Seq[DecisionQuestion]
      ): Throwable = Await.result(decisions.createDecision(input, asked).failed, 10.seconds)

      val duplicate =
        refusal(DecisionInput.Text(ticket), Seq.fill(2)(questions.head))
      duplicate shouldBe an[OpenAIScalaClientException]
      duplicate.getMessage should include("refund repeats")

      refusal(
        DecisionInput.Text(ticket),
        Seq(
          DecisionQuestion.Choice("?", Seq(DecisionChoice("true"), DecisionChoice(true)))
        )
      ).getMessage should include("true would be one option")

      // images, to a service not declared to read them
      refusal(
        DecisionInput.of(DecisionContent.InputImage("data:image/png;base64,AAAA")),
        questions.take(1)
      ).getMessage should include("imageInput = true")

      jev.lastState shouldBe None

      // ... which goes when declared
      await(
        TypeSafeServiceFactory
          .asOpenAIDecisions(jev, imageInput = true)
          .createDecision(
            DecisionInput.of(DecisionContent.InputImage("data:image/png;base64,AAAA")),
            questions.take(1)
          )
      )
      jev.lastState shouldBe Some(Json.arr(DecisionImage.part("data:image/png;base64,AAAA")))
    }

    "give a refusal back as a refusal, and keep an answer of an unknown type" in {
      val raw = Json.obj("type" -> "bounding_box", "box" -> Json.arr(1, 2))
      val jev = new RecordingTypeSafeService(
        Map(
          "refund" -> UnknownAnswer("refusal", Json.obj("type" -> "refusal")),
          "team" -> UnknownAnswer("bounding_box", raw)
        )
      )

      await(
        TypeSafeServiceFactory
          .asOpenAIDecisions(jev)
          .createDecision(DecisionInput.Text(ticket), questions.take(2))
      ).answers shouldBe Seq(
        DecisionAnswer.Refusal(Some("refund")),
        DecisionAnswer.Unknown(Some("team"), "bounding_box", raw: JsObject)
      )
    }

    "repack a native failure as the OpenAI one, the native one as its cause" in {
      val limited = new RecordingTypeSafeService(answers) {
        override def systemOne(
          state: JsValue,
          questions: Map[String, Question],
          model: String
        ): Future[SystemOneResponse] =
          Future.failed(new TypeSafeScalaRateLimitException("Code 429 : slow down"))
      }

      val error = Await.result(
        TypeSafeServiceFactory
          .asOpenAIDecisions(limited)
          .createDecision(DecisionInput.Text(ticket), questions)
          .failed,
        10.seconds
      )
      error shouldBe an[OpenAIScalaRateLimitException]
      error.getCause shouldBe a[TypeSafeScalaRateLimitException]
    }

    "adapt a wrapped service, and take a service of the factory as it is" in {
      TypeSafeServiceFactory.asOpenAIDecisions(
        new RecordingTypeSafeService(answers)
      ) shouldBe an[OpenAIDecisionsOverTypeSafe]

      val jev = TypeSafeServiceFactory(apiKey = "k", baseUrl = "http://localhost:1")
      TypeSafeServiceFactory.asOpenAIDecisions(jev) should be theSameInstanceAs jev
      jev.close()
    }

    "keep a factory service's own path through the retry adapter" in {
      implicit val retrySettings: RetrySettings = RetrySettings(maxRetries = 1)
      val system = ActorSystem("decisions-switch-retry")
      implicit val scheduler: Scheduler = system.scheduler

      try {
        // the retry adapter over a factory service serves the interface itself ...
        val jev = TypeSafeServiceFactory(apiKey = "k", baseUrl = "http://localhost:1")
        val retried = TypeSafeServiceAdapters.retry(jev)
        retried shouldBe an[OpenAIDecisionsService]
        TypeSafeServiceFactory.asOpenAIDecisions(retried) should be theSameInstanceAs retried
        jev.close()

        // ... over any other service it is the translation, with the retries underneath
        val wrapped = TypeSafeServiceAdapters.retry(new RecordingTypeSafeService(answers))
        wrapped should not be an[OpenAIDecisionsService]
        TypeSafeServiceFactory
          .asOpenAIDecisions(wrapped) shouldBe an[OpenAIDecisionsOverTypeSafe]
        await(
          TypeSafeServiceFactory
            .asOpenAIDecisions(wrapped)
            .createDecision(DecisionInput.Text(ticket), questions.take(1))
        ).answers shouldBe Seq(DecisionAnswer.Predicate(Some("refund"), 0.97))
      } finally await(system.terminate())
    }
  }

  "The two directions" should {

    "agree - System One's questions asked through OpenAI's terms come back as they were" in {
      val systemOne: Seq[(String, Question)] = Seq(
        "refund" -> NoulQuestion("Does the customer ask for a refund?"),
        "team" -> ChoiceQuestion(
          "Which team?",
          "billing" -> "Payments.",
          "sales" -> "Prices."
        ),
        "urgency" -> ScoreQuestion("How urgent?", "low", "high")
      )

      OpenAIToSystemOne
        .asked(systemOne.map { case (name, question) =>
          SystemOneToOpenAI.question(name, question)
        })
        .map(asked => asked.key -> asked.question) shouldBe systemOne
    }
  }
}
