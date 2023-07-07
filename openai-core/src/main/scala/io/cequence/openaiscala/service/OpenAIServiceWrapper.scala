package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.{FunMessageSpec, FunctionSpec, MessageSpec}
import io.cequence.openaiscala.domain.settings._

import java.io.File
import scala.concurrent.Future
import io.cequence.openaiscala.domain.response._

trait OpenAIServiceWrapper extends OpenAIService {

  override def listModels: Future[Seq[ModelInfo]] = wrap(
    _.listModels
  )

  override def retrieveModel(
    modelId: String
  ): Future[Option[ModelInfo]] = wrap(
    _.retrieveModel(modelId)
  )

  override def createCompletion(
    prompt: String,
    settings: CreateCompletionSettings
  ): Future[TextCompletionResponse] = wrap(
    _.createCompletion(prompt, settings)
  )

  override def createChatCompletion(
    messages: Seq[MessageSpec],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = wrap(
    _.createChatCompletion(messages, settings)
  )

  override def createChatFunCompletion(
    messages: Seq[FunMessageSpec],
    functions: Seq[FunctionSpec],
    responseFunctionName: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatFunCompletionResponse] = wrap(
    _.createChatFunCompletion(messages, functions, responseFunctionName, settings)
  )

  override def createEdit(
    input: String,
    instruction: String,
    settings: CreateEditSettings
  ): Future[TextEditResponse] = wrap(
    _.createEdit(input, instruction, settings)
  )

  override def createImage(
    prompt: String,
    settings: CreateImageSettings
  ): Future[ImageInfo] = wrap(
    _.createImage(prompt, settings)
  )

  override def createImageEdit(
    prompt: String,
    image: File,
    mask: Option[File],
    settings: CreateImageSettings
  ): Future[ImageInfo] = wrap(
    _.createImageEdit(prompt, image, mask, settings)
  )

  override def createImageVariation(
    image: File,
    settings: CreateImageSettings
  ): Future[ImageInfo] = wrap(
    _.createImageVariation(image, settings)
  )

  override def createEmbeddings(
    input: Seq[String],
    settings: CreateEmbeddingsSettings
  ): Future[EmbeddingResponse] = wrap(
    _.createEmbeddings(input, settings)
  )

  override def createAudioTranscription(
    file: File,
    prompt: Option[String],
    settings: CreateTranscriptionSettings
  ): Future[TranscriptResponse] = wrap(
    _.createAudioTranscription(file, prompt, settings)
  )

  override def createAudioTranslation(
    file: File,
    prompt: Option[String],
    settings: CreateTranslationSettings
  ): Future[TranscriptResponse] = wrap(
    _.createAudioTranslation(file, prompt, settings)
  )

  override def listFiles: Future[Seq[FileInfo]] = wrap(
    _.listFiles
  )

  override def uploadFile(
    file: File,
    displayFileName: Option[String],
    settings: UploadFileSettings
  ): Future[FileInfo] = wrap(
    _.uploadFile(file, displayFileName, settings)
  )

  override def deleteFile(
    fileId: String
  ): Future[DeleteResponse] = wrap(
    _.deleteFile(fileId)
  )

  override def retrieveFile(
    fileId: String
  ): Future[Option[FileInfo]] = wrap(
    _.retrieveFile(fileId)
  )

  override def retrieveFileContent(
    fileId: String
  ): Future[Option[String]] = wrap(
    _.retrieveFileContent(fileId)
  )

  override def createFineTune(
    training_file: String,
    validation_file: Option[String],
    settings: CreateFineTuneSettings
  ): Future[FineTuneJob] = wrap(
    _.createFineTune(training_file, validation_file, settings)
  )

  override def listFineTunes: Future[Seq[FineTuneJob]] = wrap(
    _.listFineTunes
  )

  override def retrieveFineTune(
    fineTuneId: String
  ): Future[Option[FineTuneJob]] = wrap(
    _.retrieveFineTune(fineTuneId)
  )

  override def cancelFineTune(
    fineTuneId: String
  ): Future[Option[FineTuneJob]] = wrap(
    _.cancelFineTune(fineTuneId)
  )

  override def listFineTuneEvents(
    fineTuneId: String
  ): Future[Option[Seq[FineTuneEvent]]] = wrap(
    _.listFineTuneEvents(fineTuneId)
  )

  override def deleteFineTuneModel(
    modelId: String
  ): Future[DeleteResponse] = wrap(
    _.deleteFineTuneModel(modelId)
  )

  override def createModeration(
    input: String,
    settings: CreateModerationSettings
  ): Future[ModerationResponse] = wrap(
    _.createModeration(input, settings)
  )

  protected def wrap[T](
    fun: OpenAIService => Future[T]
  ): Future[T]
}
