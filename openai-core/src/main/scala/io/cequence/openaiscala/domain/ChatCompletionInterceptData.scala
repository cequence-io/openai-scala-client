package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.response.ChatCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

case class ChatCompletionInterceptData(
  messages: Seq[BaseMessage],
  settings: CreateChatCompletionSettings,
  response: ChatCompletionResponse,
  timeRequestSent: java.util.Date,
  timeResponseReceived: java.util.Date
) {

  /**
   * @return
   *   the duration of the request in seconds
   */
  def duration: Double = (timeResponseReceived.getTime - timeRequestSent.getTime) / 1000.0
}
