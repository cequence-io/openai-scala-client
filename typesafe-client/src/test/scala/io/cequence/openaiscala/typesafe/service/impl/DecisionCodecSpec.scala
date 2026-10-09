package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.DecisionProviderSettings
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsString, Json}

/** System One's questions and answers translated to and from OpenAI's Decisions API. */
class DecisionCodecSpec extends AnyWordSpec with Matchers {

  private def body(
    state: play.api.libs.json.JsValue,
    questions: (String, Question)*
  ): JsObject =
    OpenAIDecisionsCodec
      .body(SystemOneRequest(state, "gpt-6-luna", questions.toMap), questions)
      .as[JsObject]

  private val image = DecisionImage.part("data:image/png;base64,AAAA")

  "The OpenAI codec" should {

    "send a chat message's text and image parts as one message of those parts" in {
      (body(
        Json.arr("What is it?", image),
        "x" -> NoulQuestion("Is it red?")
      ) \ "input").get shouldBe
        Json.arr(
          Json.obj(
            "role" -> "user",
            "content" -> Json.arr(
              Json.obj("type" -> "input_text", "text" -> "What is it?"),
              Json.obj("type" -> "input_image", "image_url" -> "data:image/png;base64,AAAA")
            )
          )
        )
    }

    "send JSON without images as its text" in {
      (body(
        Json.obj("ticket" -> "Refund me"),
        "x" -> NoulQuestion("Is it a refund?")
      ) \ "input").get shouldBe JsString("""{"ticket":"Refund me"}""")
    }

    "send a part that is no image - `image_url` without a URL - as text, like everywhere else" in {
      // the codec's image predicate is DecisionImage's (type AND a string URL): such a part
      // used to become an empty `input_image`
      (body(
        Json.arr("What is it?", Json.obj("type" -> "image_url", "image_url" -> Json.obj())),
        "x" -> NoulQuestion("Is it red?")
      ) \ "input").get shouldBe
        JsString("""["What is it?",{"type":"image_url","image_url":{}}]""")
    }

    "append a noul's criteria to its instructions, and read structured score levels" in {
      val questions = (body(
        JsString("text"),
        "spam" -> NoulQuestion(
          Some(JsString("Is it spam?")),
          Some(
            NoulCriteria(
              Some(JsString("Unsolicited ads")),
              Some(Json.obj("rule" -> "a reply"))
            )
          )
        ),
        "level" -> ScoreQuestion(
          Seq(
            Json.obj("label" -> "mild", "description" -> "Barely noticeable"),
            Json.arr("severe", "unusable")
          ),
          Some(JsString("How bad?"))
        )
      ) \ "questions").as[Seq[JsObject]]

      (questions.head \ "instructions").as[String] shouldBe
        "Is it spam?\nYes when: Unsolicited ads\nNo when: {\"rule\":\"a reply\"}"
      (questions(1) \ "levels").get shouldBe Json.arr(
        Json.obj("label" -> "mild", "description" -> "Barely noticeable"),
        Json.obj("label" -> """["severe","unusable"]""")
      )
    }

    "read an answer without a name by its position" in {
      val asked = Seq[(String, Question)](
        "first" -> NoulQuestion("One?"),
        "second" -> NoulQuestion("Two?")
      )

      OpenAIDecisionsCodec
        .response(
          Json.parse(
            """{"model":"gpt-6-luna","answers":[{"type":"predicate","name":null,"probability":0.2},{"type":"predicate","name":"second","probability":0.7}]}"""
          ),
          asked
        )
        .answers shouldBe Map("first" -> NoulAnswer(0.2), "second" -> NoulAnswer(0.7))
    }
  }

  "The codec of a provider" should {

    def bodyFor(
      provider: DecisionProvider,
      state: play.api.libs.json.JsValue
    ) = {
      val questions = Seq[(String, Question)]("x" -> NoulQuestion("Is it red?"))
      DecisionCodec(provider).body(SystemOneRequest(state, "m", questions.toMap), questions)
    }

    "lift a state's images into a top-level images array for Liquid and llama.cpp only" in {
      val state = Json.arr("What is it?", image)

      for (
        provider <- Seq(
          DecisionProviderSettings.liquid,
          DecisionProviderSettings.llamaCpp
        )
      ) {
        val body = bodyFor(provider, state)
        (body \ "state").get shouldBe Json.arr("What is it?", "[image 1]")
        (body \ "images").get shouldBe Json.arr("data:image/png;base64,AAAA")
      }

      // Perplexity reads them where they are, and TypeSafe's Jev not at all
      for (
        provider <- Seq(DecisionProviderSettings.perplexity, DecisionProviderSettings.typeSafe)
      )
        bodyFor(provider, state) shouldBe
          Json.toJson(SystemOneRequest(state, "m", Map("x" -> NoulQuestion("Is it red?"))))

      (bodyFor(DecisionProviderSettings.openAI, state) \ "input").isDefined shouldBe true
    }
  }
}
