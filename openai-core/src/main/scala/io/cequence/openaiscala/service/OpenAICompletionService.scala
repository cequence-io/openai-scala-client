package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.response.TextCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateCompletionSettings
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future

/**
 * Service that offers <b>ONLY</b> OpenAI completion endpoint. Note that this trait is usable
 * also for OpenAI-API-compatible services such as FastChat, Ollama, or OctoML.
 *
 * @since Apr
 *   2024
 */
trait OpenAICompletionService extends OpenAIServiceConsts with CloseableService {

  /**
   * Creates a completion for the provided prompt and parameters.
   *
   * @param prompt
   *   The prompt(s) to generate completions for, encoded as a string, array of strings, array
   *   of tokens, or array of token arrays. Note that <|endoftext|> is the document separator
   *   that the model sees during training, so if a prompt is not specified the model will
   *   generate as if from the beginning of a new document.
   * @param settings
   * @return
   *   text completion response
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/completions/create">OpenAI
   *   Doc</a>
   */
  def createCompletion(
    prompt: String,
    settings: CreateCompletionSettings = DefaultSettings.CreateCompletion
  ): Future[TextCompletionResponse]

}
