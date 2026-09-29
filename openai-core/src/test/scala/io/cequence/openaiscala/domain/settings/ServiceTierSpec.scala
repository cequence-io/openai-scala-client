package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.JsonFormats.serviceTierFormat
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsString, Json}

class ServiceTierSpec extends AnyWordSpec with Matchers {

  "ServiceTier" should {

    "carry every tier OpenAI accepts (live 2026-09-29), written as its name" in {
      ServiceTier.values.map(Json.toJson(_)) shouldBe Seq(
        "auto",
        "default",
        "flex",
        "priority",
        "fast",
        "ultrafast"
      ).map(JsString)

      ServiceTier.values.foreach { tier =>
        Json.toJson(tier).as[ServiceTier] shouldBe tier
      }
    }

    "be set by withServiceTier" in {
      CreateChatCompletionSettings("gpt-6-astra")
        .withServiceTier(ServiceTier.ultrafast)
        .service_tier shouldBe Some(ServiceTier.ultrafast)
    }
  }
}
