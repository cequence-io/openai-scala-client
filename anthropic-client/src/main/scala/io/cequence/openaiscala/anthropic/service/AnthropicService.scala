package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.service.CloseableService

import scala.concurrent.Future

trait AnthropicService extends CloseableService with AnthropicServiceConsts {

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
   * @return
   *   create message response
   * @see
   *   <a href="https://docs.anthropic.com/claude/reference/messages_post">Anthropic Doc</a>
   */
  def createMessage(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings = DefaultSettings.createMessage
  ): Future[CreateMessageResponse]
}
