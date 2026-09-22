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

  "ChatCompletionSettingsConversions.gpt6SolLuna" should {

    "keep GPT-5.6's rules: strip sampling params, keep 'none', downgrade 'max' and 'minimal'" in {
      val settings = CreateChatCompletionSettings(
        model = ModelId.gpt_6_luna,
        max_tokens = Some(100),
        temperature = Some(0.2),
        top_p = Some(0.5),
        presence_penalty = Some(0.5),
        frequency_penalty = Some(0.5),
        logprobs = Some(true),
        reasoning_effort = Some(ReasoningEffort.max)
      )

      val out = ChatCompletionSettingsConversions.gpt6SolLuna(settings)

      out.max_tokens shouldBe None
      out.extra_params.get("max_completion_tokens") shouldBe Some(100)
      out.temperature shouldBe Some(1d)
      out.top_p shouldBe Some(1d)
      out.presence_penalty shouldBe Some(0d)
      out.frequency_penalty shouldBe Some(0d)
      out.logprobs shouldBe None
      out.reasoning_effort shouldBe Some(ReasoningEffort.xhigh)

      val none =
        settings.copy(model = ModelId.gpt_6_sol, reasoning_effort = Some(ReasoningEffort.none))
      ChatCompletionSettingsConversions.gpt6SolLuna(none).reasoning_effort shouldBe Some(
        ReasoningEffort.none
      )
      ChatCompletionSettingsConversions
        .gpt6SolLuna(
          none.copy(reasoning_effort = Some(ReasoningEffort.minimal))
        )
        .reasoning_effort shouldBe Some(ReasoningEffort.low)
    }

    "leave function tools on the chat completions API (only Astra needs the Responses API)" in {
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        ModelId.gpt_6_luna
      ) shouldBe false
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        ModelId.gpt_6_sol
      ) shouldBe false
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        "global." + ModelId.bedrock_openai_gpt_6_luna
      ) shouldBe false
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        ModelId.bedrock_openai_gpt_6_astra
      ) shouldBe true
    }
  }

  "ChatCompletionSettingsConversions.responsesReasoningEffort" should {

    "keep 'max' and 'none' for gpt-6 Sol/Luna and gpt-5.6, downgrade 'minimal' to 'low'" in {
      Seq(ModelId.gpt_6_luna, ModelId.gpt_6_sol, ModelId.gpt_5_6_terra).foreach { model =>
        ChatCompletionSettingsConversions.responsesReasoningEffort(
          model,
          ReasoningEffort.max
        ) shouldBe ReasoningEffort.max
        ChatCompletionSettingsConversions.responsesReasoningEffort(
          model,
          ReasoningEffort.none
        ) shouldBe ReasoningEffort.none
        ChatCompletionSettingsConversions.responsesReasoningEffort(
          model,
          ReasoningEffort.minimal
        ) shouldBe ReasoningEffort.low
      }
    }

    "lift 'none' to 'low' for gpt-6-astra (also on Bedrock) and keep 'max'" in {
      ChatCompletionSettingsConversions.responsesReasoningEffort(
        ModelId.gpt_6_astra,
        ReasoningEffort.none
      ) shouldBe ReasoningEffort.low
      ChatCompletionSettingsConversions.responsesReasoningEffort(
        "us." + ModelId.bedrock_openai_gpt_6_astra,
        ReasoningEffort.none
      ) shouldBe ReasoningEffort.low
      ChatCompletionSettingsConversions.responsesReasoningEffort(
        ModelId.gpt_6_astra,
        ReasoningEffort.max
      ) shouldBe ReasoningEffort.max
    }

    "leave other models alone" in {
      ChatCompletionSettingsConversions.responsesReasoningEffort(
        ModelId.gpt_5_4,
        ReasoningEffort.minimal
      ) shouldBe ReasoningEffort.minimal
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

  "ChatCompletionSettingsConversions.canonicalOpenAIModel" should {

    "strip Bedrock's provider prefix and any inference-profile geo prefix" in {
      ChatCompletionSettingsConversions.canonicalOpenAIModel("openai.gpt-5.6-luna") shouldBe
        "gpt-5.6-luna"
      ChatCompletionSettingsConversions.canonicalOpenAIModel("us.openai.gpt-6-astra") shouldBe
        "gpt-6-astra"
      ChatCompletionSettingsConversions.canonicalOpenAIModel(
        "global.openai.gpt-5.6-sol"
      ) shouldBe "gpt-5.6-sol"
    }

    "handle geo prefixes AWS has not shipped yet, such as eu., without a code change" in {
      // the prefix is matched generically, so an EU (or any future) inference profile works
      ChatCompletionSettingsConversions.canonicalOpenAIModel("eu.openai.gpt-5.6-luna") shouldBe
        "gpt-5.6-luna"
      ChatCompletionSettingsConversions.canonicalOpenAIModel(
        "apac.openai.gpt-6-astra"
      ) shouldBe
        "gpt-6-astra"
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        "eu.openai.gpt-6-astra"
      ) shouldBe true
    }

    "canonicalize the dated Bedrock snapshots onto their undated parameter rules" in {
      // the per-model dispatch matches on a prefix, so the date suffix must survive stripping
      ChatCompletionSettingsConversions.canonicalOpenAIModel(
        ModelId.bedrock_openai_gpt_5_5_2026_04_23
      ) shouldBe ModelId.gpt_5_5_2026_04_23
      ChatCompletionSettingsConversions.canonicalOpenAIModel(
        ModelId.bedrock_openai_gpt_5_4_2026_03_05
      ) shouldBe ModelId.gpt_5_4_2026_03_05
    }

    "leave plain OpenAI ids and other providers' ids alone" in {
      ChatCompletionSettingsConversions.canonicalOpenAIModel("gpt-5.6-luna") shouldBe
        "gpt-5.6-luna"
      // Groq / Fireworks use a slash, not a dot - not a Bedrock id
      ChatCompletionSettingsConversions.canonicalOpenAIModel("openai/gpt-oss-120b") shouldBe
        "openai/gpt-oss-120b"
      ChatCompletionSettingsConversions.canonicalOpenAIModel(
        "anthropic.claude-sonnet-4-6"
      ) shouldBe
        "anthropic.claude-sonnet-4-6"
    }

    "make the GPT-6 Responses-API routing apply to the Bedrock ids too" in {
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        "us.openai.gpt-6-astra"
      ) shouldBe true
      ChatCompletionSettingsConversions.chatToolsRequireResponsesAPI(
        "openai.gpt-5.6-luna"
      ) shouldBe false
    }
  }
}
