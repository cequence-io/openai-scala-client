package io.cequence.openaiscala.v2.service.adapter

import io.cequence.openaiscala.v2.domain.BaseMessage
import io.cequence.openaiscala.v2.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.v2.service

import scala.concurrent.Future

private class ChatCompletionServiceAdapter[S <: service.CloseableService](
  chatCompletionService: service.OpenAIChatCompletionService,
  underlying: S
) extends ServiceWrapper[S]
    with service.CloseableService
    with service.OpenAIChatCompletionService {

  // we just delegate all the calls to the underlying service
  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] = fun(underlying)

  // but for the chat completion we use the chatCompletionService
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ) =
    chatCompletionService.createChatCompletion(messages, settings)

  override def close(): Unit = {
    chatCompletionService.close()
    underlying.close()
  }
}
