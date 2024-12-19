package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

case class ChatCompletionInterceptData(
  messages: Seq[BaseMessage],
  setting: CreateChatCompletionSettings,
  response: ChatCompletionResponse,
  timeRequestReceived: java.util.Date,
  timeResponseReceived: java.util.Date
)
