package io.cequence.openaiscala.service

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.domain.Batch._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain._

import java.io.File
import scala.concurrent.Future
import io.cequence.openaiscala.domain.responsesapi.Inputs
import io.cequence.openaiscala.domain.responsesapi.CreateModelResponseSettings
import io.cequence.openaiscala.domain.responsesapi.Response
import io.cequence.openaiscala.domain.responsesapi.{ DeleteResponse => ResponsesAPIDeleteResponse }
import io.cequence.openaiscala.domain.responsesapi.InputItemsResponse

/**
 * Central service to access all public OpenAI WS endpoints as defined at <a
 * href="https://platform.openai.com/docs/api-reference">the API ref. page</a>
 *
 * The following services are supported:
 *
 *   - '''Models''': listModels, and retrieveModel
 *   - '''Completions''': createCompletion
 *   - '''Chat Completions''': createChatCompletion, createChatWebSearchCompletion,
 *     createChatFunCompletion (deprecated), and createChatToolCompletion
 *   - '''Edits''': createEdit (deprecated)
 *   - '''Images''': createImage, createImageEdit, createImageVariation
 *   - '''Embeddings''': createEmbeddings
 *   - '''Batches''': createBatch, retrieveBatch, cancelBatch, and listBatches
 *   - '''Audio''': createAudioTranscription, createAudioTranslation, and createAudioSpeech
 *   - '''Files''': listFiles, uploadFile, deleteFile, retrieveFile, and retrieveFileContent
 *   - '''Fine-tunes''': createFineTune, listFineTunes, retrieveFineTune, cancelFineTune,
 *     listFineTuneEvents, listFineTuneCheckpoints, and deleteFineTuneModel
 *   - '''Moderations''': createModeration
 *   - '''Threads''': createThread, retrieveThread, modifyThread, and deleteThread
 *   - '''Thread Messages''': createThreadMessage, retrieveThreadMessage, modifyThreadMessage,
 *     listThreadMessages, retrieveThreadMessageFile, and listThreadMessageFiles
 *   - '''Runs''': createRun, etc.
 *   - '''Run Steps''': listRunSteps, etc.
 *   - '''Vector Stores''': createVectorStore, modifyVectorStore, listVectorStores,
 *     retrieveVectorStore, deleteVectorStore etc.
 *   - '''Vector Store Files''': createVectorStoreFile, listVectorStoreFiles,
 *     retrieveVectorStoreFile, deleteVectorStoreFile etc.
 *   - '''Vector Store File Batches''': TODO etc.
 *   - '''Assistants''': createAssistant, listAssistants, retrieveAssistant, modifyAssistant,
 *     and deleteAssistant
 *   - '''Assistant Files''': createAssistantFile, listAssistantFiles, retrieveAssistantFile,
 *     and deleteAssistantFile
 *   - ''''Responses''' - createModelResponse, getModelResponse, deleteModelResponse, and listModelResponseInputItems
 * @since Sep
 *   2024
 */
trait OpenAIService extends OpenAICoreService {

  /**
   * Retrieves a model instance, providing basic information about the model such as the owner
   * and permissions.
   *
   * @param modelId
   *   The ID of the model to use for this request
   * @return
   *   model or None if not found
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/models/retrieve">OpenAI Doc</a>
   */
  def retrieveModel(modelId: String): Future[Option[ModelInfo]]

  /**
   * Creates a model response for the given chat conversation expecting a function call.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param functions
   *   A list of functions the model may generate JSON inputs for.
   * @param responseFunctionName
   *   If specified it forces the model to respond with a call to that function (must be listed
   *   in `functions`). Otherwise, the default "auto" mode is used where the model can pick
   *   between an end-user or calling a function.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   *
   * Deprecated: use {@link OpenAIService.createChatToolCompletion} instead.
   */
  @Deprecated
  def createChatFunCompletion(
    messages: Seq[BaseMessage],
    functions: Seq[ChatCompletionTool],
    responseFunctionName: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatFunCompletion
  ): Future[ChatFunCompletionResponse]

  /**
   * Creates a model response for the given chat conversation expecting a tool call.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param tools
   *   A list of tools the model may call. Currently, only functions are supported as a tool.
   *   Use this to provide a list of functions the model may generate JSON inputs for.
   * @param responseToolChoice
   *   Controls which (if any) function/tool is called by the model. Specifying a particular
   *   function forces the model to call that function (must be listed in `tools`). Otherwise,
   *   the default "auto" mode is used where the model can pick between generating a message or
   *   calling a function.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse]

  /**
   * Chat completion with web search. Supports these models: gpt-4-vision-preview and
   * gpt-4-1106-vision-preview
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param searchOptions
   * @param settings
   * @return
   *   chat completion response with annotations/citations
   * @see
   *   <a href="https://platform.openai.com/docs/guides/tools-web-search">OpenAI Doc</a>
   */
  def createChatWebSearchCompletion(
    messages: Seq[BaseMessage],
    searchOptions: WebSearchOptions = WebSearchOptions(),
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatWebSearchCompletion
  ): Future[ChatWebSearchCompletionResponse]

  /**
   * Creates a new edit for the provided input, instruction, and parameters.
   *
   * @param input
   *   The input text to use as a starting point for the edit.
   * @param instruction
   *   The instruction that tells the model how to edit the prompt.
   * @param settings
   * @return
   *   text edit response
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/edits/create">OpenAI Doc</a>
   */
  @Deprecated
  def createEdit(
    input: String,
    instruction: String,
    settings: CreateEditSettings = DefaultSettings.CreateEdit
  ): Future[TextEditResponse]

  /**
   * Creates an image given a prompt.
   *
   * @param prompt
   *   A text description of the desired image(s). The maximum length is 1000 characters for
   *   dall-e-2 and 4000 characters for dall-e-3.
   * @param settings
   * @return
   *   image response (might contain multiple data items - one per image)
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/images/create">OpenAI Doc</a>
   */
  def createImage(
    prompt: String,
    settings: CreateImageSettings = DefaultSettings.CreateImage
  ): Future[ImageInfo]

