package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.MessageSpec
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.response._

import java.io.File
import scala.concurrent.Future

/** Central service to access all public OpenAI WS endpoints as defined at <a
  * href="https://platform.openai.com/docs/api-reference">the API ref. page</a>
  *
  * The following services are supported:
  *
  *   - '''Models''': listModels, and retrieveModel
  *   - '''Completions''': createCompletion
  *   - '''Edits''': createEdit
  *   - '''Images''': createImage, createImageEdit, createImageVariation
  *   - '''Embeddings''': createEmbeddings
  *   - '''Files''': listFiles, uploadFile, deleteFile, retrieveFile, and
  *     retrieveFileContent
  *   - '''Fine-tunes''': createFineTune, listFineTunes, retrieveFineTune,
  *     cancelFineTune, listFineTuneEvents, and deleteFineTuneModel
  *   - '''Moderations''': createModeration
  *
  * @since Jan
  *   2023
  */
trait OpenAIService extends OpenAIServiceConsts {

  /** Lists the currently available models, and provides basic information about
    * each one such as the owner and availability.
    *
    * @return
    *   models
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/models/list">OpenAI
    *   Doc</a>
    */
  def listModels: Future[Seq[ModelInfo]]

  /** Retrieves a model instance, providing basic information about the model
    * such as the owner and permissions.
    *
    * @param modelId
    *   The ID of the model to use for this request
    * @return
    *   model or None if not found
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/models/retrieve">OpenAI
    *   Doc</a>
    */
  def retrieveModel(modelId: String): Future[Option[ModelInfo]]

  /** Creates a completion for the provided prompt and parameters.
    *
    * @param prompt
    *   The prompt(s) to generate completions for, encoded as a string, array of
    *   strings, array of tokens, or array of token arrays. Note that
    *   <|endoftext|> is the document separator that the model sees during
    *   training, so if a prompt is not specified the model will generate as if
    *   from the beginning of a new document.
    * @param settings
    * @return
    *   text completion response
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/completions/create">OpenAI
    *   Doc</a>
    */
  def createCompletion(
      prompt: String,
      settings: CreateCompletionSettings = DefaultSettings.CreateCompletion
  ): Future[TextCompletionResponse]

  /** Creates a completion for the chat message(s).
    *
    * @param messages
    *   The messages to generate chat completions.
    * @param settings
    * @return
    *   chat completion response
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI
    *   Doc</a>
    */
  def createChatCompletion(
      messages: Seq[MessageSpec],
      settings: CreateChatCompletionSettings =
        DefaultSettings.CreateChatCompletion
  ): Future[ChatCompletionResponse]

  /** Creates a new edit for the provided input, instruction, and parameters.
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
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/edits/create">OpenAI
    *   Doc</a>
    */
  def createEdit(
      input: String,
      instruction: String,
      settings: CreateEditSettings = DefaultSettings.CreateEdit
  ): Future[TextEditResponse]

  /** Creates an image given a prompt.
    *
    * @param prompt
    *   A text description of the desired image(s). The maximum length is 1000
    *   characters.
    * @param settings
    * @return
    *   image response (might contain multiple data items - one per image)
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/images/create">OpenAI
    *   Doc</a>
    */
  def createImage(
      prompt: String,
      settings: CreateImageSettings = DefaultSettings.CreateImage
  ): Future[ImageInfo]

  /** Creates an edited or extended image given an original image and a prompt.
    *
    * @param prompt
    *   A text description of the desired image(s). The maximum length is 1000
    *   characters.
    * @param image
    *   The image to edit. Must be a valid PNG file, less than 4MB, and square.
    *   If mask is not provided, image must have transparency, which will be
    *   used as the mask.
    * @param mask
    *   An additional image whose fully transparent areas (e.g. where alpha is
    *   zero) indicate where image should be edited. Must be a valid PNG file,
    *   less than 4MB, and have the same dimensions as image.
    * @param settings
    * @return
    *   image response (might contain multiple data items - one per image)
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/images/create-edit">OpenAI
    *   Doc</a>
    */
  def createImageEdit(
      prompt: String,
      image: File,
      mask: Option[File] = None,
      settings: CreateImageSettings = DefaultSettings.CreateImageEdit
  ): Future[ImageInfo]

