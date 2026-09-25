package io.cequence.openaiscala.perplexity.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.cequence.openaiscala.perplexity.service.PerplexityScalaClientException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Await
import scala.concurrent.duration._

class ServerSentEventsSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("sse-spec")
  private implicit val materializer: Materializer = Materializer(system)

  override protected def afterAll(): Unit =
    Await.result(system.terminate(), 10.seconds)

  private def parse(
    chunks: Seq[String],
    maxEventBytes: Int = ServerSentEvents.DefaultMaxEventBytes
  ): Seq[JsValue] =
    Await.result(
      Source(chunks.map(ByteString(_)).toList)
        .via(ServerSentEvents.jsonPayloads(maxEventBytes))
        .runWith(Sink.seq),
      10.seconds
    )

  private val eventA = """{"type":"a","sequence_number":1}"""
  private val eventB = """{"type":"b","sequence_number":2,"text":"x\ny"}"""

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

    "give the same events for every split of the byte stream into two chunks" in {
      val stream =
        s"event: a\r\ndata: $eventA\r\n\r\n: keep-alive\n\ndata: $eventB\n\ndata: [DONE]\n\n"
      val expected = Seq(Json.parse(eventA), Json.parse(eventB))

      for (i <- 0 to stream.length) {
        withClue(s"split at $i: ") {
          parse(Seq(stream.take(i), stream.drop(i))) shouldBe expected
        }
      }
      parse(stream.map(_.toString)) shouldBe expected // one byte per chunk
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
      val chunks = bytes.grouped(3).toList

      Await.result(
        Source(chunks).via(ServerSentEvents.jsonPayloads()).runWith(Sink.seq),
        10.seconds
      ) shouldBe Seq(Json.parse(json))
    }

    "pass a non-SSE JSON body (an error answering the request) through whole" in {
      val error = """{"error":{"message":"Invalid API key","type":"authentication_error"}}"""
      parse(Seq(error)) shouldBe Seq(Json.parse(error))
      parse(Seq("{\n  \"error\": {\"message\": \"x\"}\n}")) shouldBe Seq(
        Json.obj("error" -> Json.obj("message" -> "x"))
      )
    }

    "fail on a non-SSE, non-JSON body such as a gateway error page" in {
      val error = intercept[PerplexityScalaClientException] {
        parse(Seq("<html><body>502 Bad Gateway</body></html>"))
      }
      error.getMessage should include("502 Bad Gateway")
    }

    "fail instead of buffering an event larger than the limit" in {
      val error = intercept[PerplexityScalaClientException] {
        parse(Seq("data: {\"type\":\"" + ("x" * 200)), maxEventBytes = 100)
      }
      error.getMessage should include("100 bytes")
    }
  }
}