  /**
   * Creates an edited or extended image given an original image and a prompt.
   *
   * @param prompt
   *   A text description of the desired image(s). The maximum length is 1000 characters.
   * @param image
   *   The image to edit. Must be a valid PNG file, less than 4MB, and square. If mask is not
   *   provided, image must have transparency, which will be used as the mask.
   * @param mask
   *   An additional image whose fully transparent areas (e.g. where alpha is zero) indicate
   *   where image should be edited. Must be a valid PNG file, less than 4MB, and have the same
   *   dimensions as image.
   * @param settings
   * @return
   *   image response (might contain multiple data items - one per image)
   *
   * @see
   *   <a https://platform.openai.com/docs/api-reference/images/createEdit">OpenAI Doc</a>
   */
  def createImageEdit(
    prompt: String,
    image: File,
    mask: Option[File] = None,
    settings: CreateImageEditSettings = DefaultSettings.CreateImageEdit
  ): Future[ImageInfo]

  /**
   * Creates a variation of a given image.
   *
   * @param image
   *   The image to use as the basis for the variation(s). Must be a valid PNG file, less than
   *   4MB, and square.
   * @param settings
   * @return
   *   image response (might contain multiple data items - one per image)
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/images/createVariation">OpenAI
   *   Doc</a>
   */
  def createImageVariation(
    image: File,
    settings: CreateImageEditSettings = DefaultSettings.CreateImageVariation
  ): Future[ImageInfo]

  /**
   * Generates audio from the input text.
   *
   * @param input
   *   The text to generate audio for. The maximum length is 4096 characters.
   *
   * @param settings
   * @return
   *   The audio file content.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/audio/createSpeech">OpenAI
   *   Doc</a>
   */
  def createAudioSpeech(
    input: String,
    settings: CreateSpeechSettings = DefaultSettings.CreateSpeech
  ): Future[Source[ByteString, _]]

  /**
   * Transcribes audio into the input language.
   *
   * @param file
   *   The audio file to transcribe, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav,
   *   or webm.
   * @param prompt
   *   An optional text to guide the model's style or continue a previous audio segment. The
   *   prompt should match the audio language.
   * @param settings
   * @return
   *   transcription text
   *
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/audio/createTranscription">OpenAI
   *   Doc</a>
   */
  def createAudioTranscription(
    file: File,
    prompt: Option[String] = None,
    settings: CreateTranscriptionSettings = DefaultSettings.CreateTranscription
  ): Future[TranscriptResponse]

  /**
   * Translates audio into into English.
   *
   * @param file
   *   The audio file to translate, in one of these formats: mp3, mp4, mpeg, mpga, m4a, wav, or
   *   webm.
   * @param prompt
   *   An optional text to guide the model's style or continue a previous audio segment. The
   *   prompt should match the audio language.
   * @param settings
   * @return
   *   translation text
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/audio/createTranslation">OpenAI
   *   Doc</a>
   */
  def createAudioTranslation(
    file: File,
    prompt: Option[String] = None,
    settings: CreateTranslationSettings = DefaultSettings.CreateTranslation
  ): Future[TranscriptResponse]

  /**
   * Returns a list of files that belong to the user's organization.
   *
   * @return
   *   file infos
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/files/list">OpenAI Doc</a>
   */
  def listFiles: Future[Seq[FileInfo]]

  /**
   * Upload a file that contains document(s) to be used across various endpoints/features.
   * Currently, the size of all the files uploaded by one organization can be up to 1 GB.
   * Please contact us if you need to increase the storage limit.
   *
   * @param file
   *   Name of the JSON Lines file to be uploaded. If the purpose is set to "fine-tune", each
   *   line is a JSON record with "prompt" and "completion" fields representing your <a
   *   href="https://platform.openai.com/docs/guides/fine-tuning/prepare-training-data">training
   *   examples</a>.
   * @param displayFileName
   *   (Explicit) display file name; if not specified a full path is used instead.
   * @param settings
   * @return
   *   file info
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/files/upload">OpenAI Doc</a>
   */
  def uploadFile(
    file: File,
    displayFileName: Option[String] = None,
    purpose: FileUploadPurpose
  ): Future[FileInfo]

  /**
   * Upload a file that contains requests to be batch-processed. Currently, the size of all the
   * files uploaded by one organization can be up to 1 GB. Please contact us if you need to
   * increase the storage limit.
   *
   * @param file
   *   JSON Lines file to be uploaded. Each line is a JSON record with: <ul> <li>"custom_id"
   *   field - request identifier used to match batch requests with their responses</li>
   *   <li>"method" field - HTTP method to be used for the request (currently only POST is
   *   supported)</li> <li>"url" field - OpenAI API relative URL to be used for the request
   *   (currently /v1/chat/completions and /v1/embeddings are supported)</li> <li>"body" field
   *   \- JSON record with model and messages fields that will be passed to the specified
   *   endpoint</li> </ul> <a
   *   href="https://platform.openai.com/docs/guides/batch/1-preparing-your-batch-file">batch
   *   examples</a>.
   * @param displayFileName
   *   (Explicit) display file name; if not specified a full path is used instead.
   * @return
   *   file info
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/files/upload">OpenAI Doc</a>
   */
  def uploadBatchFile(
    file: File,
    displayFileName: Option[String] = None
  ): Future[FileInfo]

  /**
   * Builds a temporary file from requests and uploads it.
   *
   * @param model
   *   model to be used for the requests of this batch
   * @param requests
   *   requests to be batch-processed
   * @param displayFileName
   *   (Explicit) display file name; if not specified a full path is used instead.
   * @return
   */
  def buildAndUploadBatchFile(
    model: String,
    requests: Seq[BatchRowBase],
    displayFileName: Option[String]
  ): Future[FileInfo]


  // format: off
  /**
   *
   *
   * Example output corresponds to a JSON like this:
   * <pre>
   * [
   *   {
   *     "custom_id": "request-1",
   *     "method": "POST",
   *     "url": "/v1/chat/completions",
   *     "body": {
   *       "model": "gpt-3.5-turbo",
   *       "messages": [{"role": "system", "content": "You are a helpful assistant."}, {"role": "user", "content": "What is 2+2?"}]
   *     }
   *   }
   * ]
   * </pre>
   */
  // format: on
  def buildBatchFileContent(
    model: String,
    requests: Seq[BatchRowBase]
  ): Future[Seq[BatchRow]]