  /** Creates a variation of a given image.
    *
    * @param image
    *   The image to use as the basis for the variation(s). Must be a valid PNG
    *   file, less than 4MB, and square.
    * @param settings
    * @return
    *   image response (might contain multiple data items - one per image)
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/images/create-variation">OpenAI
    *   Doc</a>
    */
  def createImageVariation(
      image: File,
      settings: CreateImageSettings = DefaultSettings.CreateImageVariation
  ): Future[ImageInfo]

  /** Creates an embedding vector representing the input text.
    *
    * @param input
    *   Input text to get embeddings for, encoded as a string or array of
    *   tokens. To get embeddings for multiple inputs in a single request, pass
    *   an array of strings or array of token arrays. Each input must not exceed
    *   8192 tokens in length.
    * @param settings
    * @return
    *   list of embeddings inside an envelope
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/embeddings/create">OpenAI
    *   Doc</a>
    */
  def createEmbeddings(
      input: Seq[String],
      settings: CreateEmbeddingsSettings = DefaultSettings.CreateEmbeddings
  ): Future[EmbeddingResponse]

  /** Transcribes audio into the input language.
    *
    * @param file
    *   The audio file to transcribe, in one of these formats: mp3, mp4, mpeg,
    *   mpga, m4a, wav, or webm.
    * @param prompt
    *   An optional text to guide the model's style or continue a previous audio
    *   segment. The prompt should match the audio language.
    * @param settings
    * @return
    *   transcription text
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/audio/create">OpenAI
    *   Doc</a>
    */
  def createAudioTranscription(
      file: File,
      prompt: Option[String] = None,
      settings: CreateTranscriptionSettings =
        DefaultSettings.CreateTranscription
  ): Future[TranscriptResponse]

  /** Translates audio into into English.
    *
    * @param file
    *   The audio file to translate, in one of these formats: mp3, mp4, mpeg,
    *   mpga, m4a, wav, or webm.
    * @param prompt
    *   An optional text to guide the model's style or continue a previous audio
    *   segment. The prompt should match the audio language.
    * @param settings
    * @return
    *   translation text
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/audio/create">OpenAI
    *   Doc</a>
    */
  def createAudioTranslation(
      file: File,
      prompt: Option[String] = None,
      settings: CreateTranslationSettings = DefaultSettings.CreateTranslation
  ): Future[TranscriptResponse]

  /** Returns a list of files that belong to the user's organization.
    *
    * @return
    *   file infos
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/files/list">OpenAI
    *   Doc</a>
    */
  def listFiles: Future[Seq[FileInfo]]

  /** Upload a file that contains document(s) to be used across various
    * endpoints/features. Currently, the size of all the files uploaded by one
    * organization can be up to 1 GB. Please contact us if you need to increase
    * the storage limit.
    *
    * @param file
    *   Name of the JSON Lines file to be uploaded. If the purpose is set to
    *   "fine-tune", each line is a JSON record with "prompt" and "completion"
    *   fields representing your <a
    *   href="https://platform.openai.com/docs/guides/fine-tuning/prepare-training-data">training
    *   examples</a>.
    * @param displayFileName
    *   (Explicit) display file name; if not specified a full path is used
    *   instead.
    * @param settings
    * @return
    *   file info
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/files/upload">OpenAI
    *   Doc</a>
    */
  def uploadFile(
      file: File,
      displayFileName: Option[String] = None,
      settings: UploadFileSettings = DefaultSettings.UploadFile
  ): Future[FileInfo]

  /** Delete a file.
    *
    * @param fileId
    *   The ID of the file to use for this request
    * @return
    *   enum indicating whether the file was deleted
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/files/delete">OpenAI
    *   Doc</a>
    */
  def deleteFile(
      fileId: String
  ): Future[DeleteResponse]

  /** Returns information about a specific file.
    *
    * @param fileId
    *   The ID of the file to use for this request
    * @return
    *   file info or None if not found
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/files/retrieve">OpenAI
    *   Doc</a>
    */
  def retrieveFile(
      fileId: String
  ): Future[Option[FileInfo]]

