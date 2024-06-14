package io.cequence.openaiscala.anthropic.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateEmbeddingsSettings,
  AnthropicCreateMessageSettings
}
import io.cequence.openaiscala.anthropic.domain.response.EmbeddingResponse
import io.cequence.wsclient.service.CloseableService

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

  /**
   * Uses the specified model to generate embeddings for the input sequence.
   *
   * @param inputs
   *   Input sequence for which to generate embeddings.
   * @param settings
   * @return
   *   list of embeddings inside an envelope
   */
  def createEmbeddings(
    inputs: Seq[String],
    settings: AnthropicCreateEmbeddingsSettings = DefaultSettings.CreateEmbeddings
  ): Future[EmbeddingResponse]

}