  /**
   * Delete a file.
   *
   * @param fileId
   *   The ID of the file to use for this request
   * @return
   *   enum indicating whether the file was deleted
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/files/delete">OpenAI Doc</a>
   */
  def deleteFile(
    fileId: String
  ): Future[DeleteResponse]

  /**
   * Returns information about a specific file.
   *
   * @param fileId
   *   The ID of the file to use for this request
   * @return
   *   file info or None if not found
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/files/retrieve">OpenAI Doc</a>
   */
  def retrieveFile(
    fileId: String
  ): Future[Option[FileInfo]]

  /**
   * Returns the contents of the specified file.
   *
   * @param fileId
   *   The ID of the file to use for this request
   * @return
   *   file content or None if not found
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/files/retrieve-content">OpenAI
   *   Doc</a>
   */
  def retrieveFileContent(
    fileId: String
  ): Future[Option[String]]

  /**
   * Returns the contents of the specified file as an Akka source.
   *
   * @param fileId
   *   The ID of the file to use for this request
   * @return
   *   file content or None if not found
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/files/retrieve-content">OpenAI
   *   Doc</a>
   */
  def retrieveFileContentAsSource(
    fileId: String
  ): Future[Option[Source[ByteString, _]]]

  /**
   * Creates a job that fine-tunes a specified model from a given dataset. Response includes
   * details of the enqueued job including job status and the name of the fine-tuned models
   * once complete.
   *
   * @param training_file
   *   The ID of an uploaded file that contains training data. See <code>uploadFile</code> for
   *   how to upload a file. Your dataset must be formatted as a JSONL file, where each
   *   training example is a JSON object with the keys "prompt" and "completion". Additionally,
   *   you must upload your file with the purpose fine-tune.
   * @param validation_file
   *   The ID of an uploaded file that contains validation data. If you provide this file, the
   *   data is used to generate validation metrics periodically during fine-tuning. These
   *   metrics can be viewed in the fine-tuning results file. Your train and validation data
   *   should be mutually exclusive. Your dataset must be formatted as a JSONL file, where each
   *   validation example is a JSON object with the keys "prompt" and "completion".
   *   Additionally, you must upload your file with the purpose fine-tune.
   * @param settings
   * @return
   *   fine tune response
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/fine-tuning/create">OpenAI API
   *   Doc</a>
   * @see
   *   <a href="https://platform.openai.com/docs/guides/fine-tuning">OpenAI Fine-Tuning
   *   Guide</a>
   */
  def createFineTune(
    training_file: String,
    validation_file: Option[String] = None,
    settings: CreateFineTuneSettings = DefaultSettings.CreateFineTune
  ): Future[FineTuneJob]

  /**
   * List your organization's fine-tuning jobs.
   *
   * @param after
   *   Identifier for the last job from the previous pagination request.
   * @param limit
   *   Number of fine-tuning jobs to retrieve.
   * @return
   *   fine tunes
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/fine-tuning/undefined">OpenAI
   *   Doc</a>
   */
  def listFineTunes(
    after: Option[String] = None,
    limit: Option[Int] = None
  ): Future[Seq[FineTuneJob]]

  /**
   * Gets info about the fine-tune job.
   *
   * @param fineTuneId
   *   The ID of the fine-tune job
   * @return
   *   fine tune info
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/fine-tuning/retrieve">OpenAI
   *   Doc</a>
   */
  def retrieveFineTune(
    fineTuneId: String
  ): Future[Option[FineTuneJob]]

  /**
   * Immediately cancel a fine-tune job.
   *
   * @param fineTuneId
   *   The ID of the fine-tune job to cancel
   * @return
   *   fine tune info or None if not found
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/fine-tuning/cancel">OpenAI
   *   Doc</a>
   */
  def cancelFineTune(
    fineTuneId: String
  ): Future[Option[FineTuneJob]]

  /**
   * Get fine-grained status updates for a fine-tune job.
   *
   * @param fineTuneId
   *   The ID of the fine-tune job to get events for.
   * @return
   *   fine tune events or None if not found
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/fine-tuning/list-events">OpenAI
   *   Doc</a>
   */
  def listFineTuneEvents(
    fineTuneId: String,
    after: Option[String] = None,
    limit: Option[Int] = None
  ): Future[Option[Seq[FineTuneEvent]]]

  /**
   * List checkpoints for a fine-tuning job.
   *
   * @param fineTuneId
   *   The ID of the fine-tune job to get checkpoints for.
   * @param after
   *   Identifier for the last checkpoint ID from the previous pagination request.
   * @param limit
   *   Number of checkpoints to retrieve.
   * @return
   *   A list of fine-tuning checkpoint objects for a fine-tuning job.
   *
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/fine-tuning/list-checkpoints">OpenAI
   *   Doc</a>
   */
  def listFineTuneCheckpoints(
    // FIXME: using fineTuneId to be consistent, however, it shall be fineTuningJobId
    fineTuneId: String,
    after: Option[String] = None,
    limit: Option[Int] = None
  ): Future[Option[Seq[FineTuneCheckpoint]]]

  /**
   * Delete a fine-tuned model. You must have the Owner role in your organization.
   *
   * @param modelId
   *   The ID of the file to use for this request
   * @return
   *   enum indicating whether the model was deleted
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/models/delete">OpenAI Doc</a>
   */
  def deleteFineTuneModel(
    modelId: String
  ): Future[DeleteResponse]

  /**
   * Classifies if text violates OpenAI's Content Policy.
   *
   * @param input
   *   The input text to classify
   * @param settings
   * @return
   *   moderation results
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/moderations/create">OpenAI
   *   Doc</a>
   */
  def createModeration(
    input: String,
    settings: CreateModerationSettings = DefaultSettings.CreateModeration
  ): Future[ModerationResponse]

