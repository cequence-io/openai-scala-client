package io.cequence.openaiscala.service

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.domain.{
  AssistantTool,
  BaseMessage,
  ChatRole,
  FileId,
  FunctionSpec,
  Pagination,
  SortOrder,
  Thread,
  ThreadFullMessage,
  ThreadMessage,
  ThreadMessageFile,
  ToolSpec
}
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response._

import java.io.File
import scala.concurrent.Future

/**
 * Central service to access all public OpenAI WS endpoints as defined at <a
 * href="https://platform.openai.com/docs/api-reference">the API ref. page</a>
 *
 * The following services are supported:
 *
 *   - '''Models''': listModels, and retrieveModel
 *   - '''Completions''': createCompletion
 *   - '''Chat Completions''': createChatCompletion, createChatFunCompletion (deprecated), and
 *     createChatToolCompletion
 *   - '''Edits''': createEdit (deprecated)
 *   - '''Images''': createImage, createImageEdit, createImageVariation
 *   - '''Embeddings''': createEmbeddings
 *   - '''Audio''': createAudioTranscription, createAudioTranslation, and createAudioSpeech
 *   - '''Files''': listFiles, uploadFile, deleteFile, retrieveFile, and retrieveFileContent
 *   - '''Fine-tunes''': createFineTune, listFineTunes, retrieveFineTune, cancelFineTune,
 *     listFineTuneEvents, and deleteFineTuneModel
 *   - '''Moderations''': createModeration
 *   - '''Threads''': createThread, retrieveThread, modifyThread, and deleteThread
 *   - '''Thread Messages''': createThreadMessage, retrieveThreadMessage, modifyThreadMessage,
 *     listThreadMessages, retrieveThreadMessageFile, and listThreadMessageFiles
 *
 * @since Jan
 *   2023
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
    functions: Seq[FunctionSpec],
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
    tools: Seq[ToolSpec],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse]

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
    settings: UploadFileSettings = DefaultSettings.UploadFile
  ): Future[FileInfo]

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

  /**
   * Creates a thread.
   *
   * @param messages
   *   A list of messages to start the thread with.
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

  /**
   * Creates a thread message.
   *
   * @param threadId
   *   The ID of the thread to create a message for.
   * @param role
   *   The role of the entity that is creating the message. Currently only user is supported.
   * @param content
   *   The content of the message.
   * @param fileIds
   *   A list of File IDs that the message should use. There can be a maximum of 10 files
   *   attached to a message. Useful for tools like retrieval and code_interpreter that can
   *   access and use files.
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
    fileIds: Seq[String] = Nil,
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

  /**
   * Create an assistant with a model and instructions.
   *
   * @param model
   *   The ID of the model to use. You can use the List models API to see all of your available
   *   models, or see our Model overview for descriptions of them.
   * @param name
   *   The name of the assistant. The maximum length is 256 characters.
   * @param descriptionx
   *   The description of the assistant. The maximum length is 512 characters.
   * @param instructions
   *   The system instructions that the assistant uses. The maximum length is 32768 characters.
   * @param tools
   *   A list of tool enabled on the assistant. There can be a maximum of 128 tools per
   *   assistant. Tools can be of types code_interpreter, retrieval, or function.
   * @param fileIds
   *   A list of file IDs attached to this assistant. There can be a maximum of 20 files
   *   attached to the assistant. Files are ordered by their creation date in ascending order.
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
    fileIds: Seq[String] = Seq.empty,
    metadata: Map[String, String] = Map.empty
  ): Future[Assistant]

  /**
   * Create an assistant file by attaching a File to an assistant.
   *
   * @param assistantId
   *   The ID of the assistant for which to create a File.
   * @param fileId
   *   A File ID (with purpose="assistants") that the assistant should use. Useful for tools
   *   like `retrieval` and `code_interpreter` that can access files.
   * @see
   *   <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/createAssistantFile">OpenAI
   *   Doc</a>
   */
  def createAssistantFile(
    assistantId: String,
    fileId: String
  ): Future[AssistantFile]

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
   *   `obj_foo`, your subsequent call can include `before=obj_foo`` in order to fetch the
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
   * Returns a list of assistant files.
   *
   * @param assistantId
   *   A limit on the number of objects to be returned. Limit can range between 1 and 100, and
   *   the default is 20.
   * @param limit
   *   Sort order by the created_at timestamp of the objects. asc for ascending order and desc
   *   for descending order.
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
   *   previous page of the list. <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/listAssistantFiles">OpenAI
   *   Doc</a>
   */
  def listAssistantFiles(
    assistantId: String,
    pagination: Pagination = Pagination.default,
    order: Option[SortOrder] = None
  ): Future[Seq[AssistantFile]]

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
   * Retrieves an AssistantFile.
   *
   * @param assistantId
   *   The ID of the assistant who the file belongs to.
   * @param fileId
   *   The ID of the file we're getting. <a
   *   href="https://platform.openai.com/docs/api-reference/assistants/retrieveAssistantFile">OpenAI
   *   Doc</a>
   */
  def retrieveAssistantFile(
    assistantId: String,
    fileId: String
  ): Future[Option[AssistantFile]]

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

}
