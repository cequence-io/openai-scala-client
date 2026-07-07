package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{
  BaseMessage,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionTool
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService
}

import scala.concurrent.Future

object OpenAIChatCompletionServiceRouter {

  /**
   * Chat-completion service (and provider adapter) with the provider-agnostic batch endpoints.
   * Batch is not part of the base [[OpenAIChatCompletionService]] - only services that
   * actually support it (the provider adapters, the full OpenAI service) carry it - so batch
   * routing is a separate entry point ([[applyWithBatch]]) that requires every routed service
   * to be batch-capable.
   */
  type BatchChatService = OpenAIChatCompletionService with OpenAIChatCompletionBatchService

  // Note on close() for apply/applyMapped/applyWithBatch/applyWithBatchMapped below: closing
  // the returned router closes the registered services but NOT the default service - the
  // default is closed by the wrapping adapter when the router is built via
  // OpenAIServiceAdapters.chatCompletionRouter/chatCompletionBatchRouter, and is the caller's
  // responsibility when the router is constructed directly (as here).
  // T is bounded (rather than the map keyed directly on OpenAIChatCompletionService) so that
  // callers holding a Map[SomeSubtype, ...] don't have to upcast the keys themselves - Map is
  // invariant in its key type, so Map[T, X] is not a Map[OpenAIChatCompletionService, X] even
  // when T <: OpenAIChatCompletionService. We widen once internally instead.
  def apply[T <: OpenAIChatCompletionService](
    serviceModels: Map[T, Seq[String]],
    defaultService: OpenAIChatCompletionService
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceRouter(
      toMappedModels(widenToChat(serviceModels)),
      defaultService
    )

  def applyMapped[T <: OpenAIChatCompletionService](
    serviceModels: Map[T, Seq[MappedModel]],
    defaultService: OpenAIChatCompletionService
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceRouter(widenToChat(serviceModels), defaultService)

  /**
   * Batch-aware router: routes chat completion '''and''' the provider-agnostic batch endpoints
   * by model. The registered services and the default must all be batch-capable, so the
   * returned service is too - see [[BatchChatService]]. All registered services need to be
   * batch-capable up front; for a map mixing batch-capable and plain chat-completion services,
   * use [[applyWithBatchMixed]]/[[applyWithBatchMixedMapped]] instead.
   */
  def applyWithBatch[T <: BatchChatService](
    serviceModels: Map[T, Seq[String]],
    defaultService: BatchChatService
  ): BatchChatService =
    new OpenAIChatCompletionBatchServiceRouter(
      toMappedModels(widenToBatch(serviceModels)),
      defaultService
    )

  def applyWithBatchMapped[T <: BatchChatService](
    serviceModels: Map[T, Seq[MappedModel]],
    defaultService: BatchChatService
  ): BatchChatService =
    new OpenAIChatCompletionBatchServiceRouter(widenToBatch(serviceModels), defaultService)

  /**
   * Mixed-capability variant of [[applyWithBatch]] for maps that combine batch-capable and
   * plain (non-batch) chat-completion services under a single key type. Chat completion routes
   * to '''all''' registered services by model, exactly like [[apply]]. Batch calls route to a
   * registered service only if it is actually batch-capable (checked at runtime, since the
   * static key type does not guarantee it); a model mapped to a non-batch service fails fast
   * with a helpful [[io.cequence.openaiscala.OpenAIScalaClientException]] instead of blindly
   * falling back to the default (which would silently send a foreign model id to the wrong
   * provider) or crashing with an erasure-related `IncompatibleClassChangeError`. The default
   * service is statically batch-capable, so batch calls that fall through to it need no check.
   */
  def applyWithBatchMixed[T <: OpenAIChatCompletionService](
    serviceModels: Map[T, Seq[String]],
    defaultService: BatchChatService
  ): BatchChatService =
    new OpenAIChatCompletionMixedBatchServiceRouter(
      toMappedModels(widenToChat(serviceModels)),
      defaultService
    )

  def applyWithBatchMixedMapped[T <: OpenAIChatCompletionService](
    serviceModels: Map[T, Seq[MappedModel]],
    defaultService: BatchChatService
  ): BatchChatService =
    new OpenAIChatCompletionMixedBatchServiceRouter(widenToChat(serviceModels), defaultService)

  private def toMappedModels[S](
    serviceModels: Map[S, Seq[String]]
  ): Map[S, Seq[MappedModel]] =
    serviceModels.map { case (service, models) =>
      service -> models.map(model => MappedModel(model, model))
    }

  private def widenToChat[T <: OpenAIChatCompletionService, V](
    serviceModels: Map[T, V]
  ): Map[OpenAIChatCompletionService, V] =
    serviceModels.map { case (service, v) => (service: OpenAIChatCompletionService) -> v }

  private def widenToBatch[T <: BatchChatService, V](
    serviceModels: Map[T, V]
  ): Map[BatchChatService, V] =
    serviceModels.map { case (service, v) => (service: BatchChatService) -> v }

  private abstract class RouterBase[S <: OpenAIChatCompletionService](
    serviceModels: Map[S, Seq[MappedModel]],
    defaultService: S
  ) extends OpenAIChatCompletionService {

    protected val modelServiceMap: Map[String, (S, String)] =
      serviceModels.flatMap { case (service, mappedModels) =>
        mappedModels.map { case MappedModel(modelToMatch, modelToUse) =>
          modelToMatch -> (service, modelToUse)
        }
      }

    protected val default: S = defaultService

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] =
      modelServiceMap.get(settings.model) match {
        case Some((modelService, modelToUse)) =>
          modelService.createChatCompletion(messages, settings.copy(model = modelToUse))

        case None =>
          default.createChatCompletion(messages, settings)
      }

    override def createChatToolCompletion(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool],
      responseToolChoice: Option[String],
      settings: CreateChatCompletionSettings
    ): Future[ChatToolCompletionResponse] =
      modelServiceMap.get(settings.model) match {
        case Some((modelService, modelToUse)) =>
          modelService.createChatToolCompletion(
            messages,
            tools,
            responseToolChoice,
            settings.copy(model = modelToUse)
          )

        case None =>
          default.createChatToolCompletion(messages, tools, responseToolChoice, settings)
      }

    // closes only the registered services (deduplicated) - not the default, see the note on
    // the factory methods below
    override def close(): Unit =
      modelServiceMap.values.map(_._1).toSet.foreach((service: S) => service.close())
  }

