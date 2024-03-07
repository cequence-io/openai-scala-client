package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{CloseableService, OpenAIChatCompletionService}

import scala.concurrent.Future

private class ChatCompletionServiceAdapter[S <: CloseableService](
  chatCompletionService: OpenAIChatCompletionService,
  underlying: S
) extends ServiceWrapper[S]
    with CloseableService
    with OpenAIChatCompletionService {

  // we just delegate all calls to the underlying service
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
