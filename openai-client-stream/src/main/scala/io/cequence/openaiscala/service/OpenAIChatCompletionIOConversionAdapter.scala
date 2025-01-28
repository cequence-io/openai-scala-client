package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.cequence.openaiscala.domain.response.ChunkMessageSpec
import io.cequence.openaiscala.domain.{AssistantMessage, BaseMessage}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.OpenAIStreamedServiceImplicits.ChatCompletionStreamExt
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters

import scala.concurrent.ExecutionContext

object OpenAIChatCompletionIOConversionAdapter {

  private val chatCompletionAdapters = OpenAIServiceAdapters.forChatCompletionService

  protected type Conversion[T] = Option[T => T]
  protected type FlowConversion[T] = Option[Flow[T, T, NotUsed]]

  def apply(
    service: OpenAIChatCompletionStreamedService,
    inputMessagesConversion: Conversion[Seq[BaseMessage]] = None,
    inputSettingsConversion: Conversion[CreateChatCompletionSettings] = None,
    outputMessageConversion: Conversion[AssistantMessage] = None,
    outputChunkMessageConversion: FlowConversion[Seq[ChunkMessageSpec]] = None
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionStreamedService = {
    val inputMessagesConversionFinal =
      inputMessagesConversion.getOrElse(identity[Seq[BaseMessage]] _)
    val inputSettingsConversionFinal =
      inputSettingsConversion.getOrElse(identity[CreateChatCompletionSettings] _)

    // input conversion
    val nonStreamedServiceAux =
      if (inputMessagesConversion.isDefined || inputSettingsConversion.isDefined) {
        chatCompletionAdapters.chatCompletionInput(
          inputMessagesConversionFinal,
          inputSettingsConversionFinal
        )(service)
      } else
        service

    val streamedServiceAux =
      if (inputMessagesConversion.isDefined || inputSettingsConversion.isDefined) {
        OpenAIChatCompletionStreamedConversionAdapter(
          service,
          inputMessagesConversionFinal,
          inputSettingsConversionFinal
        )
      } else
        service

    // output conversion
    val nonStreamedService = outputMessageConversion.map {
      chatCompletionAdapters.chatCompletionOutput(_)(nonStreamedServiceAux)
    }.getOrElse(nonStreamedServiceAux)

    val streamedService = outputChunkMessageConversion.map {
      OpenAIChatCompletionStreamedOutputConversionAdapter(
        streamedServiceAux,
        _
      )
    }.getOrElse(streamedServiceAux)

    nonStreamedService.withStreaming(streamedService)
  }
}
