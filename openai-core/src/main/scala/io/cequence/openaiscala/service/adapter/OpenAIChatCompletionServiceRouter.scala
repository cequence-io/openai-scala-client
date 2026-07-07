package io.cequence.openaiscala.service.adapter

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
  def apply(
    serviceModels: Map[OpenAIChatCompletionService, Seq[String]],
    defaultService: OpenAIChatCompletionService
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceRouter(toMappedModels(serviceModels), defaultService)

  def applyMapped(
    serviceModels: Map[OpenAIChatCompletionService, Seq[MappedModel]],
    defaultService: OpenAIChatCompletionService
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceRouter(serviceModels, defaultService)

  /**
   * Batch-aware router: routes chat completion '''and''' the provider-agnostic batch endpoints
   * by model. The registered services and the default must all be batch-capable, so the
   * returned service is too - see [[BatchChatService]].
   */
  def applyWithBatch(
    serviceModels: Map[BatchChatService, Seq[String]],
    defaultService: BatchChatService
  ): BatchChatService =
    new OpenAIChatCompletionBatchServiceRouter(toMappedModels(serviceModels), defaultService)

  def applyWithBatchMapped(
    serviceModels: Map[BatchChatService, Seq[MappedModel]],
    defaultService: BatchChatService
  ): BatchChatService =
    new OpenAIChatCompletionBatchServiceRouter(serviceModels, defaultService)

  private def toMappedModels[S](
    serviceModels: Map[S, Seq[String]]
  ): Map[S, Seq[MappedModel]] =
    serviceModels.map { case (service, models) =>
      service -> models.map(model => MappedModel(model, model))
    }

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
}

case class MappedModel(
  modelToMatch: String,
  modelToUse: String
)
