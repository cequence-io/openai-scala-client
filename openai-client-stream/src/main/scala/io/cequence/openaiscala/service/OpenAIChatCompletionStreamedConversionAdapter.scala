package io.cequence.openaiscala.service

import io.cequence.wsclient.stream.AsyncStream
import io.cequence.openaiscala.domain.ChatCompletionTool
import io.cequence.openaiscala.domain.response.ChatChunk

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
    ): AsyncStream[ChatCompletionChunkResponse] =
      underlying.createChatCompletionStreamed(
        messagesConversion(messages),
        settingsConversion(settings)
      )

    override def createChatToolCompletionStreamed(
      messages: Seq[BaseMessage],
      tools: Seq[ChatCompletionTool],
      responseToolChoice: Option[String],
      settings: CreateChatCompletionSettings
    ): AsyncStream[ChatChunk] =
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
