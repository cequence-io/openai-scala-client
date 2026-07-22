package io.cequence.openaiscala.vertexai.service.impl

import com.google.cloud.vertexai.api.GenerateContentResponse.UsageMetadata
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ToOpenAIUsageSpec extends AnyWordSpec with Matchers {

  "toOpenAI (usage)" should {

    "fold thoughts tokens into completion_tokens (thinking enabled)" in {
      val usage = toOpenAI(
        UsageMetadata
          .newBuilder()
          .setPromptTokenCount(100)
          .setCandidatesTokenCount(50)
          .setThoughtsTokenCount(30)
          .setTotalTokenCount(180)
          .build()
      )

      usage.prompt_tokens shouldBe 100
      usage.completion_tokens shouldBe Some(80)
      usage.total_tokens shouldBe 180
      // the OpenAI invariant a cost calculator relies on
      usage.prompt_tokens + usage.completion_tokens.get shouldBe usage.total_tokens
      usage.completion_tokens_details.flatMap(_.reasoning_tokens) shouldBe Some(30)
    }

    "map a plain response one-to-one and pass cached tokens through" in {
      val usage = toOpenAI(
        UsageMetadata
          .newBuilder()
          .setPromptTokenCount(10)
          .setCandidatesTokenCount(20)
          .setCachedContentTokenCount(4)
          .setTotalTokenCount(30)
          .build()
      )

      usage.prompt_tokens shouldBe 10
      usage.completion_tokens shouldBe Some(20)
      usage.total_tokens shouldBe 30
      usage.prompt_tokens_details.map(_.cached_tokens) shouldBe Some(4)
      usage.completion_tokens_details shouldBe None
    }
  }
}
