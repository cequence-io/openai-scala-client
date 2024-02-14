package io.cequence.openaiscala.service

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.domain.{
  AssistantTool,
  BaseMessage,
  ChatRole,
  FileId,
  FunctionSpec,
  SortOrder,
  Thread,
  ThreadFullMessage,
  ThreadMessage,
  ThreadMessageFile,
  ToolSpec
}
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
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = wrap(
    _.createChatCompletion(messages, settings)
  )

  override def createChatFunCompletion(
    messages: Seq[BaseMessage],
    functions: Seq[FunctionSpec],
    responseFunctionName: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatFunCompletionResponse] = wrap(
    _.createChatFunCompletion(
      messages,
      functions,
      responseFunctionName,
      settings
    )
  )

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ToolSpec],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ) = wrap(
    _.createChatToolCompletion(
      messages,
      tools,
      responseToolChoice,
      settings
    )
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
    settings: CreateImageEditSettings
  ): Future[ImageInfo] = wrap(
    _.createImageEdit(prompt, image, mask, settings)
  )

  override def createImageVariation(
    image: File,
    settings: CreateImageEditSettings
  ): Future[ImageInfo] = wrap(
    _.createImageVariation(image, settings)
  )

  override def createEmbeddings(
    input: Seq[String],
    settings: CreateEmbeddingsSettings
  ): Future[EmbeddingResponse] = wrap(
    _.createEmbeddings(input, settings)
  )

  override def createAudioSpeech(
    input: String,
    settings: CreateSpeechSettings = DefaultSettings.CreateSpeech
  ): Future[Source[ByteString, _]] = wrap(
    _.createAudioSpeech(input, settings)
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

  override def listFineTunes(
    after: Option[String],
    limit: Option[Int]
  ): Future[Seq[FineTuneJob]] = wrap(
    _.listFineTunes(after, limit)
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
    fineTuneId: String,
    after: Option[String],
    limit: Option[Int]
  ): Future[Option[Seq[FineTuneEvent]]] = wrap(
    _.listFineTuneEvents(fineTuneId, after, limit)
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

  override def createThread(
    messages: Seq[ThreadMessage],
    metadata: Map[String, String]
  ): Future[Thread] = wrap(
    _.createThread(messages, metadata)
  )

  override def retrieveThread(
    threadId: String
  ): Future[Option[Thread]] = wrap(
    _.retrieveThread(threadId)
  )

  override def modifyThread(
    threadId: String,
    metadata: Map[String, String]
  ): Future[Option[Thread]] = wrap(
    _.modifyThread(threadId, metadata)
  )

  override def deleteThread(
    threadId: String
  ): Future[DeleteResponse] = wrap(
    _.deleteThread(threadId)
  )

  override def createThreadMessage(
    threadId: String,
    content: String,
    role: ChatRole,
    fileIds: Seq[String],
    metadata: Map[String, String]
  ): Future[ThreadFullMessage] = wrap(
    _.createThreadMessage(threadId, content, role, fileIds, metadata)
  )

  override def retrieveThreadMessage(
    threadId: String,
    messageId: String
  ): Future[Option[ThreadFullMessage]] = wrap(
    _.retrieveThreadMessage(threadId, messageId)
  )

  override def modifyThreadMessage(
    threadId: String,
    messageId: String,
    metadata: Map[String, String]
  ): Future[Option[ThreadFullMessage]] = wrap(
    _.modifyThreadMessage(threadId, messageId, metadata)
  )

  override def listThreadMessages(
    threadId: String,
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    before: Option[String]
  ): Future[Seq[ThreadFullMessage]] = wrap(
    _.listThreadMessages(threadId, limit, order, after, before)
  )

  override def retrieveThreadMessageFile(
    threadId: String,
    messageId: String,
    fileId: String
  ): Future[Option[ThreadMessageFile]] = wrap(
    _.retrieveThreadMessageFile(threadId, messageId, fileId)
  )

  override def listThreadMessageFiles(
    threadId: String,
    messageId: String,
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    before: Option[String]
  ): Future[Seq[ThreadMessageFile]] = wrap(
    _.listThreadMessageFiles(threadId, messageId, limit, order, after, before)
  )

  override def createAssistant(
    model: String,
    name: Option[String],
    description: Option[String],
    instructions: Option[String],
    tools: Seq[AssistantTool],
    fileIds: Seq[String],
    metadata: Map[String, String]
  ): Future[Assistant] = wrap(
    _.createAssistant(model, name, description, instructions, tools, fileIds, metadata)
  )

  override def createAssistantFile(
    assistantId: String,
    fileId: String
  ): Future[AssistantFile] =
    wrap(
      _.createAssistantFile(assistantId, fileId)
    )

  override def listAssistants(
    limit: Option[Int],
    order: Option[SortOrder],
    after: Option[String],
    before: Option[String]
  ): Future[Seq[Assistant]] =
    wrap(
      _.listAssistants(limit, order, after, before)
    )

  protected def wrap[T](
    fun: OpenAIService => Future[T]
  ): Future[T]
}
