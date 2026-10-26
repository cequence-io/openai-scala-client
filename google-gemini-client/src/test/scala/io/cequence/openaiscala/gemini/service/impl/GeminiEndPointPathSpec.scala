package io.cequence.openaiscala.gemini.service.impl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * A model name or resource id in a Gemini REST path is ONE path segment (after the known
 * `models/`, `batches/`, `files/`, `cachedContents/` prefix is stripped): '/', '?' and '#' are
 * escaped, an id of ".." (or an empty one) is refused when the endpoint is built.
 */
class GeminiEndPointPathSpec extends AnyWordSpec with Matchers {

  "EndPoint" should {

    "keep ordinary ids and strip the known prefix" in {
      EndPoint.generateContent("gemini-2.5-flash").value shouldBe
        "models/gemini-2.5-flash:generateContent"
      EndPoint.generateContent("models/gemini-2.5-flash").value shouldBe
        "models/gemini-2.5-flash:generateContent"
      EndPoint.streamGenerateContent("gemini-2.5-flash").value shouldBe
        "models/gemini-2.5-flash:streamGenerateContent"
      EndPoint.batchGenerateContent("gemini-2.5-flash").value shouldBe
        "models/gemini-2.5-flash:batchGenerateContent"
      EndPoint.batches("batches/b1").value shouldBe "batches/b1"
      EndPoint.cancelBatch("batches/b1").value shouldBe "batches/b1:cancel"
      EndPoint.cachedContents("cachedContents/c1").value shouldBe "cachedContents/c1"
      EndPoint.files("files/f1").value shouldBe "files/f1"
    }

    "escape '/', '?' and '#' in an id" in {
      EndPoint.generateContent("gemini-2.5-flash/../../batches/b1:cancel?").value shouldBe
        "models/gemini-2.5-flash%2F..%2F..%2Fbatches%2Fb1:cancel%3F:generateContent"
      EndPoint.files("x?").value shouldBe "files/x%3F"
      EndPoint.files("files/abc#").value shouldBe "files/abc%23"
      EndPoint.batches("../b").value shouldBe "batches/..%2Fb"
      EndPoint.cachedContents("c?x=1").value shouldBe "cachedContents/c%3Fx=1"
    }

    "refuse '..', '.' and an empty id" in {
      an[IllegalArgumentException] should be thrownBy EndPoint.generateContent("..")
      an[IllegalArgumentException] should be thrownBy EndPoint.generateContent("models/..")
      an[IllegalArgumentException] should be thrownBy EndPoint.files(".")
      an[IllegalArgumentException] should be thrownBy EndPoint.files("files/")
      an[IllegalArgumentException] should be thrownBy EndPoint.batches("")
      an[IllegalArgumentException] should be thrownBy EndPoint.cancelBatch("batches/..")
      an[IllegalArgumentException] should be thrownBy EndPoint.cachedContents("..")
    }
  }
}