  ///////////////
  // ASSISTANT //
  ///////////////

  /**
   * Create an assistant with a model and instructions.
   *
   * @param model
   *   The ID of the model to use. You can use the List models API to see all of your available
   *   models, or see our Model overview for descriptions of them.
   * @param name
   *   The name of the assistant. The maximum length is 256 characters.
   * @param description
   *   The description of the assistant. The maximum length is 512 characters.
   * @param instructions
   *   The system instructions that the assistant uses. The maximum length is 32768 characters.
   * @param tools
   *   A list of tool enabled on the assistant. There can be a maximum of 128 tools per
   *   assistant. Tools can be of types code_interpreter, retrieval, or function.
   * @param toolResources
   *   A set of resources that are used by the assistant's tools. The resources are specific to
   *   the type of tool. For example, the code_interpreter tool requires a list of file IDs,
   *   while the file_search tool requires a list of vector store IDs.
   * @param metadata
   *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
   *   storing additional information about the object in a structured format. Keys can be a
   *   maximum of 64 characters long and values can be a maxium of 512 characters long.
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/createAssistant">OpenAI
   *   Doc</a>
   */
  def createAssistant(
    model: String,
    name: Option[String] = None,
    description: Option[String] = None,
    instructions: Option[String] = None,
    tools: Seq[AssistantTool] = Seq.empty[AssistantTool],
    toolResources: Option[AssistantToolResource] = None,
    metadata: Map[String, String] = Map.empty
  ): Future[Assistant]

  /**
   * Returns a list of assistants.
   *
   * @param limit
   *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and
   *   the default is 20.
   * @param order
   *   Sort order by the created_at timestamp of the objects. asc for ascending order and desc
   *   for descending order.
   * @param after
   *   A cursor for use in pagination. after is an object ID that defines your place in the
   *   list. For instance, if you make a list request and receive 100 objects, ending with
   *   `obj_foo`, your subsequent call can include `after=obj_foo` in order to fetch the next
   *   page of the list.
   * @param before
   *   A cursor for use in pagination. before is an object ID that defines your place in the
   *   list. For instance, if you make a list request and receive 100 objects, ending with
   *   `obj_foo`, your subsequent call can include `before=obj_foo` in order to fetch the
   *   previous page of the list.
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/listAssistants">OpenAI
   *   Doc</a>
   */
  def listAssistants(
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None
  ): Future[Seq[Assistant]]

  /**
   * Retrieves an assistant.
   *
   * @param assistantId
   *   The ID of the assistant to retrieve. <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/retrieveAssistant">OpenAI
   *   Doc</a>
   */
  def retrieveAssistant(assistantId: String): Future[Option[Assistant]]

  /**
   * Modifies an assistant.
   *
   * @param assistantId
   * @param model
   *   ID of the model to use. You can use the List models API to see all of your available
   *   models, or see our Model overview for descriptions of them.
   * @param name
   *   The name of the assistant. The maximum length is 256 characters.
   * @param description
   *   The description of the assistant. The maximum length is 512 characters.
   * @param instructions
   *   The system instructions that the assistant uses. The maximum length is 32768 characters.
   * @param tools
   *   A list of tool enabled on the assistant. There can be a maximum of 128 tools per
   *   assistant. Tools can be of types code_interpreter, retrieval, or function.
   * @param fileIds
   *   A list of File IDs attached to this assistant. There can be a maximum of 20 files
   *   attached to the assistant. Files are ordered by their creation date in ascending order.
   *   If a file was previously attached to the list but does not show up in the list, it will
   *   be deleted from the assistant.
   * @param metadata
   *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
   *   storing additional information about the object in a structured format. Keys can be a
   *   maximum of 64 characters long and values can be a maxium of 512 characters long. <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/modifyAssistant">OpenAI
   *   Doc</a>
   */
  def modifyAssistant(
    assistantId: String,
    model: Option[String] = None,
    name: Option[String] = None,
    description: Option[String] = None,
    instructions: Option[String] = None,
    tools: Seq[AssistantTool] = Seq.empty[AssistantTool],
    fileIds: Seq[String] = Seq.empty,
    metadata: Map[String, String] = Map.empty
  ): Future[Option[Assistant]]

  /**
   * Delete an assistant.
   *
   * @param assistantId
   *   The ID of the assistant to delete. <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/deleteAssistant">OpenAI
   *   Doc</a>
   */
  def deleteAssistant(assistantId: String): Future[DeleteResponse]

  /**
   * Delete an assistant file.
   *
   * @param assistantId
   *   The ID of the assistant that the file belongs to.
   * @param fileId
   *   The ID of the file to delete. <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/deleteAssistantFile">OpenAI
   *   Doc</a>
   */
  def deleteAssistantFile(
    assistantId: String,
    fileId: String
  ): Future[DeleteResponse]

  ////////////
  // THREAD //
  ////////////

  /**
   * Creates a thread.
   *
   * @param messages
   *   A list of messages to start the thread with.
   * @param toolResources
   *   A set of resources that are made available to the assistant's tools in this thread. The
   *   resources are specific to the type of tool. For example, the code_interpreter tool
   *   requires a list of file IDs, while the file_search tool requires a list of vector store
   *   IDs.
   * @param metadata
   *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
   *   storing additional information about the object in a structured format. Keys can be a
   *   maximum of 64 characters long and values can be a maxium of 512 characters long.
   * @return
   *   A thread object.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/threads/createThread">OpenAI
   *   Doc</a>
   */
  def createThread(
    messages: Seq[ThreadMessage] = Nil,
    toolResources: Seq[AssistantToolResource] = Nil,
    metadata: Map[String, String] = Map()
  ): Future[Thread]

  /**
   * Retrieves a thread.
   *
   * @param threadId
   *   The ID of the thread to retrieve.
   * @return
   *   The thread object matching the specified ID.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/threads/getThread">OpenAI
   *   Doc</a>
   */
  def retrieveThread(threadId: String): Future[Option[Thread]]

