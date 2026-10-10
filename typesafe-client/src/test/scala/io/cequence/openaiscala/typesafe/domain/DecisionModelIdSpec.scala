package io.cequence.openaiscala.typesafe.domain

import io.cequence.openaiscala.domain.NonOpenAIModelId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.nowarn

class DecisionModelIdSpec extends AnyWordSpec with Matchers {

  "DecisionModelId" should {

    "mirror the decision-model constants of NonOpenAIModelId" in {
      DecisionModelId.jev_latest shouldBe NonOpenAIModelId.jev_latest
      DecisionModelId.liquid_d1 shouldBe NonOpenAIModelId.liquid_d1
      DecisionModelId.pplx_decider_v1_1_27b shouldBe NonOpenAIModelId.pplx_decider_v1_1_27b
      DecisionModelId.microsoft_decision_1 shouldBe NonOpenAIModelId.microsoft_decision_1
      DecisionModelId.openrouter_clef_omni shouldBe NonOpenAIModelId.openrouter_clef_omni
    }

    "keep its 1.4.0 name as a deprecated alias of the same object" in {
      val alias: DecisionModelId.type = TypeSafeModelId: @nowarn("cat=deprecation")
      (alias eq DecisionModelId) shouldBe true
      alias.jev_latest shouldBe DecisionModelId.jev_latest
    }
  }
}
