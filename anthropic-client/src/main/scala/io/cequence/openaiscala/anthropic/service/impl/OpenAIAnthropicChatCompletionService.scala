package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

class OpenAIAnthropicChatCompletionService extends OpenAIChatCompletionService {

  /**
   * Creates a model response for the given chat conversation.
   *
   * @param messages
   * A list of messages comprising the conversation so far.
   * @param settings
   * @return
   * chat completion response
   * @see
   * <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  override def createChatCompletion(messages: Seq[BaseMessage], settings: CreateChatCompletionSettings): Future[ChatCompletionResponse] = ???

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = ???
}
