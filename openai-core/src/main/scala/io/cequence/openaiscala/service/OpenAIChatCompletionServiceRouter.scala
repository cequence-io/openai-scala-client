package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import scala.concurrent.Future

private class OpenAIChatCompletionServiceRouter(
  serviceModels: Map[OpenAIChatCompletionService, Seq[String]],
  defaultService: OpenAIChatCompletionService
) extends OpenAIChatCompletionService {

  private val modelServiceMap = serviceModels.flatMap { case (service, models) =>
    models.map(_ -> service)
  }

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    modelServiceMap.get(settings.model) match {
      case Some(modelService) =>
        modelService.createChatCompletion(messages, settings)

      case None =>
        defaultService.createChatCompletion(messages, settings)
    }

  def close =
    modelServiceMap.values.foreach(_.close)
}

object OpenAIChatCompletionServiceRouter {
  def apply(
    serviceModels: Map[OpenAIChatCompletionService, Seq[String]],
    defaultService: OpenAIChatCompletionService
  ): OpenAIChatCompletionService =
    new OpenAIChatCompletionServiceRouter(serviceModels, defaultService)
}
