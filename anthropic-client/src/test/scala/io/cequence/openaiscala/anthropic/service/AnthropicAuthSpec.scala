package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.service.auth.AnthropicTokenProvider
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for the auth-header construction helpers on [[AnthropicServiceFactory]] — covers
 * the two direct-Anthropic-API auth modes:
 *   - x-api-key (long-lived API key)
 *   - Authorization: Bearer (OAuth / static token), with an optional `anthropic-beta:
 *     oauth-2025-04-20` header
 *
 * and the trivial [[AnthropicTokenProvider.static]] provider.
 *
 * This spec lives in the same package as [[AnthropicServiceFactory]] (rather than under
 * `.impl`, like [[io.cequence.openaiscala.anthropic.service.impl.BedrockAuthSpec]]) because
 * `buildApiKeyHeaders` / `buildAuthTokenHeaders` are `private[service]`.
 */
class AnthropicAuthSpec extends AnyWordSpec with Matchers {

  "buildApiKeyHeaders" should {

    "produce exactly x-api-key and anthropic-version, with no Authorization or anthropic-beta" in {
      val headers = AnthropicServiceFactory.buildApiKeyHeaders("k")

      headers shouldBe Seq(
        ("x-api-key", "k"),
        ("anthropic-version", "2023-06-01")
      )

      val headersMap = headers.toMap
      headersMap.get("Authorization") shouldBe None
      headersMap.get("anthropic-beta") shouldBe None
    }
  }

  "buildAuthTokenHeaders" should {

    "with withOAuthBeta = true produce Authorization, anthropic-version and anthropic-beta, and no x-api-key" in {
      val headers = AnthropicServiceFactory.buildAuthTokenHeaders("tok", withOAuthBeta = true)

      headers shouldBe Seq(
        ("Authorization", "Bearer tok"),
        ("anthropic-version", "2023-06-01"),
        ("anthropic-beta", "oauth-2025-04-20")
      )

      headers.toMap.get("x-api-key") shouldBe None
    }

    "with withOAuthBeta = false omit the anthropic-beta header entirely" in {
      val headers =
        AnthropicServiceFactory.buildAuthTokenHeaders("tok", withOAuthBeta = false)

      headers shouldBe Seq(
        ("Authorization", "Bearer tok"),
        ("anthropic-version", "2023-06-01")
      )

      headers.toMap.get("anthropic-beta") shouldBe None
    }
  }

  "engine header merge (authHeaders ++ extraHeaders)" should {

    "keep both anthropic-beta values, oauth first then the caller-supplied one, in order" in {
      // Replicates ws-client's `authHeaders ++ extraHeaders` merge, which is what
      // managed-agent calls rely on to combine the always-on OAuth beta with their own
      // per-call `managed-agents-2026-04-01` beta header.
      val merged =
        AnthropicServiceFactory.buildAuthTokenHeaders("tok", withOAuthBeta = true) ++
          Seq(("anthropic-beta", "managed-agents-2026-04-01"))

      val betaValues = merged.collect { case ("anthropic-beta", value) => value }

      betaValues shouldBe Seq("oauth-2025-04-20", "managed-agents-2026-04-01")
    }
  }

  "AnthropicTokenProvider.static" should {

    "return the given token from accessToken() and expose no extra headers" in {
      val provider = AnthropicTokenProvider.static("t")

      provider.accessToken() shouldBe "t"
      provider.extraHeaders shouldBe Nil
    }
  }
}
