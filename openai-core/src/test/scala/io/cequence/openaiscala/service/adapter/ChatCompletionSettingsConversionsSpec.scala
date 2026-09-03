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
  }
}
