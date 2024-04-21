package io.cequence.openaiscala.v2.service.adapter

import akka.actor.Scheduler
import io.cequence.openaiscala.RetryHelpers.RetrySettings
import io.cequence.openaiscala.v2.domain.BaseMessage
import io.cequence.openaiscala.v2.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.v2.service._
import io.cequence.openaiscala.v2.service.adapter.ServiceWrapperTypes._

import scala.concurrent.{ExecutionContext, Future}

object OpenAIServiceAdapters {

  def forChatCompletionService: OpenAIServiceAdapters[OpenAIChatCompletionService] =
    new OpenAIChatCompletionServiceAdaptersImpl()

  def forCoreService: OpenAIServiceAdapters[OpenAICoreService] =
    new OpenAICoreServiceAdaptersImpl()

  def forFullService: OpenAIServiceAdapters[OpenAIService] =
    new OpenAIServiceAdaptersImpl()
}

trait OpenAIServiceAdapters[S <: CloseableService] {

  def roundRobin(
    underlyings: S*
  ): S =
    wrapAndDelegate(new RoundRobinAdapter(underlyings))

  def randomOrder(
    underlyings: S*
  ): S =
    wrapAndDelegate(new RandomOrderAdapter(underlyings))

  def retry(
    underlying: S,
    log: Option[String => Unit] = None
  )(
    implicit ec: ExecutionContext,
    retrySettings: RetrySettings,
    scheduler: Scheduler
  ): S =
    wrapAndDelegate(new RetryServiceAdapter(underlying, log))

  def log(
    underlying: S,
    serviceName: String,
    log: String => Unit
  ): S =
    wrapAndDelegate(new LogServiceAdapter(underlying, serviceName, log))

  def preAction(
    underlying: S,
    action: () => Future[Unit]
  )(
    implicit ec: ExecutionContext
  ): S =
    wrapAndDelegate(new PreServiceAdapter(underlying, action))

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

  protected def wrapAndDelegate(
    delegate: CloseableServiceWrapper[S]
  ): S

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
