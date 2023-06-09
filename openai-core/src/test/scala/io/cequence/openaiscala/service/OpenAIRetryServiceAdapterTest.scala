package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OpenAIRetryServiceAdapterTest
    extends AnyFlatSpec
    with Matchers
    with MockitoSugar
    with OpenAIServiceConsts {

  "OpenAIRetryServiceAdapter" should
    "retry on consistent failure until maxAttempts reached and then throw exception" in {

      val underlying = mock[OpenAIService]
      when(underlying.createCompletion(any, any)).thenReturn(
        Future.failed(new OpenAIScalaClientException("Test exception"))
      )
      val service = OpenAIRetryServiceAdapter(
        underlying,
        maxAttempts = 3,
        sleepOnFailureMs = None,
        log = println
      )

      val result = service.createCompletion(
        "test-prompt",
        DefaultSettings.CreateCompletion
      )

      assert(result.failed.futureValue.isInstanceOf[OpenAIScalaClientException])
      verify(underlying, times(3)).createCompletion(any, any)
    }
}
