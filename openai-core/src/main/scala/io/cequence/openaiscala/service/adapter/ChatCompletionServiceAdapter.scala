package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future
import io.cequence.openaiscala.domain.response.ChatCompletionResponse

private class ChatCompletionServiceAdapter[S <: CloseableService](
  chatCompletionService: OpenAIChatCompletionService,
  underlying: S
) extends ServiceWrapper[S]
    with CloseableService
    with OpenAIChatCompletionService {

  // we just delegate all the calls to the underlying service
  override protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] = fun(underlying)

  // but for the chat completion we use the chatCompletionService
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    chatCompletionService.createChatCompletion(messages, settings)

  override def close(): Unit = {
    chatCompletionService.close()
    underlying.close()
  }

}
