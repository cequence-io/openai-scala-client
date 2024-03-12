package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.service.response.CreateMessageResponse

import scala.concurrent.Future

trait AnthropicService {

  /**
   * Creates a Message.
   *
   * Send a structured list of input messages with text and/or image content, and the model
   * will generate the next message in the conversation.
   *
   * The Messages API can be used for for either single queries or stateless multi-turn
   * conversations.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @param settings
   * @return
   *   create message response
   */
  def createMessage(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings
  ): Future[CreateMessageResponse]

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  def close(): Unit
}
