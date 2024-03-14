package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionChunkResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.adapter.MappedModel

object OpenAIChatCompletionStreamedServiceRouter {
  def apply(
    serviceModels: Map[OpenAIChatCompletionStreamedServiceExtra, Seq[String]],
    defaultService: OpenAIChatCompletionStreamedServiceExtra
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedServiceRouter(
      serviceModels.map { case (service, models) =>
        service -> models.map(model => MappedModel(model, model))
      },
      defaultService
    )
  def applyMapped(
    serviceModels: Map[OpenAIChatCompletionStreamedServiceExtra, Seq[MappedModel]],
    defaultService: OpenAIChatCompletionStreamedServiceExtra
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedServiceRouter(serviceModels, defaultService)

  final private class OpenAIChatCompletionStreamedServiceRouter(
    serviceModels: Map[OpenAIChatCompletionStreamedServiceExtra, Seq[MappedModel]],
    defaultService: OpenAIChatCompletionStreamedServiceExtra
  ) extends OpenAIChatCompletionStreamedServiceExtra {

    private val modelServiceMap = serviceModels.flatMap { case (service, mappedModels) =>
      mappedModels.map { case MappedModel(modelToMatch, modelToUse) =>
        modelToMatch -> (service, modelToUse)
      }
    }

    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Source[ChatCompletionChunkResponse, NotUsed] =
      modelServiceMap.get(settings.model) match {
        case Some((modelService, modelToUse)) =>
          modelService.createChatCompletionStreamed(
            messages,
            settings.copy(model = modelToUse)
          )

        case None =>
          defaultService.createChatCompletionStreamed(messages, settings)
      }

    override def close(): Unit =
      modelServiceMap.values.foreach(_._1.close())
  }
}
