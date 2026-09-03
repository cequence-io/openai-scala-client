package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.anthropic.domain.settings.{OutputEffort, ThinkingSettings}
import io.cequence.openaiscala.anthropic.domain.tools.ToolChoice
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

  "Claude Fable 5.1" should {

    "map reasoning_effort=xhigh to adaptive thinking + OutputEffort.xhigh and drop temperature/top_p" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_fable_5_1,
          reasoning_effort = Some(ReasoningEffort.xhigh),
          temperature = Some(0.2),
          top_p = Some(0.9)
        )
      )

      out.thinking shouldBe Some(ThinkingSettings.adaptive)
      out.output_config.flatMap(_.effort) shouldBe Some(OutputEffort.xhigh)
      out.temperature shouldBe None
      out.top_p shouldBe None
    }

    "ignore an explicit thinking budget and use adaptive thinking with no output_config effort" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = NonOpenAIModelId.claude_fable_5_1)
          .setAnthropicThinkingBudgetTokens(4096)
      )

      out.thinking shouldBe Some(ThinkingSettings.adaptive)
      out.thinking.flatMap(_.budget_tokens) shouldBe None
      out.output_config.flatMap(_.effort) shouldBe None
    }

    "use the model's 128k real output cap when max_tokens is unset (bare and Bedrock-prefixed ids)" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = NonOpenAIModelId.claude_fable_5_1)
      )
      out.max_tokens shouldBe 128000

      val bedrockOut = toAnthropicSettings(
        CreateChatCompletionSettings(model = "eu." + NonOpenAIModelId.bedrock_claude_fable_5_1)
      )
      bedrockOut.max_tokens shouldBe 128000
    }

    "downgrade a forced tool_choice to auto plus a system instruction" in {
      val (toolChoice, extraSystemMessages) =
        toAnthropicToolChoice(
          NonOpenAIModelId.claude_fable_5_1,
          Some("get_weather"),
          Some(true)
        )

      toolChoice shouldBe ToolChoice.Auto(Some(true))
      extraSystemMessages should have size 1
      extraSystemMessages.head.content should include("get_weather")
    }

    "downgrade a forced tool_choice on a Bedrock-prefixed Fable 5.1 id too" in {
      val (toolChoice, extraSystemMessages) =
        toAnthropicToolChoice(
          "us." + NonOpenAIModelId.bedrock_claude_fable_5_1,
          Some("get_weather"),
          None
        )

      toolChoice shouldBe ToolChoice.Auto(None)
      extraSystemMessages should have size 1
      extraSystemMessages.head.content should include("get_weather")
    }

    "still support forced tool_choice on Claude Fable 5 (predecessor)" in {
      val (toolChoice, extraSystemMessages) =
        toAnthropicToolChoice(NonOpenAIModelId.claude_fable_5, Some("get_weather"), None)

      toolChoice shouldBe ToolChoice.Tool("get_weather", None)
      extraSystemMessages shouldBe empty
    }

    "leave tool_choice as auto with no extra system messages when no tool is forced" in {
      val (toolChoice, extraSystemMessages) =
        toAnthropicToolChoice(NonOpenAIModelId.claude_fable_5_1, None, None)

      toolChoice shouldBe ToolChoice.Auto(None)
      extraSystemMessages shouldBe empty
    }
  }

  "Claude Opus 5" should {

    "map reasoning_effort=xhigh to adaptive thinking + OutputEffort.xhigh and drop temperature/top_p" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(
          model = NonOpenAIModelId.claude_opus_5,
          reasoning_effort = Some(ReasoningEffort.xhigh),
          temperature = Some(0.2),
          top_p = Some(0.9)
        )
      )

      out.thinking shouldBe Some(ThinkingSettings.adaptive)
      out.output_config.flatMap(_.effort) shouldBe Some(OutputEffort.xhigh)
      out.temperature shouldBe None
      out.top_p shouldBe None
    }

    "ignore an explicit thinking budget and use adaptive thinking with no output_config effort" in {
      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = NonOpenAIModelId.claude_opus_5)
          .setAnthropicThinkingBudgetTokens(4096)
      )

      out.thinking shouldBe Some(ThinkingSettings.adaptive)
      out.thinking.flatMap(_.budget_tokens) shouldBe None
      out.output_config.flatMap(_.effort) shouldBe None
    }

    "still support forced tool_choice and use the model's 128k real output cap when max_tokens is unset" in {
      val (toolChoice, extraSystemMessages) =
        toAnthropicToolChoice(NonOpenAIModelId.claude_opus_5, Some("get_weather"), None)

      toolChoice shouldBe ToolChoice.Tool("get_weather", None)
      extraSystemMessages shouldBe empty

      val out = toAnthropicSettings(
        CreateChatCompletionSettings(model = NonOpenAIModelId.claude_opus_5)
      )
      out.max_tokens shouldBe 128000
    }
  }
}
