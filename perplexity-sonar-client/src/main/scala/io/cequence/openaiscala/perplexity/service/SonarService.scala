package io.cequence.openaiscala.perplexity.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.perplexity.domain.response.{
  SonarChatCompletionChunkResponse,
  SonarChatCompletionResponse
}
import io.cequence.wsclient.service.CloseableService
import io.cequence.openaiscala.perplexity.domain.Message

import scala.concurrent.Future
import io.cequence.openaiscala.perplexity.domain.settings.SonarCreateChatCompletionSettings

trait SonarService extends CloseableService with SonarServiceConsts {

  /**
   * Generates a model’s response for the given chat conversation..
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://https://docs.perplexity.ai/api-reference/chat-completions">Perplexity
   *   Docs</a>
   */
  def createChatCompletion(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Future[SonarChatCompletionResponse]

  /**
   * Generates a model’s response for the given chat conversation with streamed results.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://https://docs.perplexity.ai/api-reference/chat-completions">Perplexity
   *   Docs</a>
   */
  def createChatCompletionStreamed(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Source[SonarChatCompletionChunkResponse, NotUsed]
}
