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
      DecisionModelId.pplx_decider_flash_v1_2 shouldBe NonOpenAIModelId.pplx_decider_flash_v1_2
      DecisionModelId.openrouter_pplx_decider_v1_2 shouldBe
        NonOpenAIModelId.openrouter_pplx_decider_v1_2
      DecisionModelId.microsoft_decision_1 shouldBe NonOpenAIModelId.microsoft_decision_1
      DecisionModelId.openrouter_clef_omni shouldBe NonOpenAIModelId.openrouter_clef_omni
      DecisionModelId.vercel_laya_free shouldBe NonOpenAIModelId.vercel_laya_free
      // added 2026-10-29
      DecisionModelId.liquid_d1_1 shouldBe "d1.1"
      DecisionModelId.subq_snap_1_1 shouldBe "subq-snap-1.1"
      DecisionModelId.openrouter_baseten_mammoth_1 shouldBe "baseten/mammoth-1"
      DecisionModelId.vercel_baseten_elephant_1 shouldBe "baseten/elephant-1"
      DecisionModelId.openrouter_drex_max shouldBe "nace-ai/drex-max"
      // added 2026-11-04
      DecisionModelId.sarvam_prism shouldBe "sarvam-prism"
      // added 2026-11-05
      DecisionModelId.bespoke_nimble_latest shouldBe "nimble-latest"
      DecisionModelId.bespoke_nimble_v4_max shouldBe "nimble-v4-max"
    }

    "keep its 1.4.0 name as a deprecated alias of the same object" in {
      val alias: DecisionModelId.type = TypeSafeModelId: @nowarn("cat=deprecation")
      (alias eq DecisionModelId) shouldBe true
      alias.jev_latest shouldBe DecisionModelId.jev_latest
    }
  }
}
