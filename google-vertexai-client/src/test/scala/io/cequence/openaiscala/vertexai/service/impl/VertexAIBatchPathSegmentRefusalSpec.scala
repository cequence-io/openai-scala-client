package io.cequence.openaiscala.vertexai.service.impl

import io.cequence.openaiscala.vertexai.service.VertexAIServiceFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * A batch job id the client refuses ("", "." or "..") fails the Vertex AI batch calls as a
 * failed Future, never a synchronous throw - before any request, and before the credentials
 * (looked up lazily) are needed.
 */
class VertexAIBatchPathSegmentRefusalSpec
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  private val service =
    VertexAIServiceFactory.batchPrediction(projectId = "p", location = Some("us-central1"))

  override protected def afterAll(): Unit =
    service.close()

  private def refused(call: => Future[_]): Throwable =
    Try(call) match {
      case Failure(e)      => fail(s"thrown synchronously: $e")
      case Success(future) => Await.result(future.failed, 20.seconds)
    }

  "A refused batch job id" should {

    "fail the batch calls, never throw" in {
      refused(service.getBatchPredictionJob("..")) shouldBe an[IllegalArgumentException]
      refused(service.cancelBatchPredictionJob("")) shouldBe an[IllegalArgumentException]
      refused(
        service.deleteBatchPredictionJob("projects/p/batchPredictionJobs/.")
      ) shouldBe an[IllegalArgumentException]
    }
  }
}
