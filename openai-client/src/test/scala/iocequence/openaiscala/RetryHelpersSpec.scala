package iocequence.openaiscala

import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.TestKit
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.{
  OpenAIScalaClientTimeoutException,
  OpenAIScalaClientUnknownHostException,
  RetryHelpers
}
import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
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
    with ScalaFutures
    with RetryHelpers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val patience: PatienceConfig = PatienceConfig(timeout = 10.seconds)
  override def patienceConfig: PatienceConfig = patience

  "RetryHelpers" should {

    implicit val retrySettings: RetrySettings = RetrySettings()
    implicit val scheduler: Scheduler = actorSystem.scheduler
    val successfulResult = 42

    "retry when encountering a retryable failure" in {
      val attempts = 2
      val future = Promise[Int]().future
      val mockRetryable = mock[Retryable]
      when(mockRetryable.attempt())
        .thenReturn(
          Future.failed(
            new OpenAIScalaClientTimeoutException("retryable test exception")
          ),
          Future.successful(successfulResult)
        )

      val result = future.retry(() => mockRetryable.attempt(), attempts)

      result.futureValue shouldBe successfulResult
      whenReady(result) { _ =>
        verify(mockRetryable, times(attempts)).attempt()
      }
    }

    "not retry when encountering a non-retryable failure" in {
      val attempts = 2
      val future = Promise[Int]().future
      val mockRetryable = mock[Retryable]
      val testException = new OpenAIScalaClientUnknownHostException(
        "non retryable test exception"
      )
      when(mockRetryable.attempt())
        .thenReturn(
          Future.failed(testException),
          Future.successful(successfulResult)
        )
      val resultFuture = future.retry(() => mockRetryable.attempt(), attempts)

      Await.result(resultFuture.failed, 10.seconds) should be(testException)
      verify(mockRetryable, times(1)).attempt()
    }

    "not retry on success" in {
      val future = Future.successful(successfulResult)
      val result = future.retryOnFailure

      result.futureValue shouldBe successfulResult
    }
  }

  override def actorSystem: ActorSystem = system
}

trait Retryable {
  def attempt(): Future[Int]
}
