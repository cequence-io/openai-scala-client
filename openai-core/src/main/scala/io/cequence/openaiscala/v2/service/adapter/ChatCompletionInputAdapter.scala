package io.cequence.openaiscala.v2.service.adapter

import io.cequence.openaiscala.v2.domain.BaseMessage
import io.cequence.openaiscala.v2.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.v2.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.v2.service

import scala.concurrent.Future

private class ChatCompletionInputAdapter[S <: service.OpenAIChatCompletionService](
  adaptMessages: Seq[BaseMessage] => Seq[BaseMessage],
  adaptSettings: CreateChatCompletionSettings => CreateChatCompletionSettings
)(
  underlying: S
) extends ServiceWrapper[S]
    with service.CloseableService
    with service.OpenAIChatCompletionService {

  // we just delegate all the calls to the underlying service
  override protected[adapter] def wrap[T](
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

  override def close(): Unit =
    underlying.close()
}
