package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService
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

  override def close(): Unit =
    underlying.close()

}
