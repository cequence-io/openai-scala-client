package io.cequence.openaiscala.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Framing, Sink, Source}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.wsclient.domain.CequenceWSException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration._

class StreamingConstsSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("streaming-consts-spec")
  private implicit val materializer: Materializer = Materializer(system)

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 10.seconds)
    ()
  }

  private def capOf(value: String) =
    StreamingConsts.maxFrameLengthFrom(
      ConfigFactory.parseString(s"openai-scala-client.streaming.maxFrameLength = $value")
    )

  "The stream frame cap" should {

    "be 32 MiB unless configured" in {
      StreamingConsts.DefaultMaxFrameLength shouldBe 32 * 1024 * 1024
      StreamingConsts.maxFrameLengthFrom(ConfigFactory.empty()) shouldBe 32 * 1024 * 1024
      // no config file in core's tests
      StreamingConsts.maxFrameLength shouldBe StreamingConsts.DefaultMaxFrameLength
    }

    "read a size or a number of bytes" in {
      capOf("128 MiB") shouldBe 128 * 1024 * 1024
      capOf("16m") shouldBe 16 * 1024 * 1024
      capOf("2047 MiB") shouldBe 2047 * 1024 * 1024
      capOf("1500 KiB") shouldBe 1500 * 1024
      capOf("5000000") shouldBe 5000000
    }

    "refuse a value that is not a size, or out of range" in {
      (the[OpenAIScalaClientException] thrownBy capOf("lots")).getMessage shouldBe
        "openai-scala-client.streaming.maxFrameLength must be a size such as '128 MiB' or a number of bytes, got 'lots'."

      // a bare number is bytes - a likely slip for MiB
      (the[OpenAIScalaClientException] thrownBy capOf("64")).getMessage should include(
        "a size needs a unit, e.g. '64 MiB'"
      )

      Seq("2 GiB", "3 GiB", "512").foreach { value =>
        (the[OpenAIScalaClientException] thrownBy capOf(value)).getMessage should include(
          "must be at least 1 KiB and less than 2 GiB"
        )
      }
    }
  }

  "frameTooLong" should {

    def overflow(limit: Int): Throwable =
      Await.result(
        Source
          .single(ByteString("x" * (limit + 10) + "\n"))
          .via(Framing.delimiter(ByteString("\n"), limit, allowTruncation = true))
          .runWith(Sink.seq)
          .failed,
        10.seconds
      )

    "name the setting for a frame over the cap - Akka's failure as the cause" in {
      val failure = overflow(2048)
      val mapped = StreamingConsts.frameTooLong(failure)

      mapped shouldBe an[OpenAIScalaClientException]
      mapped.getMessage shouldBe
        "A streamed frame (one event or line of the stream) exceeds the limit of 2 KiB. " +
        "Raise it with the config key openai-scala-client.streaming.maxFrameLength (e.g. '128 MiB') " +
        "or the env variable OPENAI_SCALA_CLIENT_STREAM_MAX_FRAME_LENGTH."
      mapped.getCause shouldBe failure
    }

    "read the limit from ws-client's wrapped failure too" in {
      // ws-client's PlayWSStreamClientEngine turns Akka's failure into this one
      val wrapped = new CequenceWSException(
        "POST chat/completions: Stream framing problem occurred. Read 1048577 bytes which is more than 1048576 without seeing a line terminator."
      )

      StreamingConsts.frameTooLong(wrapped).getMessage should include(
        "exceeds the limit of 1 MiB"
      )
    }

    "leave any other failure, and its own result, as it is" in {
      StreamingConsts.frameTooLong.isDefinedAt(
        new CequenceWSException("timeout")
      ) shouldBe false
      StreamingConsts.frameTooLong.isDefinedAt(new RuntimeException("boom")) shouldBe false

      val mapped = StreamingConsts.frameTooLong(overflow(2048))
      StreamingConsts.frameTooLong.isDefinedAt(mapped) shouldBe false
    }
  }

  "The server-sent-events decoder" should {

    "fail an event over its cap with the same message" in {
      val failure = Await.result(
        Source
          .single(ByteString("data: {\"type\":\"" + ("x" * 3000)))
          .via(ServerSentEvents.jsonPayloads(maxEventBytes = 2048))
          .runWith(Sink.seq)
          .failed,
        10.seconds
      )

      failure shouldBe an[OpenAIScalaClientException]
      failure.getMessage shouldBe StreamingConsts.frameTooLongMessage(2048)
    }

    "use the shared cap by default" in {
      ServerSentEvents.DefaultMaxEventBytes shouldBe StreamingConsts.maxFrameLength
    }
  }
}
