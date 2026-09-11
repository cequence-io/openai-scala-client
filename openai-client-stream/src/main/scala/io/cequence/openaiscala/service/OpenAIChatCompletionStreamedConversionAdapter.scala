package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.ChatCompletionTool
import io.cequence.openaiscala.domain.response.ChatChunk

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.domain.BaseMessage
import io.cequence.openaiscala.domain.response.ChatCompletionChunkResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

object OpenAIChatCompletionStreamedConversionAdapter {
  def apply(
    service: OpenAIChatCompletionStreamedServiceExtra,
    messagesConversion: Seq[BaseMessage] => Seq[BaseMessage],
    settingsConversion: CreateChatCompletionSettings => CreateChatCompletionSettings
  ): OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIChatCompletionStreamedConversionAdapterImpl(
      service,
      messagesConversion,
      settingsConversion
    )

  final private class OpenAIChatCompletionStreamedConversionAdapterImpl(
    underlying: OpenAIChatCompletionStreamedServiceExtra,
    messagesConversion: Seq[BaseMessage] => Seq[BaseMessage],
    settingsConversion: CreateChatCompletionSettings => CreateChatCompletionSettings
  ) extends OpenAIChatCompletionStreamedServiceExtra {

    override def createChatCompletionStreamed(
      messages: Seq[BaseMessage],
      settings: CreateChatCompletionSettings
    ): Source[ChatCompletionChunkResponse, NotUsed] =
      underlying.createChatCompletionStreamed(
        messagesConversion(messages),
        settingsConversion(settings)
      )

    override def createChatToolCompletionStreamed(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool],
      responseToolChoice: Option[String],
      settings: CreateChatCompletionSettings
    ): Source[ChatChunk, NotUsed] =
      underlying.createChatToolCompletionStreamed(
        messagesConversion(messages),
        tools,
        responseToolChoice,
        settingsConversion(settings)
      )

    override def close(): Unit =
      underlying.close()
  }
}
