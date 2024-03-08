package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.TextBlock
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlocks
import io.cequence.openaiscala.anthropic.domain.{Message, Content}
import io.cequence.openaiscala.anthropic.service.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.service.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  BaseMessage => OpenAIBaseMessage,
  Content => OpenAIContent,
  TextContent => OpenAITextContent,
  UserMessage => OpenAIUserMessage,
  UserSeqMessage => OpenAIUserSeqMessage
}
import org.joda.time.DateTime

import java.{util => ju}

package object impl {

  // TODO: move to consts? determine the default value
  val defaultMaxTokens = 2048

  def toAnthropic(baseMessage: OpenAIBaseMessage): Message = {
    baseMessage match {
      case OpenAIUserMessage(content, _) => Message.UserMessage(content)
      case OpenAIUserSeqMessage(contents, _) =>
        Message.UserMessageContent(contents.map(toAnthropic))
    }
  }

  def toAnthropic(content: OpenAIContent): Content.ContentBlock = {
    content match {
      case OpenAITextContent(text) => TextBlock(text)
      // TODO: handle other content types
    }
  }

  def toAnthropic(settings: CreateChatCompletionSettings): AnthropicCreateMessageSettings = {
    AnthropicCreateMessageSettings(
      model = settings.model,
      // TODO: shall I filter system messages from OpenAI base messages to find out the system prompt? concatenate them if there are more of them?
      system = ???,
      max_tokens = settings.max_tokens.getOrElse(defaultMaxTokens),
      metadata = Map.empty,
      stop_sequences = settings.stop,
      temperature = settings.temperature,
      top_p = settings.top_p,
      // TODO: is there an equivalent for top_k in OpenAI?
      top_k = None
    )
  }

  def toOpenAI(response: CreateMessageResponse): ChatCompletionResponse =
    ChatCompletionResponse(
      id = response.id,
      created = new ju.Date(DateTime.now.getMillis),
      model = response.model,
      system_fingerprint = response.stop_reason,
      // TODO: check, is this the right way to convert the content?
      choices = Seq(
        ChatCompletionChoiceInfo(
          message = toOpenAIAssistantMessage(response.content),
          // TODO: what is the index?
          index = ???,
          finish_reason = response.stop_reason,
          // TODO: are there any logprobs in Anthropic response?
          logprobs = None
        )
      ),
      usage = Some(toOpenAI(response.usage))
    )

  def toOpenAIAssistantMessage(content: ContentBlocks): AssistantMessage = {
    // TODO: is each content a separate choice or can I concatenate them?
    val text = content.blocks.map { case TextBlock(text) =>
      text
    }.mkString("\n")
    AssistantMessage(text, name = None)
  }

  def toOpenAI(usageInfo: UsageInfo): OpenAIUsageInfo = {
    OpenAIUsageInfo(
      prompt_tokens = usageInfo.input_tokens,
      total_tokens = usageInfo.output_tokens,
      // TODO: how to determine completion tokens?
      completion_tokens = None
    )
  }

}
