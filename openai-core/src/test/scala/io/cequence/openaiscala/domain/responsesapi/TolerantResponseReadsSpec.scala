package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.responsesapi.JsonFormats._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

/**
 * The Response reads tolerate what Responses-compatible providers add or leave out - the
 * fields below are verbatim from Perplexity's `/v1/responses` (live 2026-09-25): an empty
 * `truncation` and a provider-specific `search_results` output item.
 */
class TolerantResponseReadsSpec extends AnyWordSpec with Matchers {

  private val response = Json.parse(
    """{
      |  "background": false, "completed_at": 1790349468, "created_at": 1790349468, "error": null,
      |  "frequency_penalty": 0, "id": "resp_ce36937b", "incomplete_details": null, "instructions": null,
      |  "max_output_tokens": 300, "max_tool_calls": null, "metadata": {}, "model": "openai/gpt-5.4-mini",
      |  "object": "response",
      |  "output": [
      |    {"type": "search_results", "queries": ["oslo news"],
      |     "results": [{"id": 1, "url": "https://example.com", "title": "t", "snippet": "s", "source": "web"}]},
      |    {"type": "message", "id": "msg_1", "role": "assistant", "status": "completed",
      |     "content": [{"type": "output_text", "text": "Oslo.", "annotations": []}]}
      |  ],
      |  "parallel_tool_calls": true, "presence_penalty": 0, "previous_response_id": null,
      |  "prompt_cache_key": null, "reasoning": null, "safety_identifier": null, "service_tier": "default",
      |  "status": "completed", "store": false, "temperature": 1, "text": {"format": {"type": "text"}},
      |  "tool_choice": "auto", "tools": [], "top_logprobs": 0, "top_p": 1, "truncation": "",
      |  "usage": {"cost": {"currency": "USD", "input_cost": 0.0001, "output_cost": 0.00005, "total_cost": 0.00015},
      |            "input_tokens": 128, "input_tokens_details": {"cached_tokens": 0}, "output_tokens": 12,
      |            "output_tokens_details": {"reasoning_tokens": 0}, "total_tokens": 140},
      |  "user": null
      |}""".stripMargin
  )

  "Response reads" should {

    "skip an unknown output item type and read an unknown truncation value as None" in {
      val parsed = response.validate[Response]

      parsed.isSuccess shouldBe true
      parsed.get.output.map(_.getClass.getSimpleName) shouldBe Seq("OutputContent")
      parsed.get.outputText shouldBe Some("Oslo.")
      parsed.get.truncation shouldBe None
    }

    "still read the known truncation values" in {
      (response.as[JsObject] + ("truncation" -> JsString("auto")))
        .as[Response]
        .truncation shouldBe
        Some(TruncationStrategy.Auto)
    }

    "still fail on a malformed item of a known type" in {
      val broken =
        response.as[JsObject] + ("output" -> Json.arr(Json.obj("type" -> "message")))
      broken.validate[Response].isError shouldBe true
    }
  }
}
