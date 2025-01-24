package io.cequence.openaiscala.perplexity.domain.response

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response.{ChatCompletionChoiceInfo, UsageInfo}

import java.{util => ju}

case class SonarChatCompletionResponse(
  id: String,
  created: ju.Date,
  model: String,
  citations: Seq[String],
  choices: Seq[ChatCompletionChoiceInfo],
  usage: Option[UsageInfo]
) {
  def contentHead: String = choices.headOption
    .map(_.message.content)
    .getOrElse(
      throw new OpenAIScalaClientException(
        s"No content in the chat completion response ${id}."
      )
    )
}
