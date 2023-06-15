package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.{FunMessageSpec, FunctionSpec, MessageSpec}
import io.cequence.openaiscala.domain.settings._

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

trait OpenAIServiceWrapper extends OpenAIService {

  override def listModels = wrap(
    _.listModels
  )

  override def retrieveModel(
    modelId: String
  ) = wrap(
    _.retrieveModel(modelId)
  )

  override def createCompletion(
    prompt: String,
    settings: CreateCompletionSettings
  ) = wrap(
    _.createCompletion(prompt, settings)
  )

  override def createChatCompletion(
    messages: Seq[MessageSpec],
    settings: CreateChatCompletionSettings
  ) = wrap(
    _.createChatCompletion(messages, settings)
  )

  override def createChatFunCompletion(
    messages: Seq[FunMessageSpec],
    functions: Seq[FunctionSpec],
    responseFunctionName: Option[String],
    settings: CreateChatCompletionSettings
  ) = wrap(
    _.createChatFunCompletion(messages, functions, responseFunctionName, settings)
  )

  override def createEdit(
    input: String,
    instruction: String,
    settings: CreateEditSettings
  ) = wrap(
    _.createEdit(input, instruction, settings)
  )

  override def createImage(
    prompt: String,
    settings: CreateImageSettings
  ) = wrap(
    _.createImage(prompt, settings)
  )

  override def createImageEdit(
    prompt: String,
    image: File,
    mask: Option[File],
    settings: CreateImageSettings
  ) = wrap(
    _.createImageEdit(prompt, image, mask, settings)
  )

  override def createImageVariation(
    image: File,
    settings: CreateImageSettings
  ) = wrap(
    _.createImageVariation(image, settings)
  )

  override def createEmbeddings(
    input: Seq[String],
    settings: CreateEmbeddingsSettings
  ) = wrap(
    _.createEmbeddings(input, settings)
  )

  override def createAudioTranscription(
    file: File,
    prompt: Option[String],
    settings: CreateTranscriptionSettings
  ) = wrap(
    _.createAudioTranscription(file, prompt, settings)
  )

  override def createAudioTranslation(
    file: File,
    prompt: Option[String],
    settings: CreateTranslationSettings
  ) = wrap(
    _.createAudioTranslation(file, prompt, settings)
  )

  override def listFiles = wrap(
    _.listFiles
  )

  override def uploadFile(
    file: File,
    displayFileName: Option[String],
    settings: UploadFileSettings
  ) = wrap(
    _.uploadFile(file, displayFileName, settings)
  )

  override def deleteFile(
    fileId: String
  ) = wrap(
    _.deleteFile(fileId)
  )

  override def retrieveFile(
    fileId: String
  ) = wrap(
    _.retrieveFile(fileId)
  )

  override def retrieveFileContent(
    fileId: String
  ) = wrap(
    _.retrieveFileContent(fileId)
  )

  override def createFineTune(
    training_file: String,
    validation_file: Option[String],
    settings: CreateFineTuneSettings
  ) = wrap(
    _.createFineTune(training_file, validation_file, settings)
  )

  override def listFineTunes = wrap(
    _.listFineTunes
  )

  override def retrieveFineTune(
    fineTuneId: String
  ) = wrap(
    _.retrieveFineTune(fineTuneId)
  )

  override def cancelFineTune(
    fineTuneId: String
  ) = wrap(
    _.cancelFineTune(fineTuneId)
  )

  override def listFineTuneEvents(
    fineTuneId: String
  ) = wrap(
    _.listFineTuneEvents(fineTuneId)
  )

  override def deleteFineTuneModel(
    modelId: String
  ) = wrap(
    _.deleteFineTuneModel(modelId)
  )

  override def createModeration(
    input: String,
    settings: CreateModerationSettings
  ) = wrap(
    _.createModeration(input, settings)
  )

  protected def wrap[T](
    fun: OpenAIService => Future[T]
  ): Future[T]
}