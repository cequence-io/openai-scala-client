package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChunkResponse,
  ChatCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}
import io.cequence.openaiscala.anthropic.service._
import io.cequence.openaiscala._

import scala.concurrent.{ExecutionContext, Future}

private[service] class OpenAIAnthropicChatCompletionService(
  underlying: AnthropicService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra {

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
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    underlying
      .createMessage(
        toAnthropicSystemMessages(messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(messages.filter(!_.isSystem), settings),
        toAnthropicSettings(settings)
      )
      .map(toOpenAI)
  }.recoverWith {
    case e: AnthropicScalaTokenCountExceededException =>
      Future.failed(new OpenAIScalaTokenCountExceededException(e.getMessage, e))
    case e: AnthropicScalaUnauthorizedException =>
      Future.failed(new OpenAIScalaUnauthorizedException(e.getMessage, e))
    case e: AnthropicScalaRateLimitException =>
      Future.failed(new OpenAIScalaRateLimitException(e.getMessage, e))
    case e: AnthropicScalaServerErrorException =>
      Future.failed(new OpenAIScalaServerErrorException(e.getMessage, e))
    case e: AnthropicScalaEngineOverloadedException =>
      Future.failed(new OpenAIScalaEngineOverloadedException(e.getMessage, e))
    case e: AnthropicScalaClientTimeoutException =>
      Future.failed(new OpenAIScalaClientTimeoutException(e.getMessage, e))
    case e: AnthropicScalaClientUnknownHostException =>
      Future.failed(new OpenAIScalaClientUnknownHostException(e.getMessage, e))
    case e: AnthropicScalaNotFoundException =>
      Future.failed(new OpenAIScalaClientException(e.getMessage, e))
    case e: AnthropicScalaClientException =>
      Future.failed(new OpenAIScalaClientException(e.getMessage, e))
  }

  /**
   * Creates a completion for the chat message(s) with streamed results.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    underlying
      .createMessageStreamed(
        toAnthropicSystemMessages(messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(messages.filter(!_.isSystem), settings),
        toAnthropicSettings(settings)
      )
      .map(toOpenAI)

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}
