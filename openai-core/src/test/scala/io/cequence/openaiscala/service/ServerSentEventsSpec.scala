package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.cequence.openaiscala.OpenAIScalaClientException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * The server-sent-events decoder's behaviour: framing (LF, CRLF, `\r\r`), multi-line data,
 * comments and the [DONE] terminator, a non-SSE body surfaced rather than dropped, the size
 * limit, the error type a module passes, and its boundary search - only the tail of the
 * pending event is searched, so it must still see a boundary that a chunk completes, whatever
 * the chunking, and a large event must decode in time linear in its size.
 */
class ServerSentEventsSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("server-sent-events-spec")
  private implicit val materializer: Materializer = Materializer(system)

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def decode(
    chunks: Seq[ByteString],
    maxEventBytes: Int = ServerSentEvents.DefaultMaxEventBytes,
    error: String => Throwable = new OpenAIScalaClientException(_)
  ): Seq[JsValue] =
    Await.result(
      Source(chunks.toList)
        .via(ServerSentEvents.jsonPayloads(maxEventBytes, error))
        .runWith(Sink.seq),
      60.seconds
    )

  private def parse(
    chunks: Seq[String],
    maxEventBytes: Int = ServerSentEvents.DefaultMaxEventBytes
  ): Seq[JsValue] =
    decode(chunks.map(ByteString(_)), maxEventBytes)

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
      decode(Seq(ByteString(stream))) shouldBe expected
      decode(stream.map(c => ByteString(c.toString))) shouldBe expected
      for (i <- 0 to stream.length)
        withClue(s"split at $i: ") {
          decode(Seq(ByteString(stream.take(i)), ByteString(stream.drop(i)))) shouldBe expected
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
      val bytes = ByteString(s"data: $json\n\n")

      decode(bytes.grouped(3).toList) shouldBe Seq(Json.parse(json))
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
          Seq(ByteString("<html>502</html>")),
          error = new ModuleException(_)
        )
      }.getMessage should include("502")
      intercept[ModuleException] {
        decode(
          Seq(ByteString("data: {\"type\":\"" + ("x" * 200))),
          maxEventBytes = 100,
          error = new ModuleException(_)
        )
      }
    }

    "decode a multi-megabyte event delivered in small chunks in linear time" in {
      // a 2 MiB event in 8 KiB chunks: a search of the whole buffer per chunk took minutes
      // here (1 MiB: 21 s); the carried-window search takes well under a second
      val text = "A" * (2 * 1024 * 1024)
      val event = ByteString(s"""data: {"text":"$text"}""" + "\n\n")
      val chunks = event.grouped(8 * 1024).toList

      val start = System.nanoTime()
      val decoded = decode(chunks)
      val seconds = (System.nanoTime() - start) / 1e9

      decoded should have size 1
      (decoded.head \ "text").as[String] should have length text.length
      seconds should be < 20.0
    }
  }
}
