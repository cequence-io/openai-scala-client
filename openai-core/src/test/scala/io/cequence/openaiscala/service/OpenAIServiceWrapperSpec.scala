package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.response.{ModelInfo, Permission}
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future

class OpenAIServiceWrapperSpec
    extends AnyWordSpecLike
    with should.Matchers
    with ScalaFutures
    with MockitoSugar {

  "OpenAIServiceWrapper" should {

    class MockWrapper(val underlying: OpenAIService)
        extends OpenAIServiceWrapper {
      var called: Boolean = false
      override protected def wrap[T](
          fun: OpenAIService => Future[T]
      ): Future[T] = {
        called = true
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
      val result = wrapper.listModels
      result.futureValue should be(testModels)
      whenReady(result) { _: Seq[ModelInfo] =>
        wrapper.called shouldBe true
      }
    }
  }

}
