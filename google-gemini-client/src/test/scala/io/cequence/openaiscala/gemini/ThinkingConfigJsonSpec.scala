package io.cequence.openaiscala.gemini

import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.gemini.domain.ThinkingLevel
import io.cequence.openaiscala.gemini.domain.settings.ThinkingConfig
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class ThinkingConfigJsonSpec extends AnyWordSpec with Matchers {

  "ThinkingConfig JSON" should {

    "serialize MEDIUM thinkingLevel" in {
      val cfg = ThinkingConfig(
        includeThoughts = Some(false),
        thinkingBudget = None,
        thinkingLevel = Some(ThinkingLevel.MEDIUM)
      )
      val json = Json.toJson(cfg)
      (json \ "thinkingLevel").as[String] shouldBe "MEDIUM"
      (json \ "thinkingBudget").toOption shouldBe None
    }

    "round-trip all ThinkingLevel values including MEDIUM" in {
      ThinkingLevel.values.foreach { lvl =>
        val cfg = ThinkingConfig(thinkingLevel = Some(lvl))
        Json.fromJson[ThinkingConfig](Json.toJson(cfg)).get.thinkingLevel shouldBe Some(lvl)
      }
    }

    "serialize thinkingBudget path (Gemini 2.5 style)" in {
      val cfg = ThinkingConfig(
        includeThoughts = Some(false),
        thinkingBudget = Some(1024),
        thinkingLevel = None
      )
      val json = Json.toJson(cfg)
      (json \ "thinkingBudget").as[Int] shouldBe 1024
      (json \ "thinkingLevel").toOption shouldBe None
    }
  }
}
