package io.cequence.openaiscala.anthropic.service.impl

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * A path id the client refuses ("", "." or "..") fails the Anthropic and Bedrock calls as a
 * failed Future or stream, never a synchronous throw - nothing is sent, so the services point
 * at no real host and hold dummy credentials.
 */
class AnthropicPathSegmentRefusalSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  private implicit val system: ActorSystem = ActorSystem("anthropic-path-segment-refusal")
  private implicit val materializer: Materializer = Materializer(system)

  private val service = AnthropicServiceFactory(apiKey = "k")
  private val bedrock =
    AnthropicServiceFactory.forBedrock(accessKey = "a", secretKey = "s", region = "us-east-1")

  override protected def afterAll(): Unit = {
    service.close()
    bedrock.close()
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

    "fail an Anthropic API call, never throw" in {
      refused(service.getSession("..")) shouldBe an[IllegalArgumentException]
      refused(service.getMessageBatch("")) shouldBe an[IllegalArgumentException]
      refusedStream(service.streamSessionEvents(".")) shouldBe an[IllegalArgumentException]
      refusedStream(service.streamMessageBatchResults("..")) shouldBe
        an[IllegalArgumentException]
    }

    "fail a Bedrock call for a refused model id, never throw" in {
      val settings = AnthropicCreateMessageSettings(model = "..", max_tokens = 10)
      val messages = Seq(Message.UserMessage("Hi"))

      refused(bedrock.createMessage(messages, settings)) shouldBe an[IllegalArgumentException]
      refusedStream(bedrock.createMessageStreamedEvents(messages, settings)) shouldBe
        an[IllegalArgumentException]
    }
  }
}
