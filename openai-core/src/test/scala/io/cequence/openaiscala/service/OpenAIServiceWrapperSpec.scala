package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.{ChatRole, MessageSpec}
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.File
import scala.concurrent.Future

class OpenAIServiceWrapperSpec
    extends AnyWordSpecLike
    with should.Matchers
    with ScalaFutures
    with OpenAIServiceConsts
    with MockitoSugar {

  "OpenAIServiceWrapper" should {

    val modelInfo =
      ModelInfo(
        "test-model",
        new java.util.Date(0L),
        owned_by = "test_owner",
        root = "test_root",
        parent = None,
        permission = Array[Permission]()
      )

    val models = Seq(modelInfo)

    val imageInfo =
      ImageInfo(created = new java.util.Date(0L), Seq[Map[String, String]]())

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

    def testWrapWith[T](fixture: T)(block: OpenAIService => Future[T]): Unit = {
      val mockService = mock[OpenAIService]
      val wrapper = new MockWrapper(mockService)
      when(block(mockService)).thenReturn(Future.successful(fixture))
      val result = block(wrapper)
      result.futureValue shouldBe fixture
      whenReady(result) { _ =>
        wrapper.called shouldBe true
      }
    }

    "call wrap for listModels" in {
      testWrapWith(models) { _.listModels }
    }

    "call wrap for retrieveModel" in {
      val fixture: Option[ModelInfo] = Some(modelInfo)
      testWrapWith(fixture) { _.retrieveModel(modelInfo.id) }
    }

    "call wrap for createCompletion" in {
      val completion = TextCompletionResponse(
        id = "test-id",
        created = new java.util.Date(0L),
        model = "test-model",
        choices = Seq[TextCompletionChoiceInfo](),
        usage = None
      )
      testWrapWith(completion) {
        _.createCompletion("test-prompt", DefaultSettings.CreateCompletion)
      }
    }

    "call wrap for createChatCompletion" in {
      val completion = ChatCompletionResponse(
        id = "test-id",
        created = new java.util.Date(0L),
        model = "test-model",
        choices = Seq[ChatCompletionChoiceInfo](),
        usage = None
      )
      testWrapWith(completion) {
        _.createChatCompletion(
          Seq(MessageSpec(role = ChatRole.User, "test-prompt")),
          DefaultSettings.CreateChatCompletion
        )
      }
    }

    "call wrap for createEdit" in {
      val response = TextEditResponse(
        created = new java.util.Date(0L),
        choices = Seq[TextEditChoiceInfo](),
        usage = UsageInfo(0, 0, None)
      )
      testWrapWith(response) {
        _.createEdit(
          "test-input",
          "test-instructions",
          DefaultSettings.CreateEdit
        )
      }
    }

    "call wrap for createImage" in {
      testWrapWith(imageInfo) {
        _.createImage("test-prompt", DefaultSettings.CreateImage)
      }
    }

    "call wrap for createImageEdit" in {
      val testFile = mock[File]
      testWrapWith(imageInfo) {
        _.createImageEdit(
          "test-prompt",
          testFile,
          None,
          DefaultSettings.CreateImageEdit
        )
      }
    }

  }

}
