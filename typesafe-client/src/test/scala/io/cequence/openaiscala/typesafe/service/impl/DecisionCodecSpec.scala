package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.openaiscala.typesafe.JsonFormats._
import io.cequence.openaiscala.typesafe.domain._
import io.cequence.openaiscala.typesafe.service.DecisionProviderSettings
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, JsObject, JsString, JsValue, Json}

import scala.collection.immutable.ListMap

/**
 * System One's questions and answers translated to and from OpenAI's Decisions API, and
 * written in text for ScaleDown.
 */
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
      state: play.api.libs.json.JsValue,
      model: String = "m"
    ) = {
      val questions = Seq[(String, Question)]("x" -> NoulQuestion("Is it red?"))
      DecisionCodec(provider, model).body(
        SystemOneRequest(state, model, questions.toMap),
        questions
      )
    }

    "lift a state's images into a top-level images array for Liquid and llama.cpp" in {
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

      // SubQ takes them as {mediaType, base64} objects
      val subq = bodyFor(DecisionProviderSettings.subq, state)
      (subq \ "state").get shouldBe Json.arr("What is it?", "[image 1]")
      (subq \ "images").get shouldBe Json.arr(
        Json.obj("mediaType" -> "image/png", "base64" -> "AAAA")
      )

      // Perplexity reads them where they are (TypeSafe's Jev, since 2026-10-14, as assets -
      // see its own cases below)
      bodyFor(DecisionProviderSettings.perplexity, state) shouldBe
        Json.toJson(SystemOneRequest(state, "m", Map("x" -> NoulQuestion("Is it red?"))))
      (bodyFor(DecisionProviderSettings.typeSafe, state) \ "assets").isDefined shouldBe true

      (bodyFor(DecisionProviderSettings.openAI, state) \ "input").isDefined shouldBe true
    }

    "write ScaleDown's text for ScaleDown and the scaledown/ models of OpenRouter and Vercel only" in {
      val state = Json.obj("ticket" -> "Refund me")
      val asText = JsString(Json.prettyPrint(state))

      (bodyFor(DecisionProviderSettings.scaleDown, state, "classify-1") \ "state").get shouldBe
        asText
      (bodyFor(
        DecisionProviderSettings.openRouter,
        state,
        DecisionModelId.openrouter_scaledown_classify_1
      ) \ "state").get shouldBe asText
      (bodyFor(
        DecisionProviderSettings.vercel,
        state,
        DecisionModelId.vercel_scaledown_classify_1
      ) \ "state").get shouldBe asText

      // the hosts' other models take any JSON
      (bodyFor(
        DecisionProviderSettings.openRouter,
        state,
        DecisionModelId.openrouter_jev_latest
      ) \ "state").get shouldBe state
      DecisionProviderSettings.openRouter.protocolFor(
        DecisionModelId.openrouter_liquid_d1
      ) shouldBe
        DecisionProtocol.SystemOne
    }
  }

  "The ScaleDown codec" should {

    def scaleDown(
      state: JsValue,
      questions: (String, Question)*
    ): JsObject = {
      val asked = if (questions.isEmpty) Seq("x" -> NoulQuestion("Is it red?")) else questions
      ScaleDownCodec
        .body(SystemOneRequest(state, "classify-1", asked.toMap), asked)
        .as[JsObject]
    }

    "send text as it is, its own state object as it is and any other JSON as pretty-printed text" in {
      (scaleDown(JsString("Refund me")) \ "state").get shouldBe JsString("Refund me")

      val own = Json.obj(
        "text" -> "See the scan.",
        "document" -> "JVBERi0xLjQ=",
        "document_mime_type" -> "application/pdf"
      )
      (scaleDown(own) \ "state").get shouldBe own
      (scaleDown(Json.obj("text" -> "Refund me")) \ "state").get shouldBe
        Json.obj("text" -> "Refund me")

      // the OpenAI adapter's state of instructions and a message
      val adapterState = Json.obj("instructions" -> "Be brief.", "message" -> "Refund me")
      (scaleDown(adapterState) \ "state").get shouldBe JsString(Json.prettyPrint(adapterState))
      // a chat message's texts, one per line
      (scaleDown(Json.arr("Hi.", "Refund me")) \ "state").get shouldBe JsString(
        "Hi.\nRefund me"
      )
      // a field of its own state that is no text, or another field, makes it JSON like any other
      for (other <- Seq(Json.obj("text" -> 5), Json.obj("text" -> "x", "plan" -> "pro")))
        (scaleDown(other) \ "state").get shouldBe JsString(Json.prettyPrint(other))
    }

    "lift one image into the state's document, leaving its marker in the text" in {
      (scaleDown(Json.arr("What is it?", image)) \ "state").get shouldBe Json.obj(
        "text" -> "What is it?\n[image 1]",
        "document" -> "AAAA",
        "document_mime_type" -> "image/png"
      )
      // a lone image, and one nested in an object
      (scaleDown(image) \ "state").get shouldBe Json.obj(
        "text" -> "[image 1]",
        "document" -> "AAAA",
        "document_mime_type" -> "image/png"
      )
      (scaleDown(Json.obj("photo" -> image)) \ "state" \ "text").as[String] shouldBe
        Json.prettyPrint(Json.obj("photo" -> "[image 1]"))
    }

    "refuse a second image before sending" in {
      the[IllegalArgumentException]
        .thrownBy(scaleDown(Json.arr(image, "and", image)))
        .getMessage should include("one image per request")
    }

    "write the questions in text - a noul's criteria appended, absent instructions empty" in {
      val questions = (scaleDown(
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
        "animal" -> NoulQuestion(
          None,
          Some(NoulCriteria(Some(JsString("Mentions an animal"))))
        ),
        "team" -> ChoiceQuestion(
          ListMap("billing" -> Some(JsString("Charges")), "other" -> None),
          None
        ),
        "level" -> ScoreQuestion(
          Seq(
            Json.obj("label" -> "mild", "description" -> "Barely noticeable"),
            Json.obj("label" -> "bad"),
            JsString("severe"),
            Json.arr("unusable", "broken")
          ),
          Some(Json.obj("ask" -> "How bad?"))
        )
      ) \ "questions").as[JsObject]

      (questions \ "spam").get shouldBe Json.obj(
        "type" -> "noul",
        "instructions" -> ("Is it spam?\nYes when: Unsolicited ads\nNo when: " +
          Json.prettyPrint(Json.obj("rule" -> "a reply")))
      )
      (questions \ "animal").get shouldBe Json.obj(
        "type" -> "noul",
        "instructions" -> "Yes when: Mentions an animal"
      )
      (questions \ "team").get shouldBe Json.obj(
        "type" -> "choice",
        "instructions" -> "",
        "criteria" -> Json.obj("billing" -> "Charges", "other" -> "")
      )
      (questions \ "level").get shouldBe Json.obj(
        "type" -> "score",
        "instructions" -> Json.prettyPrint(Json.obj("ask" -> "How bad?")),
        "criteria" -> Json.arr("mild: Barely noticeable", "bad", "severe", "unusable\nbroken")
      )
    }

    "read the answers in System One's format" in {
      // recorded through OpenRouter on 2026-10-13
      val response = ScaleDownCodec.response(
        Json.parse(
          """{"model":"scaledown/classify-1-20260928","answers":{"defect":{"type":"noul","noul":0.9481502334676174},"severity":{"type":"score","score":1.5389094068294393,"legend":{"0":"Cosmetic","1":"Inconvenient","2":"Product unusable"},"probabilities":{"0":0.033208659915832495,"1":0.39467327333889546,"2":0.572118066745272},"confidence":0.572118066745272}},"usage":{"input_tokens":714,"output_tokens":3,"cost":0.00002856}}"""
        ),
        Seq(
          "defect" -> NoulQuestion("Defect?"),
          "severity" -> ScoreQuestion(
            "How severe?",
            "Cosmetic",
            "Inconvenient",
            "Product unusable"
          )
        )
      )
      response.noul("defect").noul shouldBe 0.9481502334676174
      response.score("severity").score shouldBe 1.5389094068294393
      response.usage.input_tokens shouldBe Some(714)
    }
  }

  "TypeSafe's assets codec" should {

    val codec = DecisionCodec(DecisionProviderSettings.typeSafe, "jev-1.14.0")
    val red = DecisionImage.part("data:image/png;base64,UkVE")
    val blue = DecisionImage.part("data:image/jpeg;base64,QkxVRQ==")

    def bodyOf(
      state: JsValue,
      questions: (String, Question)*
    ): JsObject =
      codec
        .body(SystemOneRequest(state, "jev-1.14.0", questions.toMap), questions)
        .as[JsObject]

    "lift the images of the state and the questions into assets, one placeholder per image" in {
      val body = bodyOf(
        Json.obj("photo" -> red, "again" -> red),
        "same" -> NoulQuestion(
          Some(Json.arr("Is `photo` the same as this picture:", blue)),
          None
        ),
        "which" -> ChoiceQuestion(
          ListMap("red_one" -> Some(red), "blue_one" -> Some(blue), "none" -> None),
          Some(JsString("Which example matches `photo`?"))
        )
      )

      val assets = (body \ "assets").as[JsObject]
      assets.values.toSet shouldBe Set(
        Json.obj("type" -> "image", "format" -> "png", "data_base64" -> "UkVE"),
        Json.obj("type" -> "image", "format" -> "jpg", "data_base64" -> "QkxVRQ==")
      )
      val placeholder = assets.fields.map { case (key, asset) =>
        (asset \ "data_base64").as[String] -> key
      }.toMap
      val (redKey, blueKey) = (placeholder("UkVE"), placeholder("QkxVRQ=="))

      (body \ "state").get shouldBe Json.obj("photo" -> redKey, "again" -> redKey)
      (body \ "questions" \ "same" \ "instructions").get shouldBe
        Json.arr("Is `photo` the same as this picture:", blueKey)
      (body \ "questions" \ "which" \ "criteria").get shouldBe
        Json.obj("red_one" -> redKey, "blue_one" -> blueKey, "none" -> JsNull)

      // the same image, the same placeholder in every request
      (bodyOf(red, "x" -> NoulQuestion("Red?")) \ "state").get shouldBe JsString(redKey)
    }

    "opt in to images by header with images or a box question, and only then" in {
      val withImage = bodyOf(Json.obj("photo" -> red), "x" -> NoulQuestion("Red?"))
      codec.headers(withImage) shouldBe Seq("TypeSafe-Danger-Zone-Images" -> "true")

      val boxOnly = bodyOf(JsString("A kitchen."), "mug" -> BoxQuestion("the mug"))
      (boxOnly \ "assets").toOption shouldBe None
      codec.headers(boxOnly) shouldBe Seq("TypeSafe-Danger-Zone-Images" -> "true")

      val plain = bodyOf(JsString("text"), "x" -> NoulQuestion("Red?"))
      plain shouldBe Json.toJson(
        SystemOneRequest(JsString("text"), "jev-1.14.0", Map("x" -> NoulQuestion("Red?")))
      )
      codec.headers(plain) shouldBe Nil
    }

    "be TypeSafe's only - OpenRouter's Jev drops assets (live 2026-10-14), so it keeps its parts" in {
      val questions = Seq[(String, Question)]("x" -> NoulQuestion("Red?"))
      val openRouter =
        DecisionCodec(
          DecisionProviderSettings.openRouter,
          DecisionModelId.openrouter_jev_latest
        )
      val body =
        openRouter.body(SystemOneRequest(Json.arr(red), "m", questions.toMap), questions)
      (body \ "state").get shouldBe Json.arr(red)
      openRouter.headers(body) shouldBe Nil
    }
  }

  "A box question" should {

    "be refused where it cannot go - OpenAI's protocol and ScaleDown's" in {
      val box = Seq[(String, Question)]("mug" -> BoxQuestion("the mug in `photo`"))
      the[IllegalArgumentException]
        .thrownBy(
          OpenAIDecisionsCodec
            .body(SystemOneRequest(JsString("x"), "gpt-6-luna", box.toMap), box)
        )
        .getMessage should include("no box question")
      the[IllegalArgumentException]
        .thrownBy(
          ScaleDownCodec.body(SystemOneRequest(JsString("x"), "classify-1", box.toMap), box)
        )
        .getMessage should include("no box question")
    }
  }
}
