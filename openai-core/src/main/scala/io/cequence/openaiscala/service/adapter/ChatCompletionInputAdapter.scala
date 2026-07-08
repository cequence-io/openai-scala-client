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
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapper

import scala.concurrent.Future

private class ChatCompletionInputAdapter[S <: OpenAIChatCompletionService](
  adaptMessages: Seq[BaseMessage] => Seq[BaseMessage],
  adaptSettings: CreateChatCompletionSettings => CreateChatCompletionSettings
)(
  underlying: S
) extends ServiceWrapper[S]
    with CloseableService
    with OpenAIChatCompletionService {

  // we just delegate all the calls to the underlying service
  override def wrap[T](
    fun: S => Future[T]
  ): Future[T] = fun(underlying)

  // but for the chat completion we adapt the messages and settings
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    underlying.createChatCompletion(
      adaptMessages(messages),
      adaptSettings(settings)
    )

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    underlying.createChatToolCompletion(
      adaptMessages(messages),
      tools,
      responseToolChoice,
      adaptSettings(settings)
    )

  override def close(): Unit =
    underlying.close()

}

/**
 * Batch-preserving variant of [[ChatCompletionInputAdapter]] for batch-capable underlying
 * services: the plain adapter's wrapper exposes only [[OpenAIChatCompletionService]], so
 * wrapping e.g. the Anthropic or Gemini `asOpenAI` service with it would hide the batch
 * endpoints from capability checks (notably the mixed batch router's). This one additionally
 * forwards the batch endpoints, applying `adaptMessages` to every request's messages and
 * `adaptSettings` to the shared settings on submit. The id/model-keyed calls
 * (get/retrieve/cancel/delete) are forwarded untouched - the `model` there is a routing key,
 * not a settings payload.
 */
private class ChatCompletionInputBatchAdapter[
  S <: OpenAIChatCompletionService with OpenAIChatCompletionBatchService
](
  adaptMessages: Seq[BaseMessage] => Seq[BaseMessage],
  adaptSettings: CreateChatCompletionSettings => CreateChatCompletionSettings
)(
  underlying: S
) extends ServiceWrapper[S]
    with CloseableService
    with OpenAIChatCompletionService
    with OpenAIChatCompletionBatchService {

  // we just delegate all the calls to the underlying service
  override def wrap[T](
    fun: S => Future[T]
  ): Future[T] = fun(underlying)

  // but for the chat completion (and batch submission) we adapt the messages and settings
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    underlying.createChatCompletion(
      adaptMessages(messages),
      adaptSettings(settings)
    )

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    underlying.createChatToolCompletion(
      adaptMessages(messages),
      tools,
      responseToolChoice,
      adaptSettings(settings)
    )

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] =
    underlying.createChatCompletionBatch(
      requests.map(request => request.copy(messages = adaptMessages(request.messages))),
      adaptSettings(settings)
    )

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    underlying.getChatCompletionBatch(batchId, model)

  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    underlying.retrieveChatCompletionBatchResults(batchId, model)

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    underlying.cancelChatCompletionBatch(batchId, model)

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] =
    underlying.deleteChatCompletionBatch(batchId, model)

  override def close(): Unit =
    underlying.close()

}
