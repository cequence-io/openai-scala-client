package io.cequence.openaiscala.anthropic.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.{Message, ToolSpec}
import io.cequence.openaiscala.anthropic.domain.response.{ContentBlockDelta, CreateMessageResponse}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.service.CloseableService

import scala.concurrent.Future

trait AnthropicService extends CloseableService with AnthropicServiceConsts {

  /**
   * Creates a message.
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
    settings: AnthropicCreateMessageSettings = DefaultSettings.CreateMessage
  ): Future[CreateMessageResponse]


  // TODO:
  /**
   * Creates a message.
   *
   * Send a structured list of input messages with text and/or image content, and the model
   * will generate the next message in the conversation.
   *
   * The Messages API can be used for for either single queries or stateless multi-turn
   * conversations.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param tools
   * [beta] Definitions of tools that the model may use.
   *
   * If you include tools in your API request, the model may return tool_use content blocks that represent the model's
   * use of those tools. You can then run those tools using the tool input generated by the model and then optionally
   * return results back to the model using tool_result content blocks.
   *
   * @param settings
   * @return
   *   create message response
   * @see
   *   <a href="https://docs.anthropic.com/claude/reference/messages_post">Anthropic Doc</a>
   */
  def createToolMessage(
    messages: Seq[Message],
    tools: Seq[ToolSpec],
    settings: AnthropicCreateMessageSettings = DefaultSettings.CreateMessage
  ): Future[CreateMessageResponse]

  /**
   * Creates a message (streamed version).
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
  def createMessageStreamed(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings = DefaultSettings.CreateMessage
  ): Source[ContentBlockDelta, NotUsed]
}
