package io.cequence.openaiscala.v2.service.adapter

import io.cequence.openaiscala.v2.domain.BaseMessage
import io.cequence.openaiscala.v2.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.v2.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.v2.service.adapter.ServiceWrapperTypes._
import io.cequence.openaiscala.v2.service.{CloseableService, OpenAIChatCompletionService}

import scala.concurrent.Future

trait ServiceWrapper[+S] {

  protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T]
}

trait DelegatedCloseableServiceWrapper[
  +S <: CloseableService,
  +W <: CloseableServiceWrapper[S]
] extends ServiceWrapper[S]
    with CloseableService {

  protected def delegate: W

  protected[adapter] def wrap[T](
    fun: S => Future[T]
  ): Future[T] = delegate.wrap(fun)

  override def close(): Unit =
    delegate.close()
}

trait DelegatedChatCompletionCloseableServiceWrapper[+S <: CloseableService]
    extends DelegatedCloseableServiceWrapper[S, ChatCompletionCloseableServiceWrapper[S]]
    with OpenAIChatCompletionService {

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = delegate.createChatCompletion(messages, settings)
}

object ServiceWrapperTypes {
  type CloseableServiceWrapper[+S] = ServiceWrapper[S] with CloseableService
  type ChatCompletionCloseableServiceWrapper[+S] = CloseableServiceWrapper[S]
    with OpenAIChatCompletionService
}