  /** Returns the contents of the specified file.
    *
    * @param fileId
    *   The ID of the file to use for this request
    * @return
    *   file content or None if not found
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/files/retrieve-content">OpenAI
    *   Doc</a>
    */
  def retrieveFileContent(
      fileId: String
  ): Future[Option[String]]

  /** Creates a job that fine-tunes a specified model from a given dataset.
    * Response includes details of the enqueued job including job status and the
    * name of the fine-tuned models once complete.
    *
    * @param training_file
    *   The ID of an uploaded file that contains training data. See
    *   <code>uploadFile</code> for how to upload a file. Your dataset must be
    *   formatted as a JSONL file, where each training example is a JSON object
    *   with the keys "prompt" and "completion". Additionally, you must upload
    *   your file with the purpose fine-tune.
    * @param validation_file
    *   The ID of an uploaded file that contains validation data. If you provide
    *   this file, the data is used to generate validation metrics periodically
    *   during fine-tuning. These metrics can be viewed in the fine-tuning
    *   results file. Your train and validation data should be mutually
    *   exclusive. Your dataset must be formatted as a JSONL file, where each
    *   validation example is a JSON object with the keys "prompt" and
    *   "completion". Additionally, you must upload your file with the purpose
    *   fine-tune.
    * @param settings
    * @return
    *   fine tune response
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/fine-tunes/create">OpenAI
    *   API Doc</a>
    * @see
    *   <a href="https://platform.openai.com/docs/guides/fine-tuning">OpenAI
    *   Fine-Tuning Guide</a>
    */
  def createFineTune(
      training_file: String,
      validation_file: Option[String] = None,
      settings: CreateFineTuneSettings = DefaultSettings.CreateFineTune
  ): Future[FineTuneJob]

  /** List your organization's fine-tuning jobs.
    *
    * @return
    *   fine tunes
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/fine-tunes/list">OpenAI
    *   Doc</a>
    */
  def listFineTunes: Future[Seq[FineTuneJob]]

  /** Gets info about the fine-tune job.
    *
    * @param fineTuneId
    *   The ID of the fine-tune job
    * @return
    *   fine tune info
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/fine-tunes/retrieve">OpenAI
    *   Doc</a>
    */
  def retrieveFineTune(
      fineTuneId: String
  ): Future[Option[FineTuneJob]]

  /** Immediately cancel a fine-tune job.
    *
    * @param fineTuneId
    *   The ID of the fine-tune job to cancel
    * @return
    *   fine tune info or None if not found
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/fine-tunes/cancel">OpenAI
    *   Doc</a>
    */
  def cancelFineTune(
      fineTuneId: String
  ): Future[Option[FineTuneJob]]

  /** Get fine-grained status updates for a fine-tune job.
    *
    * @param fineTuneId
    *   The ID of the fine-tune job to get events for.
    * @return
    *   fine tune events or None if not found
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/fine-tunes/events">OpenAI
    *   Doc</a>
    */
  def listFineTuneEvents(
      fineTuneId: String
  ): Future[Option[Seq[FineTuneEvent]]]

  /** Delete a fine-tuned model. You must have the Owner role in your
    * organization.
    *
    * @param modelId
    *   The ID of the file to use for this request
    * @return
    *   enum indicating whether the model was deleted
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/fine-tunes/delete-model">OpenAI
    *   Doc</a>
    */
  def deleteFineTuneModel(
      modelId: String
  ): Future[DeleteResponse]

  /** Classifies if text violates OpenAI's Content Policy.
    *
    * @param input
    *   The input text to classify
    * @param settings
    * @return
    *   moderation results
    *
    * @see
    *   <a
    *   href="https://platform.openai.com/docs/api-reference/moderations/create">OpenAI
    *   Doc</a>
    */
  def createModeration(
      input: String,
      settings: CreateModerationSettings = DefaultSettings.CreateModeration
  ): Future[ModerationResponse]

  /** Closes the underlying ws client, and releases all its resources.
    */
  def close: Unit
}
