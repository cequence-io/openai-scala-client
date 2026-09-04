package io.cequence.openaiscala.vertexai.service.impl

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ToVertexAIGenerationConfigSpec extends AnyWordSpec with Matchers {

  "toVertexAI (GenerationConfig)" should {

    "wire logprobs/top_logprobs to responseLogprobs/logprobs and never touch topK" in {
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        logprobs = Some(true),
        top_logprobs = Some(5)
      )

      val config = toVertexAI(settings)

      config.hasResponseLogprobs shouldBe true
      config.getResponseLogprobs shouldBe true
      config.hasLogprobs shouldBe true
      config.getLogprobs shouldBe 5
      config.hasTopK shouldBe false
    }

    "not set responseLogprobs/logprobs when logprobs is None" in {
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        logprobs = None,
        top_logprobs = Some(5)
      )

      val config = toVertexAI(settings)

      config.hasResponseLogprobs shouldBe false
      config.hasLogprobs shouldBe false
      config.hasTopK shouldBe false
    }

    "not set logprobs (count) when logprobs is explicitly false" in {
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        logprobs = Some(false),
        top_logprobs = Some(5)
      )

      val config = toVertexAI(settings)

      config.hasResponseLogprobs shouldBe true
      config.getResponseLogprobs shouldBe false
      config.hasLogprobs shouldBe false
    }

    "wire seed to GenerationConfig.seed" in {
      val settings = CreateChatCompletionSettings(
        model = "gemini-2.5-flash",
        seed = Some(42)
      )

      val config = toVertexAI(settings)

      config.getSeed shouldBe 42
    }
  }
}
