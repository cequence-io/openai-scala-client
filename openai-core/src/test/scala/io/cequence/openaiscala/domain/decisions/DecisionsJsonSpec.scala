package io.cequence.openaiscala.domain.decisions

import io.cequence.openaiscala.domain.decisions.JsonFormats._
import io.cequence.openaiscala.domain.responsesapi.{
  InputTokensDetails,
  OutputTokensDetails,
  UsageInfo
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

/**
 * The Decisions API's JSON: request bodies written, responses (recorded live 2026-10-07) read.
 */
class DecisionsJsonSpec extends AnyWordSpec with Matchers {

  "createDecisionBody" should {

    "write a text input and the three question types" in {
      createDecisionBody(
        DecisionInput.Text("I was charged twice."),
        Seq(
          DecisionQuestion.Predicate("Does the customer ask for a refund?", Some("refund")),
          DecisionQuestion.Choice(
            "Which team?",
            Seq(
              DecisionChoice("billing", "Payments."),
              DecisionChoice("sales"),
              DecisionChoice(true)
            ),
            Some("team")
          ),
          DecisionQuestion.Score(
            "How urgent?",
            Seq(DecisionLevel("low", Some("It can wait.")), DecisionLevel("high"))
          )
        ),
        CreateDecisionSettings(safetyIdentifier = Some("user-1"))
      ) shouldBe Json.parse(
        """{
          |  "model": "gpt-6-luna",
          |  "input": "I was charged twice.",
          |  "questions": [
          |    {"type": "predicate", "instructions": "Does the customer ask for a refund?", "name": "refund"},
          |    {"type": "choice", "instructions": "Which team?", "name": "team", "choices": [
          |      {"value": "billing", "description": "Payments."}, {"value": "sales"}, {"value": true}
          |    ]},
          |    {"type": "score", "instructions": "How urgent?", "levels": [
          |      {"label": "low", "description": "It can wait."}, {"label": "high"}
          |    ]}
          |  ],
          |  "safety_identifier": "user-1"
          |}""".stripMargin
      )
    }

    "write user messages of text and inline images" in {
      (createDecisionBody(
        DecisionInput.of(
          DecisionContent.InputText("Inspect the product."),
          DecisionContent
            .InputImage("data:image/png;base64,AAAA", Some(DecisionImageDetail.high))
        ),
        Seq(DecisionQuestion.Predicate("Is it damaged?")),
        CreateDecisionSettings()
      ) \ "input").get shouldBe Json.parse(
        """[{"role": "user", "content": [
          |  {"type": "input_text", "text": "Inspect the product."},
          |  {"type": "input_image", "image_url": "data:image/png;base64,AAAA", "detail": "high"}
          |]}]""".stripMargin
      )
    }
  }

  "A decision" should {

    "read every answer type, the usage and the model" in {
      val decision = Json
        .parse(
          """{"model":"gpt-6-luna","answers":[
            |{"type":"predicate","name":"refund","probability":1.0},
            |{"type":"choice","name":"team","choice":"billing","probabilities":[{"value":"billing","probability":0.95},{"value":"technical","probability":0.05}],"confidence":0.93},
            |{"type":"score","name":"urgency","score":1.26,"probabilities":[{"value":0,"label":"Low","probability":0.01},{"value":1,"label":"Medium","probability":0.72},{"value":2,"label":"High","probability":0.27}],"confidence":0.58},
            |{"type":"choice","name":"is_refund","choice":true,"probabilities":[{"value":true,"probability":1.0},{"value":false,"probability":0.0}],"confidence":1.0},
            |{"type":"refusal","name":"works"},
            |{"type":"predicate","name":null,"probability":0.61}
            |],"usage":{"input_tokens":405,"input_tokens_details":{"cached_tokens":0,"cache_write_tokens":0},"output_tokens":0,"output_tokens_details":{"reasoning_tokens":0},"total_tokens":405}}""".stripMargin
        )
        .as[Decision]

      decision.model shouldBe "gpt-6-luna"
      decision.answers shouldBe Seq(
        DecisionAnswer.Predicate(Some("refund"), 1.0),
        DecisionAnswer.Choice(
          Some("team"),
          DecisionValue.Text("billing"),
          Seq(
            ChoiceProbability(DecisionValue.Text("billing"), 0.95),
            ChoiceProbability(DecisionValue.Text("technical"), 0.05)
          ),
          0.93
        ),
        DecisionAnswer.Score(
          Some("urgency"),
          1.26,
          Seq(
            LevelProbability(0, "Low", 0.01),
            LevelProbability(1, "Medium", 0.72),
            LevelProbability(2, "High", 0.27)
          ),
          0.58
        ),
        DecisionAnswer.Choice(
          Some("is_refund"),
          DecisionValue.Bool(true),
          Seq(
            ChoiceProbability(DecisionValue.Bool(true), 1.0),
            ChoiceProbability(DecisionValue.Bool(false), 0.0)
          ),
          1.0
        ),
        DecisionAnswer.Refusal(Some("works")),
        DecisionAnswer.Predicate(None, 0.61)
      )
      decision.usage shouldBe Some(
        UsageInfo(405, Some(InputTokensDetails(Some(0))), 0, Some(OutputTokensDetails(0)), 405)
      )
      decision.answer("works") shouldBe Some(DecisionAnswer.Refusal(Some("works")))
      decision.answer("missing") shouldBe None
    }

    "keep an answer of an unknown type as it came" in {
      val raw = Json.obj("type" -> "bounding_box", "name" -> "damage", "box" -> Json.arr(1, 2))

      Json
        .obj("model" -> "gpt-6-luna", "answers" -> Json.arr(raw))
        .as[Decision]
        .answers shouldBe
        Seq(DecisionAnswer.Unknown(Some("damage"), "bounding_box", raw: JsObject))
    }
  }
}
