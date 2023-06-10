package iocequence.openaiscala

import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.TestKit
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.{OpenAIScalaClientTimeoutException, RetryHelpers}
import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class RetryHelpersSpec
    extends TestKit(ActorSystem("RetryHelpersSpec"))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MockitoSugar
    with RetryHelpers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "RetryHelpers" should {

    implicit val retrySettings: RetrySettings = RetrySettings()
    implicit val scheduler: Scheduler = actorSystem.scheduler

    "retry on failure" in {

      val attempts = 2
      val future = Promise[Int]().future
      val mockRetryable = mock[Retryable]
      when(mockRetryable.attempt())
        .thenReturn(
          Future.failed(
            new OpenAIScalaClientTimeoutException("test exception")
          ),
          Future.successful(42)
        )
      val resultFuture = future.retry(() => mockRetryable.attempt(), attempts)

      Await.result(resultFuture, 10.seconds) shouldBe 42
      verify(mockRetryable, times(attempts)).attempt()
    }

    "not retry on success" in {

      val future = Future.successful(42)
      val resultFuture = future.retryOnFailure

      Await.result(resultFuture, 2.seconds) shouldBe 42
    }
  }

  override def actorSystem: ActorSystem = system
}

trait Retryable {
  def attempt(): Future[Int]
}
