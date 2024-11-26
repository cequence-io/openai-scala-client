package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService
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
}

object ServiceWrapperTypes {
  type ChatCompletionCloseableServiceWrapper[+S] = CloseableServiceWrapper[S]
    with OpenAIChatCompletionService
}
