package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.domain.{JsonSchema, JsonSchemaDescription, JsonSchemaRange}
import io.cequence.openaiscala.service.JsonSchemaOf
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.DecisionServiceExtra._
import io.cequence.wsclient.JsonUtil
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

// top-level, so Scala 2's runtime reflection reaches the objects
object TypeSafeDecisionTypes {

  sealed trait Team
  @JsonSchemaDescription("Payments, invoices, refunds") case object Billing extends Team
  @JsonSchemaDescription("Problems using the product") case object Technical extends Team
  @JsonSchemaDescription("Pricing, upgrades, new accounts") case object Sales extends Team

  sealed trait Topic
  @JsonSchemaDescription("Charges, refunds") case object Payments extends Topic
  case object Login extends Topic

  implicit val teamFormat: Format[Team] = JsonUtil.enumFormat[Team](Billing, Technical, Sales)
  implicit val topicFormat: Format[Topic] = JsonUtil.enumFormat[Topic](Payments, Login)

  case class Customer(
    @JsonSchemaDescription("Is the customer a VIP?") vip: Boolean
  )

  @JsonSchemaDescription("A support ticket, triaged")
  case class Triage(
    @JsonSchemaDescription("Does the customer ask for a refund?") refund: Boolean,
    @JsonSchemaDescription("Which team should handle the ticket?") team: Team,
    @JsonSchemaDescription("How urgent is the ticket, from 1 (whenever) to 5 (right now)?")
    @JsonSchemaRange(1, 5)
    urgency: Int,
    @JsonSchemaDescription("Which topics does the ticket mention?") topics: Seq[Topic],
    customer: Customer
  )

  implicit val customerFormat: Format[Customer] = Json.format[Customer]
  implicit val triageFormat: Format[Triage] = Json.format[Triage]

  // free text - nothing a decision model answers
  case class Note(note: String)
  implicit val noteFormat: Format[Note] = Json.format[Note]

  // read with snake-case names, unlike the schema's field names
  case class Urgent(isUrgent: Boolean)
  implicit val urgentFormat: Format[Urgent] =
    Json.configured(JsonConfiguration(JsonNaming.SnakeCase)).format[Urgent]
}

class TypeSafeDecisionSpec extends AnyWordSpec with Matchers {

  import TypeSafeDecisionTypes._

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private def await[T](future: Future[T]): T = Await.result(future, 10.seconds)

  private class Stub(answers: Map[String, Answer]) extends RecordingTypeSafeService(answers)

  private val triageAnswers: Map[String, Answer] = Map(
    "refund" -> NoulAnswer(0.97),
    "team" -> ChoiceAnswer(
      "Billing",
      0.8,
      Map("Billing" -> 0.91, "Sales" -> 0.04, "Technical" -> 0.05)
    ),
    "urgency" -> ScoreAnswer(
      3.6,
      0.5,
      (0 to 4).map(level => level -> JsString((level + 1).toString)).toMap,
      Map(0 -> 0.05, 1 -> 0.05, 2 -> 0.1, 3 -> 0.6, 4 -> 0.2)
    ),
    "topics.[Login]" -> NoulAnswer(0.1),
    "topics.[Payments]" -> NoulAnswer(0.88),
    "customer.vip" -> NoulAnswer(0.3)
  )

  "decide" should {

    "ask about every field in one call and read the answers back as the case class" in {
      val stub = new Stub(triageAnswers)

      val decision = await(stub.decide[Triage]("I was charged twice - refund me now!"))

      decision.value shouldBe Triage(
        refund = true,
        team = Billing,
        urgency = 4,
        topics = Seq(Payments),
        customer = Customer(vip = false)
      )

      stub.lastState shouldBe Some(JsString("I was charged twice - refund me now!"))
      stub.lastModel shouldBe Some("jev-latest")

      val questions = stub.lastQuestions
      questions.keySet shouldBe Set(
        "refund",
        "team",
        "urgency",
        "topics.[Login]",
        "topics.[Payments]",
        "customer.vip"
      )
      // the descriptions become the questions
      questions("refund") shouldBe NoulQuestion("Does the customer ask for a refund?")
      questions("customer.vip") shouldBe NoulQuestion("Is the customer a VIP?")
      // the teams' descriptions are their options' criteria
      questions("team") shouldBe ChoiceQuestion(
        scala.collection.immutable.ListMap(
          "Billing" -> Some(JsString("Payments, invoices, refunds")),
          "Sales" -> Some(JsString("Pricing, upgrades, new accounts")),
          "Technical" -> Some(JsString("Problems using the product"))
        ),
        Some(JsString("Which team should handle the ticket?"))
      )
      // a topic's question carries its own description, not every topic's
      questions("topics.[Payments]") shouldBe NoulQuestion(
        "Does 'Payments' apply? (Which topics does the ticket mention?)\n- Payments: Charges, refunds"
      )
      questions("topics.[Login]") shouldBe NoulQuestion(
        "Does 'Login' apply? (Which topics does the ticket mention?)"
      )
      questions("urgency") shouldBe a[ScoreQuestion]
    }

    "give the probabilities behind each field" in {
      val decision = await(new Stub(triageAnswers).decide[Triage]("A ticket"))

      decision.noul("refund").noul shouldBe 0.97
      decision.choice("team").probabilities("Billing") shouldBe 0.91
      decision.score("urgency").probabilities(3) shouldBe 0.6
      decision.noul("topics.[Payments]").noul shouldBe 0.88
      decision.model shouldBe "jev-1.13.0"
      decision.usage shouldBe Usage(Some(300), Some(40))
      decision.answers shouldBe triageAnswers
    }

    "read yes/no answers at the given threshold, and use the given model" in {
      val stub = new Stub(triageAnswers)

      val decision = await(stub.decide[Triage](JsString("A ticket"), "jev-preview", 0.2))

      decision.value.customer.vip shouldBe true
      decision.value.topics shouldBe Seq(Payments)
      stub.lastModel shouldBe Some("jev-preview")

      // the same over a text state
      await(stub.decide[Triage]("A ticket", "jev-preview", 0.2)).value shouldBe decision.value
      stub.lastState shouldBe Some(JsString("A ticket"))
    }

    "refuse a field no decision model answers, before calling it" in {
      val stub = new Stub(Map.empty)

      val failure = Await.result(stub.decide[Note]("A ticket").failed, 10.seconds)

      failure shouldBe an[IllegalArgumentException]
      failure.getMessage should include("note")
      stub.lastQuestions shouldBe empty
    }

    "use a schema of your own when one is in scope" in {
      implicit val noteSchema: JsonSchemaOf[Note] = JsonSchemaOf.instance(
        JsonSchema.Object(
          Seq("note" -> JsonSchema.String(Some("What is the mood?"), Seq("calm", "angry"))),
          required = Seq("note")
        )
      )
      val stub = new Stub(
        Map("note" -> ChoiceAnswer("angry", 0.9, Map("calm" -> 0.05, "angry" -> 0.95)))
      )

      await(stub.decide[Note]("I want my money back!")).value shouldBe Note("angry")
    }

    "fail clearly when the answers do not read back as the type" in {
      val stub = new Stub(Map("isUrgent" -> NoulAnswer(0.9)))

      val failure = Await.result(stub.decide[Urgent]("Now!").failed, 10.seconds)

      failure shouldBe a[TypeSafeScalaClientException]
      failure.getMessage should include("does not read back")
    }
  }
}
