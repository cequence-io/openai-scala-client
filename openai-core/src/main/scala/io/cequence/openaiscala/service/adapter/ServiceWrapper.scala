package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
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

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    delegate.createChatToolCompletion(messages, tools, responseToolChoice, settings)
}

object ServiceWrapperTypes {
  type ChatCompletionCloseableServiceWrapper[+S] = CloseableServiceWrapper[S]
    with OpenAIChatCompletionService
}
