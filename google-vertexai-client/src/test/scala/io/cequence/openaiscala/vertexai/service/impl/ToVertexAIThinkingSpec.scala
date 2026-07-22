package io.cequence.openaiscala.vertexai.service.impl

import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ToVertexAIThinkingSpec extends AnyWordSpec with Matchers {

  "toVertexAI (reasoning_effort -> thinking budget)" should {

    "map reasoning_effort to a thinking budget on Gemini 2.5" in {
      val config = toVertexAI(
        CreateChatCompletionSettings(
          model = "gemini-2.5-flash",
          reasoning_effort = Some(ReasoningEffort.medium) // -> 4096 via config mapping
        )
      )

      config.hasThinkingConfig shouldBe true
      config.getThinkingConfig.getThinkingBudget shouldBe 4096
    }

    "clamp the budget to the non-Pro maximum on Gemini 2.5 Flash" in {
      val config = toVertexAI(
        CreateChatCompletionSettings(
          model = "gemini-2.5-flash",
          reasoning_effort = Some(ReasoningEffort.max) // 32768 -> clamped to 24576
        )
      )

      config.getThinkingConfig.getThinkingBudget shouldBe 24576
    }

    "raise a sub-floor budget to 512 on Gemini 2.5 Flash-Lite" in {
      val config = toVertexAI(
        CreateChatCompletionSettings(
          model = "gemini-2.5-flash-lite",
          reasoning_effort = Some(ReasoningEffort.minimal) // 256 -> clamped to the 512 floor
        )
      )

      config.getThinkingConfig.getThinkingBudget shouldBe 512
    }

    "keep an in-range budget on Gemini 2.5 Flash-Lite" in {
      val config = toVertexAI(
        CreateChatCompletionSettings(
          model = "gemini-2.5-flash-lite",
          reasoning_effort = Some(ReasoningEffort.medium)
        )
      )

      config.getThinkingConfig.getThinkingBudget shouldBe 4096
    }

    "keep the full budget on Gemini 2.5 Pro" in {
      val config = toVertexAI(
        CreateChatCompletionSettings(
          model = "gemini-2.5-pro",
          reasoning_effort = Some(ReasoningEffort.max)
        )
      )

      config.getThinkingConfig.getThinkingBudget shouldBe 32768
    }

    "resolve the bare model id from a full publisher resource name" in {
      val config = toVertexAI(
        CreateChatCompletionSettings(
          model = "publishers/google/models/gemini-2.5-flash",
          reasoning_effort = Some(ReasoningEffort.medium)
        )
      )

      config.getThinkingConfig.getThinkingBudget shouldBe 4096
    }

    "skip thinking config on Gemini 3.x (proto SDK cannot express thinkingLevel)" in {
      val config = toVertexAI(
        CreateChatCompletionSettings(
          model = "gemini-3-pro-preview",
          reasoning_effort = Some(ReasoningEffort.high)
        )
      )

      config.hasThinkingConfig shouldBe false
    }

    "skip thinking config when reasoning_effort is not set" in {
      val config = toVertexAI(
        CreateChatCompletionSettings(model = "gemini-2.5-flash")
      )

      config.hasThinkingConfig shouldBe false
    }
  }
}