  /**
   * Modifies a thread.
   *
   * @param threadId
   *   The ID of the thread to modify. Only the metadata can be modified.
   * @param metadata
   *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
   *   storing additional information about the object in a structured format. Keys can be a
   *   maximum of 64 characters long and values can be a maxium of 512 characters long.
   * @return
   *   The modified thread object matching the specified ID.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/threads/modifyThread">OpenAI
   *   Doc</a>
   */
  def modifyThread(
    threadId: String,
    metadata: Map[String, String] = Map()
  ): Future[Option[Thread]]

  /**
   * Deletes a thread.
   *
   * @param threadId
   *   TThe ID of the thread to delete.
   * @return
   *   Deletion status
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/threads/deleteThread">OpenAI
   *   Doc</a>
   */
  def deleteThread(threadId: String): Future[DeleteResponse]

  ////////////////////
  // THREAD MESSAGE //
  ////////////////////

  /**
   * Creates a thread message.
   *
   * @param threadId
   *   The ID of the thread to create a message for.
   * @param content
   *   The content of the message.
   * @param role
   *   The role of the entity that is creating the message. Currently only user is supported.
   * @param attachments
   *   A list of files attached to the message, and the tools they should be added to.
   * @param metadata
   *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
   *   storing additional information about the object in a structured format. Keys can be a
   *   maximum of 64 characters long and values can be a maximum of 512 characters long.
   * @return
   *   A thread message object.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/messages/createMessage">OpenAI
   *   Doc</a>
   */
  def createThreadMessage(
    threadId: String,
    content: String,
    role: ChatRole = ChatRole.User,
    attachments: Seq[Attachment] = Nil,
    metadata: Map[String, String] = Map()
  ): Future[ThreadFullMessage]

  /**
   * Retrieves a thread message.
   *
   * @param threadId
   *   The ID of the thread to which this message belongs.
   * @param messageId
   *   The ID of the message to retrieve.
   * @return
   *   The message object matching the specified ID.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/messages/getMessage">OpenAI
   *   Doc</a>
   */
  def retrieveThreadMessage(
    threadId: String,
    messageId: String
  ): Future[Option[ThreadFullMessage]]

  /**
   * Modifies a thread message.
   *
   * @param threadId
   *   The ID of the thread to which this message belongs.
   * @param messageId
   *   The ID of the message to modify.
   * @param metadata
   *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
   *   storing additional information about the object in a structured format. Keys can be a
   *   maximum of 64 characters long and values can be a maximum of 512 characters long.
   * @return
   *   The modified message object.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/messages/modifyMessage">OpenAI
   *   Doc</a>
   */
  def modifyThreadMessage(
    threadId: String,
    messageId: String,
    metadata: Map[String, String] = Map()
  ): Future[Option[ThreadFullMessage]]

  /**
   * Returns a list of messages for a given thread.
   *
   * @param threadId
   *   The ID of the thread the messages belong to.
   * @param limit
   *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and
   *   the default is 20. Defaults to 20
   * @param order
   *   Sort order by the created_at timestamp of the objects. asc for ascending order and desc
   *   for descending order. Defaults to desc
   * @param after
   *   A cursor for use in pagination. after is an object ID that defines your place in the
   *   list. For instance, if you make a list request and receive 100 objects, ending with
   *   obj_foo, your subsequent call can include after=obj_foo in order to fetch the next page
   *   of the list.
   * @param before
   *   A cursor for use in pagination. before is an object ID that defines your place in the
   *   list. For instance, if you make a list request and receive 100 objects, ending with
   *   obj_foo, your subsequent call can include before=obj_foo in order to fetch the previous
   *   page of the list.
   * @return
   *   thread messages
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/messages/listMessages">OpenAI
   *   Doc</a>
   */
  def listThreadMessages(
    threadId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None
  ): Future[Seq[ThreadFullMessage]]

  /**
   * Deletes a thread message.
   *
   * @param threadId
   *   The ID of the thread to which this message belongs.
   * @param messageId
   *   The ID of the message to delete.
   * @return
   *   Deletion status.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/messages/deleteMessage">OpenAI
   *   Doc</a>
   */
  def deleteThreadMessage(
    threadId: String,
    messageId: String
  ): Future[DeleteResponse]

  /////////////////
  // THREAD FILE //
  /////////////////

  /**
   * Retrieves a thread message file.
   *
   * @param threadId
   *   The ID of the thread to which the message and File belong.
   * @param messageId
   *   The ID of the message the file belongs to.
   * @param fileId
   *   The ID of the file being retrieved.
   * @return
   *   The thread message file object.
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/messages/getMessageFile">OpenAI
   *   Doc</a>
   */
  def retrieveThreadMessageFile(
    threadId: String,
    messageId: String,
    fileId: String
  ): Future[Option[ThreadMessageFile]]

  /**
   * Returns a list of message files.
   *
   * @param threadId
   *   The ID of the thread that the message and files belong to.
   * @param messageId
   *   TThe ID of the message the file belongs to.
   * @param limit
   *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and
   *   the default is 20. Defaults to 20
   * @param order
   *   Sort order by the created_at timestamp of the objects. asc for ascending order and desc
   *   for descending order. Defaults to desc
   * @param after
   *   A cursor for use in pagination. after is an object ID that defines your place in the
   *   list. For instance, if you make a list request and receive 100 objects, ending with
   *   obj_foo, your subsequent call can include after=obj_foo in order to fetch the next page
   *   of the list.
   * @param before
   *   A cursor for use in pagination. before is an object ID that defines your place in the
   *   list. For instance, if you make a list request and receive 100 objects, ending with
   *   obj_foo, your subsequent call can include before=obj_foo in order to fetch the previous
   *   page of the list.
   * @return
   *   thread message files
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/messages/listMessageFiles">OpenAI
   *   Doc</a>
   */
  def listThreadMessageFiles(
    threadId: String,
    messageId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None
  ): Future[Seq[ThreadMessageFile]]

  /////////
  // RUN //
  /////////

