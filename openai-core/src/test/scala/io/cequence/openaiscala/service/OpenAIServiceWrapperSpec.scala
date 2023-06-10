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

    val testFile = mock[File]

    val imageInfo =
      ImageInfo(created = new java.util.Date(0L), Seq[Map[String, String]]())

    val transcriptResponse = TranscriptResponse("test-response", None)

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
      testWrapWith(imageInfo) {
        _.createImageEdit(
          "test-prompt",
          testFile,
          None,
          DefaultSettings.CreateImageEdit
        )
      }
    }

    "call wrap for createImageVariation" in {
      testWrapWith(imageInfo) {
        _.createImageVariation(testFile, DefaultSettings.CreateImageVariation)
      }
    }

    "call wrap for createEmbeddings" in {
      val response = EmbeddingResponse(
        Seq[EmbeddingInfo](),
        "test-model",
        EmbeddingUsageInfo(0, 0)
      )
      testWrapWith(response) {
        _.createEmbeddings(Seq[String](), DefaultSettings.CreateEmbeddings)
      }
    }

    "call wrap for createAudioTranscription" in {
      testWrapWith(transcriptResponse) {
        _.createAudioTranscription(
          testFile,
          Some("test-prompt"),
          DefaultSettings.CreateTranscription
        )
      }
    }

    "call wrap for createAudioTranslation" in {
      testWrapWith(transcriptResponse) {
        _.createAudioTranslation(
          testFile,
          Some("test-prompt"),
          DefaultSettings.CreateTranslation
        )
      }
    }

    "call wrap for listFiles" in {
      val response = Seq[FileInfo]()
      testWrapWith(response) { _.listFiles }
    }

    "call wrap for uploadFile" in {
      val response = FileInfo(
        "test-id",
        0,
        new java.util.Date(0L),
        filename = "test-filename",
        purpose = "test-purpose",
        status = "test-status",
        status_details = None
      )
      testWrapWith(response) {
        _.uploadFile(testFile, Some("test-name"), DefaultSettings.UploadFile)
      }
    }

    "call wrap for deleteFile" in {
      val response: DeleteResponse = DeleteResponse.Deleted
      testWrapWith(response) {
        _.deleteFile("test-file-id")
      }
    }

    "call wrap for retrieveFile" in {
      val response: Option[FileInfo] = None
      testWrapWith(response) { _.retrieveFile("test-file-id") }
    }

    "call wrap for retrieveFileContent" in {
      val response: Option[String] = None
      testWrapWith(response) { _.retrieveFileContent("test-file-id") }
    }

    "call wrap for createFineTune" in {
      def testFiles = Seq[FileInfo]()
      val response = FineTuneJob(
        id = "test-id",
        model = "test-model",
        created_at = new java.util.Date(0L),
        events = None,
        fine_tuned_model = None,
        hyperparams = FineTuneHyperparams(None, None, 0, 0.0),
        organization_id = "test-org",
        result_files = testFiles,
        status = "test-status",
        validation_files = testFiles,
        training_files = testFiles,
        updated_at = new java.util.Date(0L)
      )
      testWrapWith(response) {
        _.createFineTune("test-file", None, DefaultSettings.CreateFineTune)
      }
    }

    "call wrap for listFineTunes" in {
      val response = Seq[FineTuneJob]()
      testWrapWith(response) { _.listFineTunes }
    }

    "call wrap for cancelFineTune" in {
      val response: Option[FineTuneJob] = None
      testWrapWith(response) { _.retrieveFineTune("test-fine-tune-id") }
    }

  }

}
