package io.cequence.openaiscala.gemini.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.gemini.domain.settings.GenerateContentSettings
import io.cequence.openaiscala.gemini.domain.{ChatRole, Content, Part}
import io.cequence.openaiscala.gemini.service.GeminiServiceFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * A path id or model name the client refuses ("", "." or "..") fails the Gemini calls as a
 * failed Future or stream, never a synchronous throw - nothing is sent (a dummy key).
 */
class GeminiPathSegmentRefusalSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("gemini-path-segment-refusal")
  private implicit val materializer: Materializer = Materializer(system)

  private val service = GeminiServiceFactory(apiKey = "k")

  override protected def afterAll(): Unit = {
    service.close()
    system.terminate()
    ()
  }

  private def refused(call: => Future[_]): Throwable =
    Try(call) match {
      case Failure(e)      => fail(s"thrown synchronously: $e")
      case Success(future) => Await.result(future.failed, 20.seconds)
    }

  private def refusedStream(call: => Source[_, _]): Throwable =
    Try(call) match {
      case Failure(e)      => fail(s"thrown synchronously: $e")
      case Success(source) => Await.result(source.runWith(Sink.ignore).failed, 20.seconds)
    }

  "A refused path id" should {

    "fail a Gemini call, never throw" in {
      val contents = Seq(Content(Seq(Part.Text("Hi")), Some(ChatRole.User)))
      val settings = GenerateContentSettings(model = "models/..")

      refused(service.generateContent(contents, settings)) shouldBe an[
        IllegalArgumentException
      ]
      refusedStream(service.generateContentStreamed(contents, settings)) shouldBe
        an[IllegalArgumentException]
      refused(service.getFile("files/.")) shouldBe an[IllegalArgumentException]
      refused(service.getBatch("..")) shouldBe an[IllegalArgumentException]
      refused(service.getCachedContent("")) shouldBe an[IllegalArgumentException]
    }
  }
}
