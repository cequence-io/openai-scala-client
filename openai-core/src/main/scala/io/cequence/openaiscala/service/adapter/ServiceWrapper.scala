package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{
  BaseMessage,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionTool
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}
import io.cequence.openaiscala.service.adapter.ServiceWrapperTypes._
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapperTypes.CloseableServiceWrapper
import io.cequence.wsclient.service.adapter.DelegatedCloseableServiceWrapper

import scala.concurrent.Future

trait DelegatedChatCompletionCloseableServiceWrapper[+S <: CloseableService]
    extends DelegatedCloseableServiceWrapper[S, ChatCompletionCloseableServiceWrapper[S]]
    with OpenAIChatCompletionService {

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = delegate.createChatCompletion(messages, settings)

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    delegate.createChatToolCompletion(messages, tools, responseToolChoice, settings)
}

/**
 * Batch-capable variant of [[DelegatedChatCompletionCloseableServiceWrapper]]: additionally
 * forwards the provider-agnostic batch endpoints to a batch-capable `delegate` (e.g. a
 * batch-routing [[ChatCompletionBatchServiceAdapter]]). Used to build the `S with
 * OpenAIChatCompletionBatchService` result of
 * `OpenAIServiceAdapters.chatCompletionBatchRouter`.
 */
trait DelegatedChatCompletionBatchCloseableServiceWrapper[+S <: CloseableService]
    extends DelegatedChatCompletionCloseableServiceWrapper[S]
    with OpenAIChatCompletionBatchService {

  override protected def delegate: ChatCompletionBatchCloseableServiceWrapper[S]

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] =
    delegate.createChatCompletionBatch(requests, settings)

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    delegate.getChatCompletionBatch(batchId, model)

  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    delegate.retrieveChatCompletionBatchResults(batchId, model)

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    delegate.cancelChatCompletionBatch(batchId, model)

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] =
    delegate.deleteChatCompletionBatch(batchId, model)
}

object ServiceWrapperTypes {
  type ChatCompletionCloseableServiceWrapper[+S] = CloseableServiceWrapper[S]
    with OpenAIChatCompletionService

  type ChatCompletionBatchCloseableServiceWrapper[+S] =
    ChatCompletionCloseableServiceWrapper[S] with OpenAIChatCompletionBatchService
}
