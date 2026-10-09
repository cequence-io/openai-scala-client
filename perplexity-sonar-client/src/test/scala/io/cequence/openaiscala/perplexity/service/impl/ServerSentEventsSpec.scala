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

/**
 * The module's forwarding of the shared decoder (whose behaviour core's `ServerSentEventsSpec`
 * pins): the Sonar API's CRLF framing decodes, and a malformed stream fails with the module's
 * own exception.
 */
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

    "decode CRLF-delimited events (the Sonar API's framing)" in {
      parse(Seq(s"data: $eventA\r\n\r\ndata: $eventB\r\n\r\ndata: [DONE]\r\n\r\n")) shouldBe
        Seq(Json.parse(eventA), Json.parse(eventB))
    }

    "fail with the module's exception on a gateway error page and on the size limit" in {
      val page = intercept[PerplexityScalaClientException] {
        parse(Seq("<html><body>502 Bad Gateway</body></html>"))
      }
      page.getMessage should include("502 Bad Gateway")

      val tooLong = intercept[PerplexityScalaClientException] {
        parse(Seq("data: {\"type\":\"" + ("x" * 200)), maxEventBytes = 100)
      }
      tooLong.getMessage should include("100 bytes")
    }
  }
}