  /**
   * Creates a run for a specified thread using the given assistant.
   *
   * @param threadId
   *   The ID of the thread to run.
   * @param assistantId
   *   The ID of the assistant to use to execute this run.
   * @param instructions
   *   Optional. Overrides the instructions of the assistant. This is useful for modifying the
   *   behavior on a per-run basis.
   * @param additionalInstructions
   *   Optional. Appends additional instructions at the end of the instructions for the run.
   *   This is useful for modifying the behavior on a per-run basis without overriding other
   *   instructions.
   * @param additionalMessages
   *   Optional. Adds additional messages to the thread before creating the run.
   * @param tools
   *   Optional. Override the tools the assistant can use for this run. This is useful for
   *   modifying the behavior on a per-run basis.
   * @param responseToolChoice
   *   Optional. Controls which (if any) tool is called by the model. Can be "none", "auto",
   *   "required", or a specific tool.
   * @param settings
   *   Optional. Settings for creating the run, such as model, temperature, top_p, etc.
   * @param stream
   *   Optional. If true, returns a stream of events that happen during the Run as server-sent
   *   events, terminating when the Run enters a terminal state with a data: [DONE] message.
   * @return
   *   `Future[Run]` A future that resolves to a Run object.
   *
   * @see
   *   <a href="https://api.openai.com/v1/threads/{thread_id}/runs">OpenAI API Reference</a>
   */
  def createRun(
    threadId: String,
    assistantId: String,
    // TODO: move this to settings
    instructions: Option[String] = None,
    additionalInstructions: Option[String] = None,
    additionalMessages: Seq[BaseMessage] = Seq.empty,
    tools: Seq[AssistantTool] = Seq.empty,
    responseToolChoice: Option[ToolChoice] = None,
    settings: CreateRunSettings = DefaultSettings.CreateRun,
    stream: Boolean
  ): Future[Run]

  /**
   * @param assistantId
   *   The ID of the assistant to use to execute this run.
   * @param thread
   *   The ID of the thread to run.
   * @param instructions
   *   Override the default system message of the assistant. This is useful for modifying the
   *   behavior on a per-run basis.
   * @param tools
   *   Override the tools the assistant can use for this run. This is useful for modifying the
   *   behavior on a per-run basis.
   * @param toolResources
   *   A set of resources that are used by the assistant's tools. The resources are specific to
   *   the type of tool. For example, the code_interpreter tool requires a list of file IDs,
   *   while the file_search tool requires a list of vector store IDs.
   * @param toolChoice
   *   Controls which (if any) tool is called by the model. none means the model will not call
   *   any tools and instead generates a message. auto is the default value and means the model
   *   can pick between generating a message or calling one or more tools. required means the
   *   model must call one or more tools before responding to the user. Specifying a particular
   *   tool like {"type": "file_search"} or {"type": "function", "function": {"name":
   *   "my_function"}} forces the model to call that tool.
   * @param settings
   * @param stream
   *   If true, returns a stream of events that happen during the Run as server-sent events,
   *   terminating when the Run enters a terminal state with a data: [DONE] message.
   * @returns
   *   A run object.
   */
  def createThreadAndRun(
    assistantId: String,
    thread: Option[ThreadAndRun],
    instructions: Option[String] = None,
    tools: Seq[AssistantTool] = Seq.empty,
    toolResources: Option[ThreadAndRunToolResource] = None,
    toolChoice: Option[ToolChoice] = None,
    settings: CreateThreadAndRunSettings = DefaultSettings.CreateThreadAndRun,
    stream: Boolean
  ): Future[Run]

  /**
   * Returns a list of runs belonging to a thread.
   *
   * @param threadId
   *   The ID of the thread the run belongs to.
   * @param pagination
   * @param order
   *   Sort order by the created_at timestamp of the objects. asc for ascending order and desc
   *   for descending order.
   * @return
   *   A list of run objects.
   */
  def listRuns(
    threadId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None
  ): Future[Seq[Run]]

  def retrieveRun(
    threadId: String,
    runId: String
  ): Future[Option[Run]]

  /**
   * Modifies a run.
   *
   * @param threadId
   *   The ID of the thread that was run.
   * @param runId
   *   The ID of the run to modify.
   * @param metadata
   *   Set of 16 key-value pairs that can be attached to an object. This can be useful for
   *   storing additional information about the object in a structured format. Keys can be a
   *   maximum of 64 characters long and values can be a maximum of 512 characters long.
   * @return
   *   The modified run object matching the specified ID.
   */
  def modifyRun(
    threadId: String,
    runId: String,
    metadata: Map[String, String]
  ): Future[Run]

  /**
   * When a run has the status: "requires_action" and required_action.type is
   * submit_tool_outputs, this endpoint can be used to submit the outputs from the tool calls
   * once they're all completed. All outputs must be submitted in a single request.
   *
   * @param threadId
   *   The ID of the thread to which this run belongs.
   * @param runId
   *   The ID of the run that requires the tool output submission.
   * @param toolOutputs
   *   A list of tools for which the outputs are being submitted.
   * @param stream
   *   If true, returns a stream of events that happen during the Run as server-sent events,
   *   terminating when the Run enters a terminal state with a data: [DONE] message.
   * @return
   *   The modified run object matching the specified ID.
   */
  def submitToolOutputs(
    threadId: String,
    runId: String,
    toolOutputs: Seq[AssistantToolOutput],
    stream: Boolean
  ): Future[Run]

  /**
   * Cancels a run that is in_progress
   *
   * @param threadId
   *   The ID of the thread to which this run belongs.
   * @param runId
   *   The ID of the run to cancel.
   * @return
   *   The modified run object matching the specified ID.
   */
  def cancelRun(
    threadId: String,
    runId: String
  ): Future[Run]

  ///////////////
  // RUN STEPS //
  ///////////////

  /**
   * Returns a list of run steps belonging to a run. Returns a list of run steps belonging to a
   * run.
   *
   * @param threadId
   *   The ID of the thread the run and run step belongs to.
   * @param runId
   *   The ID of the run the run steps belong to.
   * @param pagination
   * @param order
   *   Sort order by the created_at timestamp of the objects. asc for ascending order and desc
   *   for descending order.
   * @return
   *   A list of run step objects.
   */

  def listRunSteps(
    threadId: String,
    runId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None
  ): Future[Seq[RunStep]]

