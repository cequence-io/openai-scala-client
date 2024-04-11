package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.BaseMessage

import scala.concurrent.Future

/**
 * Central service to access <b>core</b> public OpenAI WS endpoints as defined at <a
 * href="https://platform.openai.com/docs/api-reference">the API ref. page</a> or compatible
 * ones provided e.g. by FastChat <a
 * href="https://github.com/lm-sys/FastChat/blob/main/docs/openai_api.md">FastChat</a>.
 *
 * The following services are supported:
 *
 *   - '''Models''': listModels
 *   - '''Completions''': createCompletion
 *   - '''Chat Completions''': createChatCompletion
 *   - '''Embeddings''': createEmbeddings
 *
 * @since July
 *   2023
 */
trait OpenAICoreService extends OpenAIChatCompletionService with OpenAICompletionService {

  /**
   * Lists the currently available models, and provides basic information about each one such
   * as the owner and availability.
   *
   * @return
   *   models
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/models/list">OpenAI Doc</a>
   */
  def listModels: Future[Seq[ModelInfo]]

  /**
   * Creates a completion for the provided prompt and parameters. Note that this is defined *
   * already in [[OpenAICompletionService]], but it is repeated here for clarity.
   *
   * @param prompt
   *   The prompt(s) to generate completions for, encoded as a string, array of strings, array
   *   of tokens, or array of token arrays. Note that <|endoftext|> is the document separator
   *   that the model sees during training, so if a prompt is not specified the model will
   *   generate as if from the beginning of a new document.
   * @param settings
   * @return
   *   text completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/completions/create">OpenAI
   *   Doc</a>
   */
  def createCompletion(
    prompt: String,
    settings: CreateCompletionSettings = DefaultSettings.CreateCompletion
  ): Future[TextCompletionResponse]

  /**
   * Creates a model response for the given chat conversation. Note that this is defined
   * already in [[OpenAIChatCompletionService]], but it is repeated here for clarity.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Future[ChatCompletionResponse]

  /**
   * Creates an embedding vector representing the input text.
   *
   * @param input
   *   Input text to get embeddings for, encoded as a string or array of tokens. To get
   *   embeddings for multiple inputs in a single request, pass an array of strings or array of
   *   token arrays. Each input must not exceed 8192 tokens in length.
   * @param settings
   * @return
   *   list of embeddings inside an envelope
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/embeddings/create">OpenAI
   *   Doc</a>
   */
  def createEmbeddings(
    input: Seq[String],
    settings: CreateEmbeddingsSettings = DefaultSettings.CreateEmbeddings
  ): Future[EmbeddingResponse]
}
