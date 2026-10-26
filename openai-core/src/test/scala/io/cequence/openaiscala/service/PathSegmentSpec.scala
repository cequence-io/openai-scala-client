package io.cequence.openaiscala.service

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PathSegmentSpec extends AnyWordSpec with Matchers {

  "PathSegment" should {

    "keep unreserved characters, sub-delims, ':' and '@' literal" in {
      val unreserved = "abcXYZ019-._~"
      PathSegment(unreserved) shouldBe unreserved

      val subDelims = "!$&'()*+,;="
      PathSegment(subDelims) shouldBe subDelims

      PathSegment("ft:gpt-4o-mini:org:custom:id") shouldBe "ft:gpt-4o-mini:org:custom:id"
      PathSegment("user@host") shouldBe "user@host"
      PathSegment("vs_123") shouldBe "vs_123"
    }

    "encode '/', '?', '#', '%' and whitespace" in {
      PathSegment("../../vs_victim") shouldBe "..%2F..%2Fvs_victim"
      PathSegment("x?a=b") shouldBe "x%3Fa=b"
      PathSegment("a#") shouldBe "a%23"
      PathSegment("%2e%2e") shouldBe "%252e%252e"
      PathSegment("a b") shouldBe "a%20b"
      PathSegment("a\tb\nc") shouldBe "a%09b%0Ac"
      PathSegment("a\\b") shouldBe "a%5Cb"
    }

    "encode non-ASCII characters as UTF-8" in {
      PathSegment("é") shouldBe "%C3%A9"
      PathSegment("日本") shouldBe "%E6%97%A5%E6%9C%AC"
      PathSegment("a😀") shouldBe "a%F0%9F%98%80"
    }

    "refuse null, empty, '.' and '..'" in {
      an[IllegalArgumentException] should be thrownBy PathSegment(null)
      an[IllegalArgumentException] should be thrownBy PathSegment("")
      an[IllegalArgumentException] should be thrownBy PathSegment(".")
      an[IllegalArgumentException] should be thrownBy PathSegment("..")
    }

    "accept dots inside a longer segment" in {
      PathSegment("...") shouldBe "..."
      PathSegment("a..b") shouldBe "a..b"
      PathSegment(".hidden") shouldBe ".hidden"
    }
  }
}
