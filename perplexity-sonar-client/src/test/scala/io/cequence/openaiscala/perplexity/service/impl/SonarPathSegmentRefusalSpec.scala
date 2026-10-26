package io.cequence.openaiscala.perplexity.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.cequence.openaiscala.perplexity.service.SonarServiceFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

/**
 * A response id the client refuses ("", "." or "..") fails the Perplexity Agent API calls as a
 * failed Future or stream, never a synchronous throw - nothing is sent (a dummy key).
 */
class SonarPathSegmentRefusalSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("sonar-path-segment-refusal")
  private implicit val materializer: Materializer = Materializer(system)

  private val service = SonarServiceFactory(apiKey = "k")

  override protected def afterAll(): Unit = {
    service.close()
    system.terminate()
    ()
  }

  "A refused response id" should {

    "fail the retrieval as a failed Future, never a throw" in {
      val failure = Try(service.retrieveAgentResponse("..")) match {
        case Failure(e)      => fail(s"thrown synchronously: $e")
        case Success(future) => Await.result(future.failed, 20.seconds)
      }
      failure shouldBe an[IllegalArgumentException]
    }

    "fail the resumed stream, never throw" in {
      val failure = Try(service.resumeAgentResponseStream(".")) match {
        case Failure(e)      => fail(s"thrown synchronously: $e")
        case Success(source) => Await.result(source.runWith(Sink.ignore).failed, 20.seconds)
      }
      failure shouldBe an[IllegalArgumentException]
    }
  }
}