  private final class OpenAIChatCompletionServiceRouter(
    serviceModels: Map[OpenAIChatCompletionService, Seq[MappedModel]],
    defaultService: OpenAIChatCompletionService
  ) extends RouterBase[OpenAIChatCompletionService](serviceModels, defaultService)

  private final class OpenAIChatCompletionBatchServiceRouter(
    serviceModels: Map[BatchChatService, Seq[MappedModel]],
    defaultService: BatchChatService
  ) extends RouterBase[BatchChatService](serviceModels, defaultService)
      with OpenAIChatCompletionBatchService {

    override def createChatCompletionBatch(
      requests: Seq[ChatCompletionBatchRequest],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionBatchInfo] =
      modelServiceMap.get(settings.model) match {
        case Some((modelService, modelToUse)) =>
          modelService.createChatCompletionBatch(requests, settings.copy(model = modelToUse))

        case None =>
          default.createChatCompletionBatch(requests, settings)
      }

    // batch status/results/cancel/delete calls carry no settings - just a batch id - so `model`
    // is passed alongside it explicitly, and dispatched exactly like the calls above: a model
    // match picks the right registered service, deterministically, with no trial and error.
    override def getChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[ChatCompletionBatchInfo] =
      modelServiceMap.get(model) match {
        case Some((modelService, modelToUse)) =>
          modelService.getChatCompletionBatch(batchId, modelToUse)

        case None =>
          default.getChatCompletionBatch(batchId, model)
      }

    override def retrieveChatCompletionBatchResults(
      batchId: String,
      model: String
    ): Future[Seq[ChatCompletionBatchResultItem]] =
      modelServiceMap.get(model) match {
        case Some((modelService, modelToUse)) =>
          modelService.retrieveChatCompletionBatchResults(batchId, modelToUse)

        case None =>
          default.retrieveChatCompletionBatchResults(batchId, model)
      }

    override def cancelChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[ChatCompletionBatchInfo] =
      modelServiceMap.get(model) match {
        case Some((modelService, modelToUse)) =>
          modelService.cancelChatCompletionBatch(batchId, modelToUse)

        case None =>
          default.cancelChatCompletionBatch(batchId, model)
      }

    override def deleteChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[Unit] =
      modelServiceMap.get(model) match {
        case Some((modelService, modelToUse)) =>
          modelService.deleteChatCompletionBatch(batchId, modelToUse)

        case None =>
          default.deleteChatCompletionBatch(batchId, model)
      }
  }