  /**
   * Retrieves a run step.
   *
   * @param threadID
   *   The ID of the thread to which the run and run step belongs.
   * @param runId
   *   The ID of the run to which the run step belongs.
   * @param stepId
   *   The ID of the run step to retrieve.
   * @return
   *   The run step object matching the specified ID.
   */
  def retrieveRunStep(
    threadID: String,
    runId: String,
    stepId: String
  ): Future[Option[RunStep]]

  //////////////////
  // VECTOR STORE //
  //////////////////

  /**
   * Create a vector store.
   *
   * @param fileIds
   *   A list of File IDs that the vector store should use (optional). Useful for tools like
   *   file_search that can access files.
   * @param name
   *   The name of the vector store.
   * @param metadata
   *   The expiration policy for a vector store. TODO maximum of 64 characters long and values
   *   can be a maximum of 512 characters long.
   * @return
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/vector-stores/create">OpenAI
   *   Doc</a>
   */
  def createVectorStore(
    fileIds: Seq[String] = Nil,
    name: Option[String] = None,
    metadata: Map[String, Any] = Map.empty // TODO: expires after
  ): Future[VectorStore]

  /**
   * Modifies a vector store.
   *
   * @param vectorStoreId
   *   The ID of the vector store to modify.
   * @param name
   *   The new name of the vector store (optional).
   * @param metadata
   *   A map of metadata to update (optional).
   * @return
   *   A Future containing the modified VectorStore.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/vector-stores/modify">OpenAI
   *   Doc</a>
   */
  def modifyVectorStore(
    vectorStoreId: String,
    name: Option[String] = None,
    metadata: Map[String, Any] = Map.empty // TODO: expires after
  ): Future[VectorStore]

  /**
   * Returns a list of vector stores. the default is 20. Defaults to 20 for descending order.
   * Defaults to desc obj_foo, your subsequent call can include after=obj_foo in order to fetch
   * the next page of the list. obj_foo, your subsequent call can include before=obj_foo in
   * order to fetch the previous page of the list.
   * @return
   *   thread messages
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/vector-stores/list">OpenAI
   *   Doc</a>
   */
  def listVectorStores(
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None
  ): Future[Seq[VectorStore]]

  /**
   * Retrieves a vector store.
   *
   * @param vectorStoreId
   *   The ID of the vector store to retrieve.
   * @return
   *   A Future containing an Option of VectorStore. The Option will be None if the vector
   *   store is not found.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/vector-stores/retrieve">OpenAI
   *   Doc</a>
   */
  def retrieveVectorStore(
    vectorStoreId: String
  ): Future[Option[VectorStore]]

  /**
   * Deletes a vector store.
   *
   * @param vectorStoreId
   *   The ID of the vector store to use for this request
   * @return
   *   enum indicating whether the vector store was deleted
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/vector-stores/delete">OpenAI
   *   Doc</a>
   */
  def deleteVectorStore(
    vectorStoreId: String
  ): Future[DeleteResponse]

  ///////////////////////
  // VECTOR STORE FILE //
  ///////////////////////

  /**
   * Creates a vector store file.
   * @param vectorStoreId
   *   The ID of the vector store to use for this request
   * @param fileId
   *   The ID of the file to use for this request
   * @param chunkingStrategy
   *   The chunking strategy to use for this request
   * @return
   *   vector store file
   *
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/vector-stores-files/createFile">OpenAI
   *   Doc</a>
   */
  def createVectorStoreFile(
    vectorStoreId: String,
    fileId: String,
    chunkingStrategy: ChunkingStrategy = ChunkingStrategy.AutoChunkingStrategy
  ): Future[VectorStoreFile]

  /**
   * Returns a list of vector store files.
   *
   * @param vectorStoreId
   *   The ID of the vector store to use for this request
   * @param pagination
   *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and
   *   the default is 20. Defaults to 20
   * @param order
   *   Sort order by the created_at timestamp of the objects. asc for ascending order and desc
   *   for descending order. Defaults to desc
   * @param filter
   *   Filter by the status of the vector store file. Defaults to None
   * @return
   *   vector store files
   *
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/vector-stores-files/listFiles">OpenAI
   *   Doc</a>
   */
  def listVectorStoreFiles(
    vectorStoreId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None,
    filter: Option[VectorStoreFileStatus] = None
  ): Future[Seq[VectorStoreFile]]

  /**
   * Retrieves a vector store file.
   *
   * @param vectorStoreId
   *   The ID of the vector store to which the file belongs.
   * @param fileId
   *   The ID of the file to retrieve.
   * @return
   *   A Future containing an Option of VectorStoreFile. The Option will be None if the file is
   *   not found.
   *
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/vector-stores-files/getFile">OpenAI
   *   Doc</a>
   */
  def retrieveVectorStoreFile(
    vectorStoreId: String,
    fileId: FileId
  ): Future[VectorStoreFile]

  /**
   * Deletes a vector store file.
   *
   * @param vectorStoreId
   *   The ID of the vector store to use for this request
   * @param fileId
   *   The ID of the file to use for this request
   * @return
   *   enum indicating whether the vector store file was deleted
   *
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/vector-stores-files/deleteFile">OpenAI
   *   Doc</a>
   */
  def deleteVectorStoreFile(
    vectorStoreId: String,
    fileId: String
  ): Future[DeleteResponse]

  ///////////
  // BATCH //
  ///////////

  /**
   * Creates and executes a batch from an uploaded file of requests.
   *
   * @param inputFileId
   *   The ID of an uploaded file that contains requests for the new batch. The input file must
   *   be formatted as a JSONL file, and must be uploaded with the purpose "batch".
   * @param endpoint
   *   The endpoint to be used for all requests in the batch. Supported values are
   *   ChatCompletions and Embeddings.
   * @param completionWindow
   *   The time frame within which the batch should be processed. Currently only
   *   TwentyFourHours is supported.
   * @param metadata
   *   Optional custom metadata for the batch.
   * @return
   *   Future[Batch] A future that resolves to a Batch object containing details about the
   *   created batch.
   *
   * <a href="https://platform.openai.com/docs/api-reference/batch/create">OpenAI Doc</a>
   */
  def createBatch(
    inputFileId: String,
    endpoint: BatchEndpoint,
    completionWindow: CompletionWindow = CompletionWindow.`24h`,
    metadata: Map[String, String] = Map()
  ): Future[Batch]

