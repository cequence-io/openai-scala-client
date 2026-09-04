package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.{BaseMessage, JsonSchema, ModelId, UserMessage}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Covers the jtokkit encoding-resolution fallback in [[OpenAICountTokensHelper]]: newer OpenAI
 * models (gpt-5.x, gpt-6.x, chat-latest, o-series, ...) and non-OpenAI model ids (claude-*,
 * gemini-*, ...) are not present in jtokkit 1.1.0's `ModelType` list and must no longer throw
 * `NoSuchElementException`, but instead fall back to an o200k_base estimate.
 */
class OpenAICountTokensEncodingFallbackSpec extends AnyWordSpec with Matchers {

  trait TestCase extends OpenAICountTokensHelper

  private val messages: Seq[BaseMessage] = Seq(UserMessage("Hello world"))

  private val unicodeAndCodeText =
    "Ünïcödé 🚀 def foo(x): return x*2 — pièce de résistance"

  "OpenAICountTokensHelper" should {

    Seq(
      "gpt-5.4-mini",
      "gpt-6-astra",
      "chat-latest",
      "o3-pro",
      "claude-sonnet-4-6",
      "gemini-3.6-flash",
      "some-unknown-model"
    ).foreach { model =>
      s"not throw and return a positive token count for '$model'" in new TestCase {
        noException should be thrownBy countMessageTokens(model, messages)

        val count: Int = countMessageTokens(model, messages)
        count should be > 0
      }
    }

    "count tokens for gpt-5.4-mini (unlisted) the same as for gpt-4o " +
      "(both o200k_base)" in new TestCase {
        val gpt54MiniCount: Int = countMessageTokens(ModelId.gpt_5_4_mini, messages)
        val gpt4oCount: Int = countMessageTokens(ModelId.gpt_4o, messages)

        gpt54MiniCount shouldEqual gpt4oCount
      }

    "count tokens for gpt-4.1 the same as for gpt-4o (both o200k_base)" in new TestCase {
      val unicodeMessages = Seq(UserMessage(unicodeAndCodeText))

      val gpt41Count: Int = countMessageTokens(ModelId.gpt_4_1, unicodeMessages)
      val gpt4oCount: Int = countMessageTokens(ModelId.gpt_4o, unicodeMessages)

      gpt41Count shouldEqual gpt4oCount
    }

    "count tokens for gpt-4.1 (o200k_base) differently than gpt-4-0613 (cl100k_base) " +
      "for a unicode/emoji/code-heavy text" in new TestCase {
        val unicodeMessages = Seq(UserMessage(unicodeAndCodeText))

        val gpt41Count: Int = countMessageTokens(ModelId.gpt_4_1, unicodeMessages)
        val gpt4Count: Int = countMessageTokens(ModelId.gpt_4_0613, unicodeMessages)

        gpt41Count should not equal gpt4Count
      }

    "not throw for countFunMessageTokens with an unlisted model (gpt-5)" in new TestCase {
      val function = FunctionTool(
        name = "getWeather",
        parameters = JsonSchema.Object(
          properties = Seq(
            "location" -> JsonSchema.String(
              description = Some("The city to get the weather for")
            )
          )
        )
      )

      noException should be thrownBy countFunMessageTokens(
        ModelId.gpt_5,
        messages,
        Seq(function),
        None
      )

      val count: Int = countFunMessageTokens(ModelId.gpt_5, messages, Seq(function), None)
      count should be > 0
    }

    "count tokens for a plain text with an unlisted model (gpt-6-astra)" in new TestCase {
      val count: Int = countTokens("hello", ModelId.gpt_6_astra)
      count should be > 0
    }
  }
}
