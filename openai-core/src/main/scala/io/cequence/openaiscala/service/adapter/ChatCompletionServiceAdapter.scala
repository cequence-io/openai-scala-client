package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{
  BaseMessage,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionTool
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.wsclient.service.adapter.ServiceWrapper

private class ChatCompletionServiceAdapter[S <: CloseableService](
  chatCompletionService: OpenAIChatCompletionService,
  underlying: S
) extends ServiceWrapper[S]
    with CloseableService
    with OpenAIChatCompletionService {

  // we just delegate all the calls to the underlying service
  override def wrap[T](
    fun: S => Future[T]
  ): Future[T] = fun(underlying)

  // but for the chat completion we use the chatCompletionService
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    chatCompletionService.createChatCompletion(messages, settings)

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    chatCompletionService.createChatToolCompletion(
      messages,
      tools,
      responseToolChoice,
      settings
    )

  override def close(): Unit = {
    chatCompletionService.close()
    underlying.close()
  }
}

/**
 * Batch-capable variant of [[ChatCompletionServiceAdapter]]: swaps both chat completion
 * '''and''' the provider-agnostic batch endpoints to `batchChatService` (a batch-routing
 * service), while every other call still delegates to `underlying`. Used to build the `S with
 * OpenAIChatCompletionBatchService` result of
 * `OpenAIServiceAdapters.chatCompletionBatchRouter`.
 */
private class ChatCompletionBatchServiceAdapter[S <: CloseableService](
  batchChatService: OpenAIChatCompletionService with OpenAIChatCompletionBatchService,
  underlying: S
) extends ServiceWrapper[S]
    with CloseableService
    with OpenAIChatCompletionService
    with OpenAIChatCompletionBatchService {

  override def wrap[T](
    fun: S => Future[T]
  ): Future[T] = fun(underlying)

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    batchChatService.createChatCompletion(messages, settings)

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    batchChatService.createChatToolCompletion(messages, tools, responseToolChoice, settings)

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] =
    batchChatService.createChatCompletionBatch(requests, settings)

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    batchChatService.getChatCompletionBatch(batchId, model)

  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    batchChatService.retrieveChatCompletionBatchResults(batchId, model)

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    batchChatService.cancelChatCompletionBatch(batchId, model)

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] =
    batchChatService.deleteChatCompletionBatch(batchId, model)

  override def close(): Unit = {
    batchChatService.close()
    underlying.close()
  }
}
