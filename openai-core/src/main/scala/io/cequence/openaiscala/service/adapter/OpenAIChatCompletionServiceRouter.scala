package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIChatCompletionService

import scala.concurrent.Future

object OpenAIChatCompletionServiceRouter {
  def apply(
    serviceModels: Map[OpenAIChatCompletionService, Seq[String]],
    defaultService: OpenAIChatCompletionService
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceRouter(
      serviceModels.map { case (service, models) =>
        service -> models.map(model => MappedModel(model, model))
      },
      defaultService
    )

  def applyMapped(
    serviceModels: Map[OpenAIChatCompletionService, Seq[MappedModel]],
    defaultService: OpenAIChatCompletionService
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceRouter(serviceModels, defaultService)

  private final class OpenAIChatCompletionServiceRouter(
    serviceModels: Map[OpenAIChatCompletionService, Seq[MappedModel]],
    defaultService: OpenAIChatCompletionService
  ) extends OpenAIChatCompletionService {

    private val modelServiceMap = serviceModels.flatMap { case (service, mappedModels) =>
      mappedModels.map { case MappedModel(modelToMatch, modelToUse) =>
        modelToMatch -> (service, modelToUse)
      }
    }

    override def createChatCompletion(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] =
      modelServiceMap.get(settings.model) match {
        case Some((modelService, modelToUse)) =>
          modelService.createChatCompletion(messages, settings.copy(model = modelToUse))

        case None =>
          defaultService.createChatCompletion(messages, settings)
      }

    override def createJsonChatCompletion(
      messages: Seq[BaseMessage],
      jsonSchema: Map[String, Any],
      settings: CreateChatCompletionSettings
    ): Future[ChatCompletionResponse] =
      modelServiceMap.get(settings.model) match {
        case Some((modelService, modelToUse)) =>
          modelService.createJsonChatCompletion(
            messages,
            jsonSchema,
            settings.copy(model = modelToUse)
          )

        case None =>
          defaultService.createJsonChatCompletion(messages, jsonSchema, settings)
      }

    def close(): Unit =
      modelServiceMap.values.foreach(_._1.close())
  }
}

case class MappedModel(
  modelToMatch: String,
  modelToUse: String
)
