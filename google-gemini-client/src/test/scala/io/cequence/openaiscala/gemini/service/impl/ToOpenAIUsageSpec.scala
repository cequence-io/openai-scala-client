package io.cequence.openaiscala.gemini.service.impl

import io.cequence.openaiscala.gemini.domain.response.UsageMetadata
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ToOpenAIUsageSpec extends AnyWordSpec with Matchers {

  "toOpenAIUsage" should {

    "fold tool-use and thoughts tokens into prompt/completion (live MCP numbers)" in {
      // exact usage from a live gemini-3-flash-preview + Tool.McpServers call (2026-07-20)
      val usage = OpenAIGeminiChatCompletionService.toOpenAIUsage(
        UsageMetadata(
          promptTokenCount = 85,
          candidatesTokenCount = Some(124),
          totalTokenCount = 2310,
          thoughtsTokenCount = Some(127),
          toolUsePromptTokenCount = Some(1974)
        )
      )

      usage.prompt_tokens shouldBe 85 + 1974
      usage.completion_tokens shouldBe Some(124 + 127)
      usage.total_tokens shouldBe 2310
      // the OpenAI invariant a cost calculator relies on
      usage.prompt_tokens + usage.completion_tokens.get shouldBe usage.total_tokens
      usage.completion_tokens_details.flatMap(_.reasoning_tokens) shouldBe Some(127)
    }

    "map a plain response one-to-one (no tool use, no thoughts)" in {
      val usage = OpenAIGeminiChatCompletionService.toOpenAIUsage(
        UsageMetadata(
          promptTokenCount = 10,
          candidatesTokenCount = Some(20),
          totalTokenCount = 30
        )
      )

      usage.prompt_tokens shouldBe 10
      usage.completion_tokens shouldBe Some(20)
      usage.total_tokens shouldBe 30
      usage.completion_tokens_details shouldBe None
    }

    "pass cached tokens through and keep thoughts-only completion consistent" in {
      val usage = OpenAIGeminiChatCompletionService.toOpenAIUsage(
        UsageMetadata(
          promptTokenCount = 100,
          cachedContentTokenCount = Some(40),
          candidatesTokenCount = None,
          totalTokenCount = 150,
          thoughtsTokenCount = Some(50)
        )
      )

      usage.prompt_tokens_details.map(_.cached_tokens) shouldBe Some(40)
      usage.completion_tokens shouldBe Some(50)
      usage.prompt_tokens + usage.completion_tokens.get shouldBe usage.total_tokens
    }
  }
}
