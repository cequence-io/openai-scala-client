package io.cequence.openaiscala.gemini.service.impl

import io.cequence.openaiscala.gemini.domain.ThinkingLevel
import io.cequence.openaiscala.gemini.domain.ThinkingLevel._
import io.cequence.openaiscala.gemini.service.impl.GeminiThinking._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * [[GeminiThinking]] against the thinking matrix measured on the live Gemini API (2026-09-26):
 * which models take levels, budgets or nothing, and which levels each accepts.
 */
class GeminiThinkingSpec extends AnyWordSpec with Matchers {

  // model -> the levels the live API accepted
  private val accepted: Map[String, Set[ThinkingLevel]] = Map(
    "gemini-3-flash-preview" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "gemini-3.1-flash-lite" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "gemini-3.5-flash" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "gemini-3.5-flash-lite" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "gemini-3.6-flash" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "gemini-3.7-flash" -> Set(LOW, MEDIUM, HIGH),
    "gemini-3.8-flash" -> Set(LOW, MEDIUM, HIGH),
    "gemini-3.1-pro-preview" -> Set(LOW, MEDIUM, HIGH),
    "gemini-3.1-pro-preview-customtools" -> Set(LOW, MEDIUM, HIGH),
    "gemini-3-pro-image" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "gemini-3-pro-image-preview" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "nano-banana-pro-preview" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "gemini-3.1-flash-image" -> Set(MINIMAL, HIGH),
    "gemini-3.1-flash-image-preview" -> Set(MINIMAL, HIGH),
    "gemini-3.1-flash-lite-image" -> Set(MINIMAL, HIGH),
    "gemini-flash-latest" -> Set(LOW, MEDIUM, HIGH),
    "gemini-flash-lite-latest" -> Set(MINIMAL, LOW, MEDIUM, HIGH),
    "gemini-pro-latest" -> Set(LOW, MEDIUM, HIGH)
  )

  "GeminiThinking.mode" should {

    "use levels for Gemini 3.x, the rolling aliases and nano-banana-pro" in {
      accepted.keys.foreach(model => withClue(model)(mode(model) shouldBe Levels))
      mode("models/gemini-3.8-flash") shouldBe Levels
    }

    "use a budget for Gemini 2.5, and nothing for 2.5 Flash Image or older models" in {
      Seq(
        "gemini-2.5-flash",
        "gemini-2.5-pro",
        "gemini-2.5-flash-lite",
        "models/gemini-2.5-pro"
      ).foreach(model => withClue(model)(mode(model) shouldBe Budget))
      Seq("gemini-2.5-flash-image", "gemma-4-31b-it", "gemini-omni-1.1-flash").foreach(model =>
        withClue(model)(mode(model) shouldBe Unsupported)
      )
    }
  }

  "GeminiThinking.level" should {

    "only ever pick a level the model accepted live, for every requested level" in {
      for {
        (model, levels) <- accepted
        requested <- Seq(MINIMAL, LOW, MEDIUM, HIGH)
      } withClue(s"$model / $requested: ") {
        levels should contain(level(model, requested))
      }
    }

    "keep the requested level where it is accepted" in {
      level("gemini-3.5-flash", MINIMAL) shouldBe MINIMAL
      level("gemini-3.8-flash", MEDIUM) shouldBe MEDIUM
      level("gemini-3.1-pro-preview", MINIMAL) shouldBe LOW
      level("gemini-3.1-flash-image", LOW) shouldBe MINIMAL
      level("gemini-3.1-flash-image", MEDIUM) shouldBe HIGH
    }
  }
}
