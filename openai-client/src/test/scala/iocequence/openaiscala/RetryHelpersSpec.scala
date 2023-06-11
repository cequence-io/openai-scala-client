package iocequence.openaiscala

import akka.actor.{ActorSystem, Scheduler}
import akka.testkit.TestKit
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaClientTimeoutException,
  OpenAIScalaClientUnknownHostException,
  RetryHelpers
}
import org.mockito.scalatest.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import org.scalatest.RecoverMethods._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

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

    def testWithResults(attempts: Int, results: Seq[Future[Int]])(
        test: (Retryable, Future[Int]) => Unit
    ): Unit = {
      val future = Promise[Int]().future
      val mockRetryable = mock[Retryable]
      when(mockRetryable.attempt())
        .thenReturn(results.head, results.takeRight(results.length - 1): _*)
      val result = future.retry(() => mockRetryable.attempt(), attempts)
      test(mockRetryable, result)
    }

    def testWithException(ex: OpenAIScalaClientException, attempts: Int)(
        test: (Retryable, Future[Int]) => Unit
    ): Unit = {
      testWithResults(
        attempts,
        Seq(Future.failed(ex), Future.successful(successfulResult))
      )(test)
    }

    "retry when encountering a retryable failure" in {
      val attempts = 2
      val ex = new OpenAIScalaClientTimeoutException("retryable test exception")
      testWithException(ex, attempts) { (mockRetryable, result) =>
        result.futureValue shouldBe successfulResult
        whenReady(result) { _ =>
          verify(mockRetryable, times(attempts)).attempt()
        }
      }
    }

    "not retry when encountering a non-retryable failure" in {
      val attempts = 2
      val ex = new OpenAIScalaClientUnknownHostException(
        "non retryable test exception"
      )
      testWithException(ex, attempts) { (mockRetryable, result) =>
        val f = for {
          _ <- recoverToExceptionIf[OpenAIScalaClientUnknownHostException](
            result
          )
        } yield mockRetryable
        whenReady(f) { mockRetryable =>
          verify(mockRetryable, times(1)).attempt()
        }
      }
    }

    "not retry on success" in {
      testWithResults(attempts = 2, Seq(Future.successful(successfulResult))) { (mockRetryable, result) =>
        result.futureValue shouldBe successfulResult
        whenReady(result.map(_ => mockRetryable)) {  mockRetryable =>
          verify(mockRetryable, times(1)).attempt()
        }
      }
    }
  }

  override def actorSystem: ActorSystem = system
}

trait Retryable {
  def attempt(): Future[Int]
}
