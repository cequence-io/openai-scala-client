package io.cequence.openaiscala.service.adapter

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.domain.Batch._
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.service.adapter.ServiceWrapperTypes._
import io.cequence.openaiscala.service.{OpenAIChatCompletionService, OpenAICoreService, OpenAIService}
import io.cequence.wsclient.service.adapter.DelegatedCloseableServiceWrapper
import io.cequence.wsclient.service.adapter.ServiceWrapperTypes.CloseableServiceWrapper

import java.io.File
import scala.concurrent.Future

private class OpenAIServiceWrapperImpl(
  val delegate: CloseableServiceWrapper[OpenAIService]
) extends OpenAIServiceWrapper

private class OpenAIServiceExtWrapperImpl(
  val delegate: ChatCompletionCloseableServiceWrapper[OpenAIService]
) extends OpenAIServiceWrapper
    with DelegatedChatCompletionCloseableServiceWrapper[OpenAIService]

trait OpenAIServiceWrapper
    extends DelegatedCloseableServiceWrapper[OpenAIService, CloseableServiceWrapper[
      OpenAIService
    ]]
    with OpenAICoreServiceWrapper
    with OpenAIService {

  override def retrieveModel(
    modelId: String
  ): Future[Option[ModelInfo]] = wrap(
    _.retrieveModel(modelId)
  )

  override def createChatFunCompletion(
    messages: Seq[BaseMessage],
    functions: Seq[ChatCompletionTool],
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

  def createRun(
    threadId: String,
    assistantId: String,
    instructions: Option[String],
    additionalInstructions: Option[String],
    additionalMessages: Seq[BaseMessage],
    tools: Seq[AssistantTool],
    responseToolChoice: Option[ToolChoice] = None,
    settings: CreateRunSettings = DefaultSettings.CreateRun,
    stream: Boolean
  ): Future[Run] = wrap(
    _.createRun(
      threadId,
      assistantId,
      instructions,
      additionalInstructions,
      additionalMessages,
      tools,
      responseToolChoice,
      settings,
      stream
    )
  )

  override def cancelRun(
    threadId: String,
    runId: String
  ): Future[Run] = wrap(
    _.cancelRun(threadId, runId)
  )

  override def modifyRun(
    threadId: String,
    runId: String,
    metadata: Map[String, String]
  ): Future[Run] = wrap(
    _.modifyRun(threadId, runId, metadata)
  )

  def submitToolOutputs(
    threadId: String,
    runId: String,
    toolOutputs: Seq[AssistantToolOutput],
    stream: Boolean
  ): Future[Run] = wrap(
    _.submitToolOutputs(threadId, runId, toolOutputs, stream)
  )

  def retrieveRun(
    threadId: String,
    runId: String
  ): Future[Option[Run]] = wrap(
    _.retrieveRun(threadId, runId)
  )

  override def retrieveRunStep(
    threadID: String,
    runId: String,
    stepId: String
  ): Future[Option[RunStep]] =
    wrap(_.retrieveRunStep(threadID, runId, stepId))

  override def listRunSteps(
    threadId: String,
    runId: String,
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[RunStep]] = wrap(
    _.listRunSteps(threadId, runId, pagination, order)
  )

  override def listRuns(
    threadId: String,
    pagination: Pagination,
    order: Option[SortOrder] = None
  ): Future[Seq[Run]] = wrap(
    _.listRuns(threadId, pagination, order)
  )

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] = wrap(
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

  override def createAudioSpeech(
    input: String,
    settings: CreateSpeechSettings
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
    purpose: FileUploadPurpose
  ): Future[FileInfo] = wrap(
    _.uploadFile(file, displayFileName, purpose)
  )

  override def uploadBatchFile(
    file: File,
    displayFileName: Option[String]
  ): Future[FileInfo] =
    wrap(
      _.uploadBatchFile(file, displayFileName)
    )

  override def buildAndUploadBatchFile(
    model: String,
    requests: Seq[BatchRowBase],
    displayFileName: Option[String]
  ): Future[FileInfo] =
    wrap(
      _.buildAndUploadBatchFile(model, requests, displayFileName)
    )

  override def buildBatchFileContent(
    model: String,
    requests: Seq[BatchRowBase]
  ): Future[Seq[BatchRow]] =
    wrap(
      _.buildBatchFileContent(model, requests)
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

  override def retrieveFileContentAsSource(
    fileId: String
  ): Future[Option[Source[ByteString, _]]] = wrap(
    _.retrieveFileContentAsSource(fileId)
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

  override def listFineTuneCheckpoints(
    fineTuneId: String,
    after: Option[String],
    limit: Option[Int]
  ): Future[Option[Seq[FineTuneCheckpoint]]] = wrap(
    _.listFineTuneCheckpoints(fineTuneId, after, limit)
  )

  override def deleteFineTuneModel(
    modelId: String
  ): Future[DeleteResponse] = wrap(
    _.deleteFineTuneModel(modelId)
  )

  override def createVectorStore(
    fileIds: Seq[String],
    name: Option[String],
    metadata: Map[String, Any]
  ): Future[VectorStore] = wrap(
    _.createVectorStore(fileIds, name, metadata)
  )

  override def modifyVectorStore(
    vectorStoreId: String,
    name: Option[String],
    metadata: Map[String, Any]
  ): Future[VectorStore] = wrap(
    _.modifyVectorStore(vectorStoreId, name, metadata)
  )

  override def listVectorStores(
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[VectorStore]] = wrap(
    _.listVectorStores(pagination, order)
  )

  override def retrieveVectorStore(
    vectorStoreId: String
  ): Future[Option[VectorStore]] = wrap(
    _.retrieveVectorStore(vectorStoreId)
  )

  override def deleteVectorStore(
    vectorStoreId: String
  ): Future[DeleteResponse] = wrap(
    _.deleteVectorStore(vectorStoreId)
  )

  override def createVectorStoreFile(
    vectorStoreId: String,
    fileId: String,
    chunkingStrategy: ChunkingStrategy = ChunkingStrategy.AutoChunkingStrategy
  ): Future[VectorStoreFile] =
    wrap(
      _.createVectorStoreFile(vectorStoreId, fileId, chunkingStrategy)
    )

  def listVectorStoreFiles(
    vectorStoreId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None,
    filter: Option[VectorStoreFileStatus] = None
  ): Future[Seq[VectorStoreFile]] =
    wrap(
      _.listVectorStoreFiles(vectorStoreId, pagination, order, filter)
    )

  def retrieveVectorStoreFile(
    vectorStoreId: String,
    fileId: FileId
  ): Future[VectorStoreFile] =
    wrap(
      _.retrieveVectorStoreFile(vectorStoreId, fileId)
    )

  def deleteVectorStoreFile(
    vectorStoreId: String,
    fileId: String
  ): Future[DeleteResponse] =
    wrap(
      _.deleteVectorStoreFile(vectorStoreId, fileId)
    )

  override def createModeration(
    input: String,
    settings: CreateModerationSettings
  ): Future[ModerationResponse] = wrap(
    _.createModeration(input, settings)
  )

  override def createThread(
    messages: Seq[ThreadMessage],
    toolResources: Seq[AssistantToolResource] = Nil,
    metadata: Map[String, String]
  ): Future[Thread] = wrap(
    _.createThread(messages, toolResources, metadata)
  )

  override def createThreadAndRun(
    assistantId: String,
    thread: Option[ThreadAndRun],
    instructions: Option[String],
    tools: Seq[AssistantTool],
    toolResources: Option[ThreadAndRunToolResource],
    toolChoice: Option[ToolChoice],
    settings: CreateThreadAndRunSettings,
    stream: Boolean
  ): Future[Run] =
    wrap(
      _.createThreadAndRun(
        assistantId,
        thread,
        instructions,
        tools,
        toolResources,
        toolChoice,
        settings,
        stream
      )
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
    attachments: Seq[Attachment],
    metadata: Map[String, String]
  ): Future[ThreadFullMessage] = wrap(
    _.createThreadMessage(threadId, content, role, attachments, metadata)
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
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[ThreadFullMessage]] = wrap(
    _.listThreadMessages(threadId, pagination, order)
  )

  def deleteThreadMessage(
    threadId: String,
    messageId: String
  ): Future[DeleteResponse] = wrap(
    _.deleteThreadMessage(threadId, messageId)
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
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[ThreadMessageFile]] = wrap(
    _.listThreadMessageFiles(threadId, messageId, pagination, order)
  )

  override def createAssistant(
    model: String,
    name: Option[String] = None,
    description: Option[String] = None,
    instructions: Option[String] = None,
    tools: Seq[AssistantTool] = Seq.empty[AssistantTool],
    toolResources: Option[AssistantToolResource] = None,
    metadata: Map[String, String] = Map.empty
  ): Future[Assistant] = wrap(
    _.createAssistant(model, name, description, instructions, tools, toolResources, metadata)
  )

  override def listAssistants(
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[Assistant]] =
    wrap(
      _.listAssistants(pagination, order)
    )

  override def retrieveAssistant(assistantId: String): Future[Option[Assistant]] =
    wrap(_.retrieveAssistant(assistantId))

  override def modifyAssistant(
    assistantId: String,
    model: Option[String],
    name: Option[String],
    description: Option[String],
    instructions: Option[String],
    tools: Seq[AssistantTool],
    fileIds: Seq[String],
    metadata: Map[String, String]
  ): Future[Option[Assistant]] =
    wrap(
      _.modifyAssistant(
        assistantId,
        model,
        name,
        description,
        instructions,
        tools,
        fileIds,
        metadata
      )
    )

  override def deleteAssistant(assistantId: String): Future[DeleteResponse] =
    wrap(_.deleteAssistant(assistantId))

  override def deleteAssistantFile(
    assistantId: String,
    fileId: String
  ): Future[DeleteResponse] =
    wrap(_.deleteAssistantFile(assistantId, fileId))

  override def createBatch(
    inputFileId: String,
    endpoint: BatchEndpoint,
    completionWindow: CompletionWindow,
    metadata: Map[String, String]
  ): Future[Batch] =
    wrap(
      _.createBatch(inputFileId, endpoint, completionWindow, metadata)
    )

  override def retrieveBatch(batchId: String): Future[Option[Batch]] =
    wrap(_.retrieveBatch(batchId))

  override def retrieveBatchFile(batchId: String): Future[Option[FileInfo]] =
    wrap(_.retrieveBatchFile(batchId))

  override def retrieveBatchFileContent(batchId: String): Future[Option[String]] =
    wrap(_.retrieveBatchFileContent(batchId))

  override def retrieveBatchResponses(batchId: String): Future[Option[CreateBatchResponses]] =
    wrap(_.retrieveBatchResponses(batchId))

  override def cancelBatch(batchId: String): Future[Option[Batch]] =
    wrap(_.cancelBatch(batchId))

  override def listBatches(
    pagination: Pagination,
    order: Option[SortOrder]
  ): Future[Seq[Batch]] =
    wrap(_.listBatches(pagination, order))

}

private class OpenAICoreServiceWrapperImpl(
  val delegate: CloseableServiceWrapper[OpenAICoreService]
) extends OpenAICoreServiceWrapper

private class OpenAICoreServiceExtWrapperImpl(
  val delegate: ChatCompletionCloseableServiceWrapper[OpenAICoreService]
) extends OpenAICoreServiceWrapper
    with DelegatedChatCompletionCloseableServiceWrapper[OpenAICoreService]

trait OpenAICoreServiceWrapper
    extends DelegatedCloseableServiceWrapper[OpenAICoreService, CloseableServiceWrapper[
      OpenAICoreService
    ]]
    with OpenAIChatCompletionServiceWrapper
    with OpenAICoreService {

  override def listModels: Future[Seq[ModelInfo]] = wrap(
    _.listModels
  )

  override def createCompletion(
    prompt: String,
    settings: CreateCompletionSettings
  ): Future[TextCompletionResponse] = wrap(
    _.createCompletion(prompt, settings)
  )

  override def createEmbeddings(
    input: Seq[String],
    settings: CreateEmbeddingsSettings
  ): Future[EmbeddingResponse] = wrap(
    _.createEmbeddings(input, settings)
  )
}

private class OpenAIChatCompletionServiceWrapperImpl(
  val delegate: CloseableServiceWrapper[OpenAIChatCompletionService]
) extends OpenAIChatCompletionServiceWrapper

private class OpenAIChatCompletionServiceExtWrapperImpl(
  val delegate: ChatCompletionCloseableServiceWrapper[OpenAIChatCompletionService]
) extends OpenAIChatCompletionServiceWrapper
    with DelegatedChatCompletionCloseableServiceWrapper[OpenAIChatCompletionService]

trait OpenAIChatCompletionServiceWrapper
    extends DelegatedCloseableServiceWrapper[
      OpenAIChatCompletionService,
      CloseableServiceWrapper[OpenAIChatCompletionService]
    ]
    with OpenAIChatCompletionService {

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = wrap(
    _.createChatCompletion(messages, settings)
  )

}