  // mixed-capability variant of OpenAIChatCompletionBatchServiceRouter above: the map key type
  // is only OpenAIChatCompletionService (not BatchChatService), so a registered service is not
  // guaranteed to be batch-capable and each batch call is runtime-checked via pattern matching
  // instead - see applyWithBatchMixed/applyWithBatchMixedMapped scaladoc for the rationale.
  private final class OpenAIChatCompletionMixedBatchServiceRouter(
    serviceModels: Map[OpenAIChatCompletionService, Seq[MappedModel]],
    defaultBatchService: BatchChatService
  ) extends RouterBase[OpenAIChatCompletionService](serviceModels, defaultBatchService)
      with OpenAIChatCompletionBatchService {

    private def notBatchCapable(model: String): Future[Nothing] =
      Future.failed(
        new OpenAIScalaClientException(
          "Chat-completion batch is not supported by the service registered for model " +
            s"'$model' - register a batch-capable service for it, or wrap it with " +
            "OpenAIServiceAdapters.chatCompletionBatchEmulated."
        )
      )

    override def createChatCompletionBatch(
      requests: Seq[ChatCompletionBatchRequest],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionBatchInfo] =
      modelServiceMap.get(settings.model) match {
        case Some((modelService: OpenAIChatCompletionBatchService, modelToUse)) =>
          modelService.createChatCompletionBatch(requests, settings.copy(model = modelToUse))

        case Some(_) =>
          notBatchCapable(settings.model)

        case None =>
          defaultBatchService.createChatCompletionBatch(requests, settings)
      }

    override def getChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[ChatCompletionBatchInfo] =
      modelServiceMap.get(model) match {
        case Some((modelService: OpenAIChatCompletionBatchService, modelToUse)) =>
          modelService.getChatCompletionBatch(batchId, modelToUse)

        case Some(_) =>
          notBatchCapable(model)

        case None =>
          defaultBatchService.getChatCompletionBatch(batchId, model)
      }

    override def retrieveChatCompletionBatchResults(
      batchId: String,
      model: String
    ): Future[Seq[ChatCompletionBatchResultItem]] =
      modelServiceMap.get(model) match {
        case Some((modelService: OpenAIChatCompletionBatchService, modelToUse)) =>
          modelService.retrieveChatCompletionBatchResults(batchId, modelToUse)

        case Some(_) =>
          notBatchCapable(model)

        case None =>
          defaultBatchService.retrieveChatCompletionBatchResults(batchId, model)
      }

    override def cancelChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[ChatCompletionBatchInfo] =
      modelServiceMap.get(model) match {
        case Some((modelService: OpenAIChatCompletionBatchService, modelToUse)) =>
          modelService.cancelChatCompletionBatch(batchId, modelToUse)

        case Some(_) =>
          notBatchCapable(model)

        case None =>
          defaultBatchService.cancelChatCompletionBatch(batchId, model)
      }

    override def deleteChatCompletionBatch(
      batchId: String,
      model: String
    ): Future[Unit] =
      modelServiceMap.get(model) match {
        case Some((modelService: OpenAIChatCompletionBatchService, modelToUse)) =>
          modelService.deleteChatCompletionBatch(batchId, modelToUse)

        case Some(_) =>
          notBatchCapable(model)

        case None =>
          defaultBatchService.deleteChatCompletionBatch(batchId, model)
      }
  }
}

case class MappedModel(
  modelToMatch: String,
  modelToUse: String
)
