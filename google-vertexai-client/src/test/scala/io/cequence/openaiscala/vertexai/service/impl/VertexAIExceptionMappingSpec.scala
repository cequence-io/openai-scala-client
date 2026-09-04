package io.cequence.openaiscala.vertexai.service.impl

import com.google.api.gax.grpc.GrpcStatusCode
import com.google.api.gax.rpc.{
  DeadlineExceededException,
  ResourceExhaustedException,
  UnavailableException
}
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaClientTimeoutException,
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException
}
import io.grpc.Status
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.CompletionException

class VertexAIExceptionMappingSpec extends AnyWordSpec with Matchers {

  "toOpenAIException" should {

    "unwrap a CompletionException and map ResourceExhaustedException to OpenAIScalaRateLimitException" in {
      val statusCode = GrpcStatusCode.of(Status.Code.RESOURCE_EXHAUSTED)
      val gaxException =
        new ResourceExhaustedException(new RuntimeException("quota"), statusCode, false)
      val wrapped = new CompletionException(gaxException)

      toOpenAIException(wrapped) shouldBe a[OpenAIScalaRateLimitException]
    }

    "map UnavailableException to OpenAIScalaEngineOverloadedException" in {
      val statusCode = GrpcStatusCode.of(Status.Code.UNAVAILABLE)
      val gaxException =
        new UnavailableException(new RuntimeException("unavailable"), statusCode, true)

      toOpenAIException(gaxException) shouldBe a[OpenAIScalaEngineOverloadedException]
    }

    "map DeadlineExceededException to OpenAIScalaClientTimeoutException" in {
      val statusCode = GrpcStatusCode.of(Status.Code.DEADLINE_EXCEEDED)
      val gaxException =
        new DeadlineExceededException(new RuntimeException("timeout"), statusCode, true)

      toOpenAIException(gaxException) shouldBe a[OpenAIScalaClientTimeoutException]
    }

    "leave a plain RuntimeException unchanged" in {
      val e = new RuntimeException("boom")

      toOpenAIException(e) shouldBe theSameInstanceAs(e)
    }

    "pass an already-OpenAIScalaClientException through unchanged" in {
      val e = new OpenAIScalaClientException("already mapped")

      toOpenAIException(e) shouldBe theSameInstanceAs(e)
    }
  }
}
