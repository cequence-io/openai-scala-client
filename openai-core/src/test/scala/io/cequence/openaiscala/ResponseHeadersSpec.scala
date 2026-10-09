package io.cequence.openaiscala

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ResponseHeadersSpec extends AnyWordSpec with Matchers {

  private val headers = Map(
    "Content-Type" -> Seq("application/json"),
    "X-Request-Id" -> Seq("req_1", "req_2"),
    "x-typesafe-request-id" -> Seq(" ts_1 "),
    "x-generation-id" -> Seq("")
  )

  "ResponseHeaders.first" should {

    "find a header whatever its case, taking its first value" in {
      ResponseHeaders.first(headers, Seq("x-request-id")) shouldBe Some("req_1")
      ResponseHeaders.first(headers, Seq("X-REQUEST-ID")) shouldBe Some("req_1")
    }

    "take the names by priority, not by their order in the response" in {
      ResponseHeaders.first(headers, Seq("x-typesafe-request-id", "x-request-id")) shouldBe
        Some("ts_1")
      ResponseHeaders.first(headers, Seq("x-request-id", "x-typesafe-request-id")) shouldBe
        Some("req_1")
    }

    "skip a blank value and an absent name" in {
      ResponseHeaders.first(headers, Seq("x-generation-id", "x-request-id")) shouldBe
        Some("req_1")
      ResponseHeaders.first(headers, Seq("x-generation-id")) shouldBe None
      ResponseHeaders.first(headers, Seq("x-missing")) shouldBe None
      ResponseHeaders.first(Map.empty, Seq("x-request-id")) shouldBe None
    }
  }
}
