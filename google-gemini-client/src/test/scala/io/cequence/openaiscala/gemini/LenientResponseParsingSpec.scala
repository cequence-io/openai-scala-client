package io.cequence.openaiscala.gemini

import io.cequence.openaiscala.domain.NonOpenAIModelId
import io.cequence.openaiscala.gemini.JsonFormats._
import io.cequence.openaiscala.gemini.domain.{HarmProbability, Modality, Part}
import io.cequence.openaiscala.gemini.domain.response.{
  BlockReason,
  Candidate,
  FinishReason,
  GenerateContentResponse,
  PromptFeedback,
  UsageMetadata
}
import io.cequence.openaiscala.gemini.service.GeminiServiceConsts
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.libs.json.{JsString, Json}

/**
 * Covers two forward-compatibility defects in Gemini response parsing:
 *   - `candidate.content` is sometimes omitted by the server (blocked/empty candidates) but
 *     was modeled as mandatory, failing the whole (already billed) response.
 *   - strict enum formats (finishReason, blockReason, harmProbability, modality) failed the
 *     whole response on any server-side enum value this client doesn't yet model.
 */
class LenientResponseParsingSpec extends AnyWordSpecLike with Matchers {

  "GenerateContentResponse parsing" should {

    "parse a response whose only candidate was blocked (no content field)" in {
      val json = Json.parse("""
        |{
        |  "candidates": [
        |    {
        |      "finishReason": "SAFETY",
        |      "index": 0,
        |      "safetyRatings": [
        |        {
        |          "category": "HARM_CATEGORY_HARASSMENT",
        |          "probability": "HIGH",
        |          "blocked": true
        |        }
        |      ]
        |    }
        |  ],
        |  "usageMetadata": {
        |    "promptTokenCount": 10,
        |    "totalTokenCount": 15
        |  },
        |  "modelVersion": "gemini-3.6-flash"
        |}
        |""".stripMargin)

      val response = json.as[GenerateContentResponse]

      response.candidates should have size 1
      val candidate = response.candidates.head
      candidate.content.parts shouldBe empty
      candidate.finishReason shouldBe Some(FinishReason.SAFETY)
      response.usageMetadata shouldBe
        UsageMetadata(promptTokenCount = 10, totalTokenCount = 15)
      response.modelVersion shouldBe "gemini-3.6-flash"
    }

    "fall back an unknown finishReason to OTHER while still parsing the content" in {
      val json = Json.parse("""
        |{
        |  "content": {
        |    "parts": [ { "text": "Hello" } ],
        |    "role": "model"
        |  },
        |  "finishReason": "SOME_FUTURE_REASON"
        |}
        |""".stripMargin)

      val candidate = json.as[Candidate]

      candidate.finishReason shouldBe Some(FinishReason.OTHER)
      candidate.content.parts shouldBe Seq(Part.Text("Hello"))
    }

    "parse the newly added UNEXPECTED_TOOL_CALL finish reason" in {
      val json = Json.parse("""
        |{
        |  "content": { "parts": [], "role": "model" },
        |  "finishReason": "UNEXPECTED_TOOL_CALL"
        |}
        |""".stripMargin)

      json.as[Candidate].finishReason shouldBe Some(FinishReason.UNEXPECTED_TOOL_CALL)
    }

    "fall back an unknown blockReason, harmProbability and modality" in {
      val promptFeedbackJson = Json.parse("""
        |{
        |  "blockReason": "NEW_BLOCK_REASON",
        |  "safetyRatings": [
        |    {
        |      "category": "HARM_CATEGORY_HARASSMENT",
        |      "probability": "WHATEVER",
        |      "blocked": false
        |    }
        |  ]
        |}
        |""".stripMargin)

      val promptFeedback = promptFeedbackJson.as[PromptFeedback]
      promptFeedback.blockReason shouldBe Some(BlockReason.OTHER)
      promptFeedback.safetyRatings.head.probability shouldBe
        HarmProbability.HARM_PROBABILITY_UNSPECIFIED

      val usageMetadataJson = Json.parse("""
        |{
        |  "promptTokenCount": 5,
        |  "totalTokenCount": 5,
        |  "promptTokensDetails": [
        |    { "modality": "HOLOGRAM", "tokenCount": 5 }
        |  ]
        |}
        |""".stripMargin)

      val usageMetadata = usageMetadataJson.as[UsageMetadata]
      usageMetadata.promptTokensDetails.head.modality shouldBe Modality.MODALITY_UNSPECIFIED
    }

    "still write a known finish reason as its plain string" in {
      Json.toJson(FinishReason.STOP: FinishReason) shouldBe JsString("STOP")
    }
  }

  "GeminiServiceConsts" should {

    "default to a currently supported (non shut-down) model" in {
      val consts = new GeminiServiceConsts {}
      consts.DefaultSettings.GenerateContent.model shouldBe NonOpenAIModelId.gemini_3_6_flash
    }
  }
}
