package io.cequence.openaiscala.gemini

import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.gemini.domain.Part
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{JsObject, Json}

class PartJsonSpec extends AnyWordSpecLike with Matchers {

  "Part JSON codec" should {

    "round-trip a plain text part" in {
      val part: Part = Part.Text("hello")
      val json = Json.toJson(part)
      json shouldBe Json.obj("text" -> "hello")
      json.as[Part] shouldBe part
    }

    "read a thought-summary text part (thought flag + signature preserved)" in {
      val json = Json.obj(
        "text" -> "planning...",
        "thought" -> true,
        "thoughtSignature" -> "sig=="
      )

      val part = json.as[Part]
      part shouldBe Part.Text(
        "planning...",
        thought = Some(true),
        thoughtSignature = Some("sig==")
      )
      // and the flags survive a write (needed to echo signatures back in multi-turn)
      Json.toJson(part) shouldBe json
    }

    "not depend on field order when discriminating the part type" in {
      // auxiliary field first - previously this crashed the whole response parse
      val json = Json.obj("thought" -> true, "text" -> "still a text part")

      json.as[Part] shouldBe Part.Text("still a text part", thought = Some(true))
    }

    "attach an enclosing thoughtSignature to a functionCall part and write it back out" in {
      val json = Json.obj(
        "functionCall" -> Json
          .obj("name" -> "get_weather", "args" -> Json.obj("city" -> "Oslo")),
        "thoughtSignature" -> "sig=="
      )

      val part = json.as[Part]
      part match {
        case fc: Part.FunctionCall =>
          fc.name shouldBe "get_weather"
          fc.thoughtSignature shouldBe Some("sig==")
        case other => fail(s"Expected FunctionCall, got $other")
      }

      val written = Json.toJson(part).as[JsObject]
      (written \ "thoughtSignature").as[String] shouldBe "sig=="
      // the signature must NOT leak inside the functionCall object itself
      (written \ "functionCall" \ "thoughtSignature").toOption shouldBe None
    }

    "carry an unrecognized part type as Part.Unknown instead of failing the parse" in {
      val json = Json.obj(
        "someFuturePartType" -> Json.obj("payload" -> 42),
        "thoughtSignature" -> "sig=="
      )

      val part = json.as[Part]
      part shouldBe Part.Unknown(json.as[JsObject])
      // written back to the wire verbatim
      Json.toJson(part) shouldBe json
    }
  }
}
