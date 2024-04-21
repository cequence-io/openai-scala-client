package io.cequence.openaiscala.v2.service

import io.cequence.openaiscala.v2.domain.BaseMessage
import io.cequence.openaiscala.v2.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.v2.domain.settings.CreateChatCompletionSettings

import scala.concurrent.Future

/**
 * Service that offers <b>ONLY</b> OpenAI chat completion endpoint. Note that this trais is
 * usable also for OpenAI-API-compatible services such as FastChat, Ollama, or OctoML.
 *
 * @since March
 *   2024
 */
trait OpenAIChatCompletionService extends OpenAIServiceConsts with CloseableService {

  /**
   * Creates a model response for the given chat conversation.
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
}
