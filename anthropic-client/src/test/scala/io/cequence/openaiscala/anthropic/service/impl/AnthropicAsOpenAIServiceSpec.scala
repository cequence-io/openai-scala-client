package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{AssistantToolMessage, FunctionCallSpec}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AnthropicAsOpenAIServiceSpec extends AnyWordSpec with Matchers {

  // a model on the LEGACY thinking path: no output_config.effort support, budget_tokens ok
  private val legacyThinkingModel = "claude-3-7-sonnet-20250219"

  private val defaultMaxTokens = 2048

  "toAnthropicSettings (thinking budget vs max_tokens)" should {

    "raise the default max_tokens above an explicit thinking budget (legacy path)" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = legacyThinkingModel)
          .setAnthropicThinkingBudgetTokens(4096)
      )

      out.thinking.flatMap(_.budget_tokens) shouldBe Some(4096)
      // budget + default response headroom
      out.max_tokens shouldBe 4096 + defaultMaxTokens
    }

    "raise the default max_tokens above a reasoning_effort-mapped budget (legacy path)" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = legacyThinkingModel,
          reasoning_effort = Some(ReasoningEffort.medium) // -> 4096 via config mapping
        )
      )

      out.thinking.flatMap(_.budget_tokens) shouldBe Some(4096)
      out.max_tokens shouldBe 4096 + defaultMaxTokens
    }

    "keep a caller-chosen max_tokens that already exceeds the budget" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = legacyThinkingModel,
          max_tokens = Some(50000)
        ).setAnthropicThinkingBudgetTokens(4096)
      )

      out.max_tokens shouldBe 50000
    }

    "raise a caller-chosen max_tokens at or below the budget" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = legacyThinkingModel,
          max_tokens = Some(8192)
        ).setAnthropicThinkingBudgetTokens(16384)
      )

      out.max_tokens shouldBe 16384 + defaultMaxTokens
    }

    "use the model's real output cap - not the flat default - on the adaptive/output-effort path (no budget_tokens)" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_opus_4_8,
          reasoning_effort = Some(ReasoningEffort.high)
        )
      )

      out.thinking.flatMap(_.budget_tokens) shouldBe None
      out.output_config.flatMap(_.effort) should not be empty
      // claude-opus-4-8's real max output (128k), NOT the flat 2048 default - budget_tokens
      // is never involved for an adaptive-thinking model, so the flat-default bug could not
      // have been caught by the budget-vs-max_tokens fix above
      out.max_tokens shouldBe 128000
    }

    "not touch max_tokens when thinking is off, for a model without a known output cap" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = legacyThinkingModel)
      )

      out.thinking shouldBe None
      out.max_tokens shouldBe defaultMaxTokens
    }
  }

  "toAnthropicMessages" should {

    "reject malformed assistant tool arguments" in {
      val exception = intercept[OpenAIScalaClientException] {
        toAnthropicMessages(
          Seq(
            AssistantToolMessage(
              tool_calls = Seq("call-1" -> FunctionCallSpec("lookup", "not json"))
            )
          ),
          CreateChatCompletionSettings(model = legacyThinkingModel)
        )
      }

      exception.getMessage should include("call-1")
      exception.getMessage should include("lookup")
    }
  }

  "toAnthropicSettings (default max_tokens without any thinking - reported production bug)" should {

    "use claude-sonnet-4-6's real 128k output cap when max_tokens and reasoning_effort are both unset" in {
      // exact reproduction: an unspecified max_tokens (the OpenAI adapter sends this whenever
      // the caller doesn't set one) used to fall back to a flat 2048, silently truncating
      // large completions (live-verified: an 85-entity JSON extraction cut off mid-JSON)
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = NonOpenAIModelId.claude_sonnet_4_6)
      )

      out.thinking shouldBe None
      out.max_tokens shouldBe 128000
    }

    "match a Bedrock-prefixed model id the same way as the bare model id" in {
      // the exact (gateway-prefixed) model string from the production report
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = "anthropic_bedrock-anthropic.claude-sonnet-4-6")
      )

      out.max_tokens shouldBe 128000
    }

    "keep the flat default for a model with no freshly-verified output cap" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = legacyThinkingModel)
      )

      out.max_tokens shouldBe defaultMaxTokens
    }

    "still honor a caller-supplied max_tokens over the model default" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_sonnet_4_6,
          max_tokens = Some(500)
        )
      )

      out.max_tokens shouldBe 500
    }
  }
}
