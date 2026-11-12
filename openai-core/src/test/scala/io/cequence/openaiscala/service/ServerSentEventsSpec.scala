package io.cequence.openaiscala.service

import io.cequence.wsclient.stream.{AsyncStream, Bytes}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.testkit.StreamSpecSupport
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration._

/**
 * The server-sent-events decoder's behaviour: framing (LF, CRLF, `\r\r`), multi-line data,
 * comments and the [DONE] terminator, a non-SSE body surfaced rather than dropped, the size
 * limit, the error type a module passes, and its boundary search - only the tail of the
 * pending event is searched, so it must still see a boundary that a chunk completes, whatever
 * the chunking, and a large event must decode in time linear in its size.
 */
class ServerSentEventsSpec
    extends AnyWordSpec
    with Matchers
    with StreamSpecSupport
    with BeforeAndAfterAll {

  private implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  // the bytes in chunks of `size` (views, no copies)
  private def grouped(
    bytes: Bytes,
    size: Int
  ): List[Bytes] =
    (0 until bytes.length by size).map(i => bytes.slice(i, i + size)).toList

  private def decode(
    chunks: Seq[Bytes],
    maxEventBytes: Int = ServerSentEvents.DefaultMaxEventBytes,
    error: String => Throwable = new OpenAIScalaClientException(_)
  ): Seq[JsValue] =
    runStream(
      AsyncStream
        .fromIterable(chunks.toList)
        .via(ServerSentEvents.jsonPayloads(maxEventBytes, error)),
      60.seconds
    )

  private def parse(
    chunks: Seq[String],
    maxEventBytes: Int = ServerSentEvents.DefaultMaxEventBytes
  ): Seq[JsValue] =
    decode(chunks.map(Bytes.utf8(_)), maxEventBytes)

  private val eventA = """{"type":"a","sequence_number":1}"""
  private val eventB = """{"type":"b","sequence_number":2,"text":"x\ny"}"""

  private class ModuleException(message: String) extends RuntimeException(message)

  "ServerSentEvents.jsonPayloads" should {

    "decode LF-delimited events with event: lines and the [DONE] terminator" in {
      parse(
        Seq(s"event: a\ndata: $eventA\n\nevent: b\ndata: $eventB\n\ndata: [DONE]\n\n")
      ) shouldBe
        Seq(Json.parse(eventA), Json.parse(eventB))
    }

    "decode CRLF-delimited events (the Sonar API's framing)" in {
      parse(Seq(s"data: $eventA\r\n\r\ndata: $eventB\r\n\r\ndata: [DONE]\r\n\r\n")) shouldBe
        Seq(Json.parse(eventA), Json.parse(eventB))
    }

    "see a boundary that a chunk completes, whatever the chunking - a byte at a time too" in {
      val stream =
        "event: a\r\ndata: {\"n\":1}\r\n\r\n: keep-alive\n\nid: 2\ndata: {\"n\":2}\n\n" +
          "data: {\"n\":3}\r\rdata: [DONE]\n\n"
      val expected = Seq(Json.obj("n" -> 1), Json.obj("n" -> 2), Json.obj("n" -> 3))

      // one event per chunking: whole, every byte its own chunk, and every two-chunk split
      decode(Seq(Bytes.utf8(stream))) shouldBe expected
      decode(stream.map(c => Bytes.utf8(c.toString))) shouldBe expected
      for (i <- 0 to stream.length)
        withClue(s"split at $i: ") {
          decode(Seq(Bytes.utf8(stream.take(i)), Bytes.utf8(stream.drop(i)))) shouldBe expected
        }
    }

    "join multi-line data and accept `data:` without the space" in {
      parse(Seq("data:{\"type\":\"a\",\ndata: \"sequence_number\":1}\n\n")) shouldBe Seq(
        Json.parse(eventA)
      )
    }

    "skip comments, id / retry lines and empty events" in {
      parse(Seq(s": ping\n\nid: 5\nretry: 1000\n\n\n\ndata: $eventA\n\n")) shouldBe Seq(
        Json.parse(eventA)
      )
    }

    "emit a last event that lacks the trailing blank line" in {
      parse(Seq(s"data: $eventA\n\ndata: $eventB")) shouldBe Seq(
        Json.parse(eventA),
        Json.parse(eventB)
      )
    }

    "keep multi-byte UTF-8 characters intact across chunk borders" in {
      val json = """{"type":"a","sequence_number":1,"delta":"Ørsted – 東京"}"""
      val bytes = Bytes.utf8(s"data: $json\n\n")

      decode(grouped(bytes, 3)) shouldBe Seq(Json.parse(json))
    }

    "pass a non-SSE JSON body (an error answering the request) through whole" in {
      val error = """{"error":{"message":"Invalid API key","type":"authentication_error"}}"""
      parse(Seq(error)) shouldBe Seq(Json.parse(error))
      parse(Seq("{\n  \"error\": {\"message\": \"x\"}\n}")) shouldBe Seq(
        Json.obj("error" -> Json.obj("message" -> "x"))
      )
    }

    "fail on a non-SSE, non-JSON body such as a gateway error page" in {
      val error = intercept[OpenAIScalaClientException] {
        parse(Seq("<html><body>502 Bad Gateway</body></html>"))
      }
      error.getMessage should include("502 Bad Gateway")
    }

    "fail instead of buffering an event larger than the limit" in {
      val error = intercept[OpenAIScalaClientException] {
        parse(Seq("data: {\"type\":\"" + ("x" * 200)), maxEventBytes = 100)
      }
      error.getMessage should include("100 bytes")
    }

    "fail with the exception a module passes" in {
      intercept[ModuleException] {
        decode(
          Seq(Bytes.utf8("<html>502</html>")),
          error = new ModuleException(_)
        )
      }.getMessage should include("502")
      intercept[ModuleException] {
        decode(
          Seq(Bytes.utf8("data: {\"type\":\"" + ("x" * 200))),
          maxEventBytes = 100,
          error = new ModuleException(_)
        )
      }
    }

    "decode a multi-megabyte event delivered in small chunks in linear time" in {
      // a 2 MiB event in 8 KiB chunks: a search of the whole buffer per chunk took minutes
      // here (1 MiB: 21 s); the carried-window search takes well under a second
      val text = "A" * (2 * 1024 * 1024)
      val event = Bytes.utf8(s"""data: {"text":"$text"}""" + "\n\n")
      val chunks = grouped(event, 8 * 1024)

      val start = System.nanoTime()
      val decoded = decode(chunks)
      val seconds = (System.nanoTime() - start) / 1e9

      decoded should have size 1
      (decoded.head \ "text").as[String] should have length text.length
      seconds should be < 20.0
    }

    "decode a 16 MiB event in 1460-byte chunks (TCP-sized) in linear time" in {
      // a tail search on a rope of the chunks was quadratic here (64 MiB: 11 s, against 0.9 s
      // with the carried window - measured 2026-10-09); the per-run buffer is linear too
      val parts = Seq.fill(16 * 1024)("\"" + ("x" * 1018) + "\"")
      val event = Bytes.utf8(s"""data: {"parts":[${parts.mkString(",")}]}""" + "\n\n")
      val chunks = grouped(event, 1460)

      val start = System.nanoTime()
      val decoded = decode(chunks, maxEventBytes = 64 * 1024 * 1024)
      val seconds = (System.nanoTime() - start) / 1e9

      decoded should have size 1
      (decoded.head \ "parts").as[Seq[String]] should have size parts.size.toLong
      seconds should be < 10.0
    }

    "decode one huge chunk of blank lines in linear time" in {
      // 256 KiB of '\n' in ONE chunk = 131,072 boundaries: the decoder used to copy and rescan
      // the rest of the chunk once per boundary (quadratic)
      val chunk = Bytes(Array.fill[Byte](256 * 1024)('\n'.toByte))

      val start = System.nanoTime()
      val decoded = decode(Seq(chunk))
      val seconds = (System.nanoTime() - start) / 1e9

      decoded shouldBe empty
      seconds should be < 2.0
    }

    "decode one chunk of thousands of small events, in order, in linear time" in {
      val count = 4096
      val chunk = Bytes.utf8((0 until count).map(i => s"""data: {"i":$i}\n\n""").mkString)

      val start = System.nanoTime()
      val decoded = decode(Seq(chunk))
      val seconds = (System.nanoTime() - start) / 1e9

      decoded shouldBe (0 until count).map(i => Json.obj("i" -> i))
      seconds should be < 2.0
    }

    "decode one chunk of comment-only events in linear time" in {
      // 64 KiB of ':\n\n' (21,845 events, none carrying data) in a single chunk
      val chunk = Bytes.utf8(":\n\n" * (64 * 1024 / 3))

      val start = System.nanoTime()
      val decoded = decode(Seq(chunk))
      val seconds = (System.nanoTime() - start) / 1e9

      decoded shouldBe empty
      seconds should be < 2.0
    }

    "keep the events of a many-event chunk across a boundary the next chunk completes" in {
      // events closed inside the chunk, then a pending one finished in the following chunk
      val first = "data: {\"i\":1}\n\ndata: {\"i\":2}\r\n\r\ndata: {\"i\":3}\n"
      val second = "\ndata: {\"i\":4}\r\r"

      decode(Seq(Bytes.utf8(first), Bytes.utf8(second))) shouldBe
        (1 to 4).map(i => Json.obj("i" -> i))
    }
  }
}
