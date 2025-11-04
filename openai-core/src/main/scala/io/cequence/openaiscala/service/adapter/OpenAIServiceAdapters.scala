package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ChatCompletionInterceptData
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.ServiceWrapperTypes._
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapperTypes.CloseableServiceWrapper

import scala.concurrent.{ExecutionContext, Future}

object OpenAIServiceAdapters {

  def forChatCompletionService: OpenAIServiceAdapters[OpenAIChatCompletionService] =
    new OpenAIChatCompletionServiceAdaptersImpl()

  def forCoreService: OpenAIServiceAdapters[OpenAICoreService] =
    new OpenAICoreServiceAdaptersImpl()

  def forFullService: OpenAIServiceAdapters[OpenAIService] =
    new OpenAIServiceAdaptersImpl()
}

trait OpenAIServiceAdapters[S <: CloseableService] extends ServiceAdapters[S] {

  def chatCompletion(
    chatCompletionService: OpenAIChatCompletionService,
    service: S
  ): S =
    wrapAndDelegateChatCompletion(
      new ChatCompletionServiceAdapter(chatCompletionService, service)
    )

  def chatCompletionInput(
    adaptMessages: Seq[BaseMessage] => Seq[BaseMessage],
    adaptSettings: CreateChatCompletionSettings => CreateChatCompletionSettings
  )(
    service: S with OpenAIChatCompletionService
  ): S =
    wrapAndDelegateChatCompletion(
      new ChatCompletionInputAdapter(adaptMessages, adaptSettings)(service)
    )

  def chatCompletionOutput(
    adaptMessage: AssistantMessage => AssistantMessage
  )(
    service: S with OpenAIChatCompletionService
  )(
    implicit ec: ExecutionContext
  ): S =
    wrapAndDelegateChatCompletion(
      new ChatCompletionOutputAdapter(adaptMessage)(service)
    )

  def chatCompletionIntercept(
    intercept: ChatCompletionInterceptData => Future[Unit],
    adjustSettingsForCall: CreateChatCompletionSettings => CreateChatCompletionSettings =
      identity[CreateChatCompletionSettings]
  )(
    service: S with OpenAIChatCompletionService
  )(
    implicit ec: ExecutionContext
  ): S =
    wrapAndDelegateChatCompletion(
      new ChatCompletionInterceptAdapter(intercept, adjustSettingsForCall)(service)
    )

  def chatCompletionRouter(
    serviceModels: Map[OpenAIChatCompletionService, Seq[String]],
    service: S with OpenAIChatCompletionService
  ): S = {
    val chatCompletionService =
      OpenAIChatCompletionServiceRouter(serviceModels, service)
    wrapAndDelegateChatCompletion(
      new ChatCompletionServiceAdapter(chatCompletionService, service)
    )
  }

  def chatCompletionRouterMapped(
    serviceModels: Map[OpenAIChatCompletionService, Seq[MappedModel]],
    service: S with OpenAIChatCompletionService
  ): S = {
    val chatCompletionService =
      OpenAIChatCompletionServiceRouter.applyMapped(serviceModels, service)
    wrapAndDelegateChatCompletion(
      new ChatCompletionServiceAdapter(chatCompletionService, service)
    )
  }

  def chatToCompletion(
    service: S with OpenAICompletionService with OpenAIChatCompletionService
  )(
    implicit ec: ExecutionContext
  ): S =
    wrapAndDelegateChatCompletion(new ChatToCompletionAdapter(service))

  protected def wrapAndDelegateChatCompletion(
    delegate: ChatCompletionCloseableServiceWrapper[S]
  ): S
}

private class OpenAIChatCompletionServiceAdaptersImpl
    extends OpenAIServiceAdapters[OpenAIChatCompletionService] {
  override protected def wrapAndDelegate(
    delegate: CloseableServiceWrapper[OpenAIChatCompletionService]
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceWrapperImpl(delegate)

  override protected def wrapAndDelegateChatCompletion(
    delegate: ChatCompletionCloseableServiceWrapper[OpenAIChatCompletionService]
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceExtWrapperImpl(delegate)

}

private class OpenAICoreServiceAdaptersImpl extends OpenAIServiceAdapters[OpenAICoreService] {
  override protected def wrapAndDelegate(
    delegate: CloseableServiceWrapper[OpenAICoreService]
  ): OpenAICoreService =
    new OpenAICoreServiceWrapperImpl(delegate)

  override protected def wrapAndDelegateChatCompletion(
    delegate: ChatCompletionCloseableServiceWrapper[OpenAICoreService]
  ): OpenAICoreService =
    new OpenAICoreServiceExtWrapperImpl(delegate)
}

private class OpenAIServiceAdaptersImpl extends OpenAIServiceAdapters[OpenAIService] {
  override protected def wrapAndDelegate(
    delegate: CloseableServiceWrapper[OpenAIService]
  ): OpenAIService =
    new OpenAIServiceWrapperImpl(delegate)

  override protected def wrapAndDelegateChatCompletion(
    delegate: ChatCompletionCloseableServiceWrapper[OpenAIService]
  ): OpenAIService =
    new OpenAIServiceExtWrapperImpl(delegate)
}
