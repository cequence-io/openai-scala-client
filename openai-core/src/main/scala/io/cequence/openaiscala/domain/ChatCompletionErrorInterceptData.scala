package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

case class ChatCompletionErrorInterceptData(
  messages: Seq[BaseMessage],
  settings: CreateChatCompletionSettings,
  error: Throwable,
  timeRequestSent: java.util.Date,
  timeErrorReceived: java.util.Date
) {
  def execTimeMs: Long = timeErrorReceived.getTime - timeRequestSent.getTime
}
