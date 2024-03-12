package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.TextBlock
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlocks
import io.cequence.openaiscala.anthropic.domain.{Content, Message}
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
  SystemMessage,
  BaseMessage => OpenAIBaseMessage,
  Content => OpenAIContent,
  ImageURLContent => OpenAIImageContent,
  TextContent => OpenAITextContent,
  UserMessage => OpenAIUserMessage,
  UserSeqMessage => OpenAIUserSeqMessage
}

import java.{util => ju}

package object impl extends AnthropicServiceConsts {

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
      case OpenAIImageContent(url) =>
        // TODO: convert OpenAI image content to Anthropic image content
        throw new IllegalArgumentException(s"Image content not supported: $url")
    }
  }

  def toAnthropic(
    settings: CreateChatCompletionSettings,
    messages: Seq[OpenAIBaseMessage]
  ): AnthropicCreateMessageSettings = {
    def systemMessagesContent = messages.collect { case SystemMessage(content, _) =>
      content
    }.mkString("\n")

    AnthropicCreateMessageSettings(
      model = settings.model,
      system = if (systemMessagesContent.isEmpty) None else Some(systemMessagesContent),
      max_tokens = settings.max_tokens.getOrElse(defaultMaxTokens),
      metadata = Map.empty,
      stop_sequences = settings.stop,
      temperature = settings.temperature,
      top_p = settings.top_p,
      top_k = None
    )
  }

  def toOpenAI(response: CreateMessageResponse): ChatCompletionResponse =
    ChatCompletionResponse(
      id = response.id,
      created = new ju.Date(),
      model = response.model,
      system_fingerprint = response.stop_reason,
      choices = Seq(
        ChatCompletionChoiceInfo(
          message = toOpenAIAssistantMessage(response.content),
          index = 0,
          finish_reason = response.stop_reason,
          logprobs = None
        )
      ),
      usage = Some(toOpenAI(response.usage))
    )

  def toOpenAIAssistantMessage(content: ContentBlocks): AssistantMessage = {
    val textContents = content.blocks.collect { case TextBlock(text) => text }
    // TODO: log if there is more than one text content
    if (textContents.size == 0) {
      throw new IllegalArgumentException("No text content found in the response")
    }
    val singleTextContent = concatenateMessages(textContents)
    AssistantMessage(singleTextContent, name = None)
  }

  private def concatenateMessages(messageContent: Seq[String]): String =
    messageContent.mkString("\n")

  def toOpenAI(usageInfo: UsageInfo): OpenAIUsageInfo = {
    OpenAIUsageInfo(
      prompt_tokens = usageInfo.input_tokens,
      total_tokens = usageInfo.input_tokens + usageInfo.output_tokens,
      completion_tokens = Some(usageInfo.output_tokens)
    )
  }

}
