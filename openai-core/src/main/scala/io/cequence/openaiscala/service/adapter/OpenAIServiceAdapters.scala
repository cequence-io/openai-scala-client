package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage,
  ChatCompletionErrorInterceptData,
  ChatCompletionInterceptData
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service._
import io.cequence.openaiscala.service.adapter.ServiceWrapperTypes._
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapperTypes.CloseableServiceWrapper
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

object OpenAIServiceAdapters {

  private lazy val logger = LoggerFactory.getLogger("OpenAIServiceAdapters")

  def forChatCompletionService: OpenAIServiceAdapters[OpenAIChatCompletionService] =
    new OpenAIChatCompletionServiceAdaptersImpl()

  def forCoreService: OpenAIServiceAdapters[OpenAICoreService] =
    new OpenAICoreServiceAdaptersImpl()

  def forFullService: OpenAIServiceAdapters[OpenAIService] =
    new OpenAIServiceAdaptersImpl()

  /**
   * Makes a plain (non-batch) [[OpenAIChatCompletionService]] batch-capable by '''emulating'''
   * batch on top of synchronous chat completion: each batch is run as ordinary chat-completion
   * calls (up to `maxParallelism` concurrently), with a warning logged (via [[warn]]) that
   * native batch is unavailable. Use it to register an otherwise-unsupported provider in
   * [[OpenAIServiceAdapters.chatCompletionBatchRouter]] as a fallback, so the router can mix
   * natively-batching providers with emulated ones. See
   * [[ChatCompletionBatchEmulationAdapter]] for the caveats (no batch discount, no async
   * processing, results held in memory, bounded by `maxRetainedBatches`).
   *
   * @param maxParallelism
   *   Max number of requests run concurrently per emulated batch.
   * @param maxRetainedBatches
   *   Max number of emulated batches kept in memory at once; past this, the oldest one is
   *   evicted on a FIFO basis.
   */
  def chatCompletionBatchEmulated(
    service: OpenAIChatCompletionService,
    warn: String => Unit = logger.warn,
    maxParallelism: Int = 10,
    maxRetainedBatches: Int = 100
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
    new ChatCompletionBatchEmulationAdapter(service, warn, maxParallelism, maxRetainedBatches)
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

  def chatCompletionErrorIntercept(
    intercept: ChatCompletionErrorInterceptData => Future[Unit],
    adjustSettingsForCall: CreateChatCompletionSettings => CreateChatCompletionSettings =
      identity[CreateChatCompletionSettings]
  )(
    service: S with OpenAIChatCompletionService
  )(
    implicit ec: ExecutionContext
  ): S =
    wrapAndDelegateChatCompletion(
      new ChatCompletionErrorInterceptAdapter(intercept, adjustSettingsForCall)(service)
    )

  def chatCompletionRouter[T <: OpenAIChatCompletionService](
    serviceModels: Map[T, Seq[String]],
    service: S with OpenAIChatCompletionService
  ): S = {
    val chatCompletionService =
      OpenAIChatCompletionServiceRouter(serviceModels, service)
    wrapAndDelegateChatCompletion(
      new ChatCompletionServiceAdapter(chatCompletionService, service)
    )
  }

  def chatCompletionRouterMapped[T <: OpenAIChatCompletionService](
    serviceModels: Map[T, Seq[MappedModel]],
    service: S with OpenAIChatCompletionService
  ): S = {
    val chatCompletionService =
      OpenAIChatCompletionServiceRouter.applyMapped(serviceModels, service)
    wrapAndDelegateChatCompletion(
      new ChatCompletionServiceAdapter(chatCompletionService, service)
    )
  }

  /**
   * Batch-aware sibling of [[chatCompletionRouter]]: routes chat completion '''and''' the
   * provider-agnostic batch endpoints by model, while keeping the rest of `service`'s
   * capabilities. Every registered service and the default (`service`) must be batch-capable,
   * so the result additionally exposes [[OpenAIChatCompletionBatchService]]. Works with all
   * three factories - e.g. `forFullService.chatCompletionBatchRouter(...)` returns an
   * `OpenAIService` whose chat completion and batch are routed but whose files/assistants/...
   * still delegate to `service`.
   */
  def chatCompletionBatchRouter[T <: OpenAIChatCompletionServiceRouter.BatchChatService](
    serviceModels: Map[T, Seq[String]],
    service: S with OpenAIChatCompletionService with OpenAIChatCompletionBatchService
  ): S with OpenAIChatCompletionBatchService = {
    val batchChatService =
      OpenAIChatCompletionServiceRouter.applyWithBatch(serviceModels, service)
    wrapAndDelegateChatCompletionBatch(
      new ChatCompletionBatchServiceAdapter[S](batchChatService, service)
    )
  }

  def chatCompletionBatchRouterMapped[T <: OpenAIChatCompletionServiceRouter.BatchChatService](
    serviceModels: Map[T, Seq[MappedModel]],
    service: S with OpenAIChatCompletionService with OpenAIChatCompletionBatchService
  ): S with OpenAIChatCompletionBatchService = {
    val batchChatService =
      OpenAIChatCompletionServiceRouter.applyWithBatchMapped(serviceModels, service)
    wrapAndDelegateChatCompletionBatch(
      new ChatCompletionBatchServiceAdapter[S](batchChatService, service)
    )
  }

  /**
   * Mixed-capability sibling of [[chatCompletionBatchRouter]] for maps that combine
   * batch-capable and plain (non-batch) chat-completion services under a single key type -
   * e.g. OpenAI/Anthropic/Gemini (batch-capable) mixed with Groq/Grok/Mistral/... (chat-only)
   * in one `Map[OpenAIChatCompletionService, Seq[String]]`. Chat completion routes to
   * '''all''' registered services by model, exactly like [[chatCompletionRouter]]. Batch calls
   * route to a registered service only if it is actually batch-capable; a model mapped to a
   * non-batch service fails fast with a helpful error instead of silently falling back to the
   * default or crashing with an erasure-related `IncompatibleClassChangeError` - see
   * [[OpenAIChatCompletionServiceRouter.applyWithBatchMixed]].
   */
  def chatCompletionBatchRouterMixed[T <: OpenAIChatCompletionService](
    serviceModels: Map[T, Seq[String]],
    service: S with OpenAIChatCompletionService with OpenAIChatCompletionBatchService
  ): S with OpenAIChatCompletionBatchService = {
    val batchChatService =
      OpenAIChatCompletionServiceRouter.applyWithBatchMixed(serviceModels, service)
    wrapAndDelegateChatCompletionBatch(
      new ChatCompletionBatchServiceAdapter[S](batchChatService, service)
    )
  }

  def chatCompletionBatchRouterMixedMapped[T <: OpenAIChatCompletionService](
    serviceModels: Map[T, Seq[MappedModel]],
    service: S with OpenAIChatCompletionService with OpenAIChatCompletionBatchService
  ): S with OpenAIChatCompletionBatchService = {
    val batchChatService =
      OpenAIChatCompletionServiceRouter.applyWithBatchMixedMapped(serviceModels, service)
    wrapAndDelegateChatCompletionBatch(
      new ChatCompletionBatchServiceAdapter[S](batchChatService, service)
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

  protected def wrapAndDelegateChatCompletionBatch(
    delegate: ChatCompletionBatchCloseableServiceWrapper[S]
  ): S with OpenAIChatCompletionBatchService
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

  override protected def wrapAndDelegateChatCompletionBatch(
    delegate: ChatCompletionBatchCloseableServiceWrapper[OpenAIChatCompletionService]
  ): OpenAIChatCompletionService with OpenAIChatCompletionBatchService =
    new OpenAIChatCompletionServiceBatchExtWrapperImpl(delegate)

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

  override protected def wrapAndDelegateChatCompletionBatch(
    delegate: ChatCompletionBatchCloseableServiceWrapper[OpenAICoreService]
  ): OpenAICoreService with OpenAIChatCompletionBatchService =
    new OpenAICoreServiceBatchExtWrapperImpl(delegate)
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

  override protected def wrapAndDelegateChatCompletionBatch(
    delegate: ChatCompletionBatchCloseableServiceWrapper[OpenAIService]
  ): OpenAIService with OpenAIChatCompletionBatchService =
    new OpenAIServiceBatchExtWrapperImpl(delegate)
}
