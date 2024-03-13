package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionChunkResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

object OpenAIChatCompletionStreamedServiceRouter {
  def apply(
    serviceModels: Map[OpenAIChatCompletionStreamedServiceExtra, Seq[String]],
    defaultService: OpenAIChatCompletionStreamedServiceExtra
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedServiceRouter(serviceModels, defaultService)

  final private class OpenAIChatCompletionStreamedServiceRouter(
    serviceModels: Map[OpenAIChatCompletionStreamedServiceExtra, Seq[String]],
    defaultService: OpenAIChatCompletionStreamedServiceExtra
  ) extends OpenAIChatCompletionStreamedServiceExtra {

    private val modelServiceMap = serviceModels.flatMap { case (service, models) =>
      models.map(_ -> service)
    }

    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
    ): Source[ChatCompletionChunkResponse, NotUsed] =
      modelServiceMap.get(settings.model) match {
        case Some(modelService) =>
          modelService.createChatCompletionStreamed(messages, settings)

        case None =>
          defaultService.createChatCompletionStreamed(messages, settings)
      }

    override def close(): Unit =
      modelServiceMap.values.foreach(_.close())
  }
}
