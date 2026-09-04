package io.cequence.openaiscala.anthropic.service.impl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Unit tests for [[BedrockConnectionSettings]]'s redacted `toString` - it's easy to
 * accidentally `println`/log this settings object while debugging Bedrock auth, so its
 * `toString` must never reveal the secret key, session token, or bearer token, and must not
 * reveal the full access key either.
 */
class BedrockConnectionSettingsSpec extends AnyWordSpec with Matchers {

  private val AccessKey = "ASIATESTACCESSKEYWXYZ"
  private val SecretKey = "super-secret-value"
  private val SessionToken = "session-token-value"
  private val BearerToken = "bearer-token-value"

  "BedrockConnectionSettings.toString" should {

    "redact secretKey, sessionToken and bearerToken, and truncate accessKey" in {
      val settings = BedrockConnectionSettings(
        accessKey = AccessKey,
        secretKey = SecretKey,
        region = "eu-north-1",
        inferenceProfilePrefix = Some("eu"),
        sessionToken = Some(SessionToken),
        bearerToken = Some(BearerToken)
      )

      val str = settings.toString

      str should not include SecretKey
      str should not include SessionToken
      str should not include BearerToken
      str should not include AccessKey

      str should include("***")
      str should include("eu-north-1")
      str should include(AccessKey.takeRight(4))
      str should include("BedrockConnectionSettings(")
    }

    "render None for absent sessionToken and bearerToken" in {
      val settings = BedrockConnectionSettings(
        accessKey = AccessKey,
        secretKey = SecretKey,
        region = "us-east-1"
      )

      val str = settings.toString

      str should include("sessionToken=None")
      str should include("bearerToken=None")
    }

    "keep copy and equality semantics unaffected by the custom toString" in {
      val original = BedrockConnectionSettings(
        accessKey = AccessKey,
        secretKey = SecretKey,
        region = "eu-north-1",
        inferenceProfilePrefix = Some("eu"),
        sessionToken = Some(SessionToken),
        bearerToken = None
      )

      val copied = original.copy(region = "us-west-2")

      copied should not equal original
      copied.region shouldBe "us-west-2"

      val freshlyBuilt = BedrockConnectionSettings(
        accessKey = AccessKey,
        secretKey = SecretKey,
        region = "us-west-2",
        inferenceProfilePrefix = Some("eu"),
        sessionToken = Some(SessionToken),
        bearerToken = None
      )

      copied shouldBe freshlyBuilt
    }
  }
}
