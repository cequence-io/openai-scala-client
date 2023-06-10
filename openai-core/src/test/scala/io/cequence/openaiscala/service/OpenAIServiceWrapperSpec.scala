package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.response.{ModelInfo, Permission}
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future

class OpenAIServiceWrapperSpec
    extends AnyWordSpecLike
    with should.Matchers
    with MockitoSugar {

  "OpenAIServiceWrapper" should {

    class MockWrapper(val underlying: OpenAIService)
        extends OpenAIServiceWrapper {
      override protected def wrap[T](
          fun: OpenAIService => Future[T]
      ): Future[T] = {
        fun(underlying)
      }

      override def close: Unit = {}
    }

    "call wrap for listModels" in {
      val mockService = mock[OpenAIService]
      val wrapper = new MockWrapper(mockService)
      val testModels = Array(
        ModelInfo(
          "test",
          new java.util.Date(0L),
          owned_by = "test_owner",
          root = "test_root",
          parent = None,
          permission = Array[Permission]()
        )
      )
      when(mockService.listModels).thenReturn(Future.successful {
        testModels
      })
      wrapper.listModels.futureValue should be(testModels)
    }
  }

}
