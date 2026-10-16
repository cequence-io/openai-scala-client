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
    "gemini-pro-latest" -> Set(LOW, MEDIUM, HIGH),
    // measured 2026-10-13, the day it reached the Gemini API
    "gemini-4-argon" -> Set(LOW, MEDIUM, HIGH)
  )

  "GeminiThinking.mode" should {

    "use levels for Gemini 3.x and 4, the rolling aliases and nano-banana-pro" in {
      accepted.keys.foreach(model => withClue(model)(mode(model) shouldBe Levels))
      mode("models/gemini-3.8-flash") shouldBe Levels
      // the next Gemini 4 models too, without MINIMAL until one takes it
      mode("gemini-4-flash") shouldBe Levels
      level("gemini-4.1-argon", MINIMAL) shouldBe LOW
      // not a Gemini 4 - Gemma
      mode("gemma-4-31b-it") shouldBe Unsupported
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

  "GeminiThinking.requiresFunctionCallSignatures" should {

    "hold for the models that refused a function call sent back unsigned (2026-10-13)" in {
      Seq(
        "gemini-4-argon",
        "gemini-3.8-flash",
        "gemini-3.1-pro-preview",
        "gemini-flash-lite-latest",
        "models/gemini-4-argon"
      ).foreach(model => withClue(model)(requiresFunctionCallSignatures(model) shouldBe true))
      Seq("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite", "gemma-4-31b-it")
        .foreach(model =>
          withClue(model)(requiresFunctionCallSignatures(model) shouldBe false)
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
      level("gemini-4-argon", MINIMAL) shouldBe LOW
      level("gemini-4-argon", HIGH) shouldBe HIGH
      level("gemini-3.1-flash-image", LOW) shouldBe MINIMAL
      level("gemini-3.1-flash-image", MEDIUM) shouldBe HIGH
    }
  }
}
