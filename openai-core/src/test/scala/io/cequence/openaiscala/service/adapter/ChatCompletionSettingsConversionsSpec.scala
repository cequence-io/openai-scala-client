package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, ReasoningEffort}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ChatCompletionSettingsConversionsSpec extends AnyWordSpec with Matchers {

  "ChatCompletionSettingsConversions.chatLatest" should {

    "convert max_tokens to max_completion_tokens, force temperature to 1, and drop an unsupported reasoning_effort/logprobs" in {
      val settings = CreateChatCompletionSettings(
        model = ModelId.chat_latest,
        max_tokens = Some(100),
        temperature = Some(0.3),
        reasoning_effort = Some(ReasoningEffort.low),
        logprobs = Some(true)
      )

      val out = ChatCompletionSettingsConversions.chatLatest(settings)

      out.max_tokens shouldBe None
      out.extra_params.get("max_completion_tokens") shouldBe Some(100)
      out.temperature shouldBe Some(1d)
      out.reasoning_effort shouldBe None
      out.logprobs shouldBe None
    }

    "keep reasoning_effort when it is already 'medium'" in {
      val settings = CreateChatCompletionSettings(
        model = ModelId.chat_latest,
        reasoning_effort = Some(ReasoningEffort.medium)
      )

      val out = ChatCompletionSettingsConversions.chatLatest(settings)

      out.reasoning_effort shouldBe Some(ReasoningEffort.medium)
    }
  }

  "ChatCompletionSettingsConversions.gpt6" should {

    "restrict sampling params, move max_tokens, and downgrade reasoning_effort 'max' to 'xhigh'" in {
      val settings = CreateChatCompletionSettings(
        model = ModelId.gpt_6_astra,
        max_tokens = Some(100),
        temperature = Some(0.3),
        top_p = Some(0.5),
        presence_penalty = Some(0.5),
        frequency_penalty = Some(0.5),
        reasoning_effort = Some(ReasoningEffort.max),
        logprobs = Some(true)
      )

      val out = ChatCompletionSettingsConversions.gpt6(settings)

      out.max_tokens shouldBe None
      out.extra_params.get("max_completion_tokens") shouldBe Some(100)
      out.temperature shouldBe Some(1d)
      out.top_p shouldBe Some(1d)
      out.presence_penalty shouldBe Some(0d)
      out.frequency_penalty shouldBe Some(0d)
      out.reasoning_effort shouldBe Some(ReasoningEffort.xhigh)
      out.logprobs shouldBe None
    }

    "downgrade reasoning_effort 'minimal' to 'low' and keep 'high'" in {
      val minimal = CreateChatCompletionSettings(
        model = ModelId.gpt_6_astra,
        reasoning_effort = Some(ReasoningEffort.minimal)
      )
      val high = minimal.copy(reasoning_effort = Some(ReasoningEffort.high))

      ChatCompletionSettingsConversions.gpt6(minimal).reasoning_effort shouldBe Some(
        ReasoningEffort.low
      )
      ChatCompletionSettingsConversions.gpt6(high).reasoning_effort shouldBe Some(
        ReasoningEffort.high
      )
    }

    "downgrade reasoning_effort 'none' to 'low' (rejected by gpt-6-astra, unlike gpt-5.6)" in {
      val none = CreateChatCompletionSettings(
        model = ModelId.gpt_6_astra,
        reasoning_effort = Some(ReasoningEffort.none)
      )

      ChatCompletionSettingsConversions.gpt6(none).reasoning_effort shouldBe Some(
        ReasoningEffort.low
      )
      ChatCompletionSettingsConversions.gpt5_6(none).reasoning_effort shouldBe Some(
        ReasoningEffort.none
      )
    }
  }

  "ChatCompletionSettingsConversions chat-tool conversions" should {

    "drop an explicit reasoning_effort for gpt-5.5 and keep everything else" in {
      val settings = CreateChatCompletionSettings(
        model = ModelId.gpt_5_5,
        max_tokens = Some(100),
        reasoning_effort = Some(ReasoningEffort.high)
      )

      val out = ChatCompletionSettingsConversions.gpt5_5ChatTools(settings)

      out.reasoning_effort shouldBe None
      out.max_tokens shouldBe Some(100)
      ChatCompletionSettingsConversions.gpt5_5ChatTools(
        settings.copy(reasoning_effort = None)
      ) shouldBe settings.copy(reasoning_effort = None)
    }

    "force reasoning_effort 'none' for gpt-5.6, also when it is not set" in {
      val unset = CreateChatCompletionSettings(model = ModelId.gpt_5_6_sol)
      val high = unset.copy(reasoning_effort = Some(ReasoningEffort.high))
      val none = unset.copy(reasoning_effort = Some(ReasoningEffort.none))

      ChatCompletionSettingsConversions.gpt5_6ChatTools(unset).reasoning_effort shouldBe Some(
        ReasoningEffort.none
      )
      ChatCompletionSettingsConversions.gpt5_6ChatTools(high).reasoning_effort shouldBe Some(
        ReasoningEffort.none
      )
      ChatCompletionSettingsConversions.gpt5_6ChatTools(none) shouldBe none
    }
  }
}