  /**
   * Retrieves a batch using its ID.
   *
   * @param batchId
   *   The ID of the batch to retrieve. This is a unique identifier for the batch.
   * @return
   *   `Future[Option[Batch]` A future that resolves to an Option containing the [[Batch]]
   *   object. Returns None if the batch with the specified ID does not exist.
   *
   * <a href="https://platform.openai.com/docs/api-reference/batch/retrieve">OpenAI Doc</a>
   */
  def retrieveBatch(batchId: String): Future[Option[Batch]]

  /**
   * Retrieves an output batch file using the ID of the batch it belongs to.
   *
   * @param batchId
   *   The ID of the output batch file to retrieve. This is a unique identifier for the batch.
   * @return
   *   `Future[Option[FileInfo]` A future that resolves to an Option containing the
   *   [[FileInfo]] object. Returns None if the batch with the specified ID does not exist.
   *
   * <a href="https://platform.openai.com/docs/api-reference/batch/retrieve">OpenAI Doc</a>
   */
  def retrieveBatchFile(batchId: String): Future[Option[FileInfo]]

  /**
   * Retrieves content of output batch file using the ID of the batch it belongs to.
   *
   * @param batchId
   *   The ID of the batch whose output file's content is to be retrieved.
   * @return
   *   `Future[Option[String]` A future that resolves to an Option containing the [[String]]
   *   object. Returns None if the batch with the specified ID does not exist.
   *
   * <a href="https://platform.openai.com/docs/api-reference/batch/retrieve">OpenAI Doc</a>
   */
  def retrieveBatchFileContent(batchId: String): Future[Option[String]]

  /**
   * Retrieves OpenAI endpoint responses (for chat completion or embeddings, see:
   * [[BatchEndpoint]]) using the ID of the batch they belong to.
   *
   * @param batchId
   *   The ID of the batch whose endpoint responses are to be retrieved.
   * @return
   *   `Future[Option[CreateBatchResponses]` A future that resolves to an Option containing the
   *   [[CreateBatchResponses]] object. Returns None if the batch with the specified ID does
   *   not exist.
   *
   * <a href="https://platform.openai.com/docs/api-reference/batch/retrieve">OpenAI Doc</a>
   */
  def retrieveBatchResponses(batchId: String): Future[Option[CreateBatchResponses]]

  /**
   * Cancels an in-progress batch.
   *
   * @param batchId
   *   The ID of the batch to cancel. This should be the unique identifier for the in-progress
   *   batch.
   * @return
   *   Future[Option[Batch]] A future that resolves to an Option containing the Batch object
   *   after cancellation. Returns None if the batch with the specified ID does not exist or if
   *   it is not in-progress.
   *
   * <a href="https://platform.openai.com/docs/api-reference/batch/cancel">OpenAI Doc</a>
   */
  def cancelBatch(batchId: String): Future[Option[Batch]]

  /**
   * Lists all batches that belong to the user's organization.
   *
   * @param pagination
   *   <ul> <li>limit - A limit on the number of objects to be returned. Limit can range
   *   between 1 and 100, and the default is 20.</li> <li>after - A cursor for use in
   *   pagination. after is an object ID that defines your place in the list. For instance, if
   *   you make a list request and receive 100 objects, ending with `obj_foo`, your subsequent
   *   call can include `after=obj_foo` in order to fetch the next page of the list. </li>
   *   <li>before - A cursor for use in pagination. before is an object ID that defines your
   *   place in the list. For instance, if you make a list request and receive 100 objects,
   *   ending with `obj_foo`, your subsequent call can include `before=obj_foo` in order to
   *   fetch the previous page of the list.</li> </ul>
   * @param order
   *   Sort order by the created_at timestamp of the objects. asc for ascending order and desc
   *   for descending order.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/batch/list">OpenAI Doc</a>
   * @return
   */
  def listBatches(
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None
  ): Future[Seq[Batch]]

  /**
   * Creates a model response for the given input data.
   *
   * @param inputs
   *   The input data to create a response for.
   * @param settings
   *   Settings to customize the response creation.
   * @return
   *   A Future containing the Response object.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/create">OpenAI Doc</a>
   */
  def createModelResponse(
    inputs: Inputs,
    settings: CreateModelResponseSettings = DefaultSettings.CreateModelResponse
  ): Future[Response]

  /**
   * Retrieves a model response by its ID.
   *
   * @param responseId
   *   The ID of the response to retrieve.
   * @param include
   *   Optional list of related objects to include in the response.
   * @return
   *   A Future containing the Response object.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/retrieve">OpenAI
   *   Doc</a>
   */
  def getModelResponse(
    responseId: String,
    include: Seq[String] = Nil
  ): Future[Response]

  /**
   * Deletes a model response by its ID.
   *
   * @param responseId
   *   The ID of the response to delete.
   * @return
   *   A Future containing the DeleteResponse object, indicating the result of the deletion.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/delete">OpenAI Doc</a>
   */
  def deleteModelResponse(
    responseId: String
  ): Future[ResponsesAPIDeleteResponse]

  /**
   * List input items for a model response.
   *
   * @param responseId
   *   The ID of the response to retrieve input items for.
   * @param after
   *   An item ID to list items after, used in pagination.
   * @param before
   *   An item ID to list items before, used in pagination.
   * @param include
   *   Additional fields to include in the response.
   * @param limit
   *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and the default is 20.
   * @param order
   *   The order to return the input items in. Default is asc.
   * @return
   *   A list of input item objects.
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/responses/list-input-items">OpenAI Doc</a>
   */
  def listModelResponseInputItems(
    responseId: String,
    after: Option[String] = None,
    before: Option[String] = None,
    include: Seq[String] = Nil,
    limit: Option[Int] = None,
    order: Option[SortOrder] = None
  ): Future[InputItemsResponse]
}
