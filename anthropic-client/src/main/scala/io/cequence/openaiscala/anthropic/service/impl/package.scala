package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.JsonUtil
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{TextBlock, ToolUseBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlocks
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.{Content, Message, ToolSpec}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse,
  ChunkMessageSpec,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  AssistantToolMessage,
  ChatRole,
  FunctionCallSpec,
  FunctionSpec,
  MessageSpec,
  SystemMessage,
  ToolCallSpec,
  BaseMessage => OpenAIBaseMessage,
  Content => OpenAIContent,
  ImageURLContent => OpenAIImageContent,
  TextContent => OpenAITextContent,
  ToolSpec => OpenAIToolSpec,
  UserMessage => OpenAIUserMessage,
  UserSeqMessage => OpenAIUserSeqMessage
}

import java.{util => ju}

package object impl extends AnthropicServiceConsts {

  def toAnthropic(messages: Seq[OpenAIBaseMessage]): Seq[Message] = {
    messages.collect {
      case OpenAIUserMessage(content, _) =>
        Message.UserMessage(content)
      case OpenAIUserSeqMessage(contents, _) =>
        Message.UserMessageContent(contents.map(toAnthropic))
      // legacy message type
      case MessageSpec(role, content, _) if role == ChatRole.User =>
        Message.UserMessage(content)
    }
  }

  def toAnthropicSystemPrompt(messages: Seq[OpenAIBaseMessage]): Option[String] = {
    val systemMessagesContent = messages.collect { case SystemMessage(content, _) =>
      content
    }.mkString("\n")

    if (systemMessagesContent.isEmpty) None else Some(systemMessagesContent)
  }

  def toAnthropicToolUseEncouragement(toolChoice: String): UserMessage =
    UserMessage(s"Use the $toolChoice tool in your response.")

  def toAnthropicToolSpecs(toolSpecs: Seq[OpenAIToolSpec]): Seq[ToolSpec] = {
    toolSpecs.collect { case FunctionSpec(name, description, parameters) =>
      ToolSpec(name, description, parameters)
    }
  }

  def toAnthropic(content: OpenAIContent): Content.ContentBlock = {
    content match {
      case OpenAITextContent(text) => TextBlock(text)
      case OpenAIImageContent(url) =>
        if (url.startsWith("data:")) {
          val mediaTypeEncodingAndData = url.drop(5)
          val mediaType = mediaTypeEncodingAndData.takeWhile(_ != ';')
          val encodingAndData = mediaTypeEncodingAndData.drop(mediaType.length + 1)
          val encoding = mediaType.takeWhile(_ != ',')
          val data = encodingAndData.drop(encoding.length + 1)
          Content.ContentBlock.ImageBlock(encoding, mediaType, data)
        } else {
          throw new IllegalArgumentException(
            s"Image content only supported by providing image data directly."
          )
        }
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
      // system = if (systemMessagesContent.isEmpty) None else Some(systemMessagesContent),
      max_tokens = settings.max_tokens.getOrElse(DefaultSettings.CreateMessage.max_tokens),
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
      usage = response.usage.map(toOpenAI)
    )

  def toOpenAIChatToolCompletionResponse(createMessageResponse: CreateMessageResponse) = {
    ChatToolCompletionResponse(
      id = createMessageResponse.id,
      created = new ju.Date(),
      model = createMessageResponse.model,
      system_fingerprint = createMessageResponse.stop_reason,
      choices = Seq(
        ChatToolCompletionChoiceInfo(
          message = toOpenAIAssistantToolMessage(createMessageResponse.content),
          index = 0,
          finish_reason = createMessageResponse.stop_reason
        )
      ),
      usage = createMessageResponse.usage.map(toOpenAI)
    )
  }

  def toOpenAI(blockDelta: ContentBlockDelta): ChatCompletionChunkResponse =
    ChatCompletionChunkResponse(
      id = "",
      created = new ju.Date,
      model = "",
      system_fingerprint = None,
      choices = Seq(
        ChatCompletionChoiceChunkInfo(
          delta = ChunkMessageSpec(
            role = None,
            content = Some(blockDelta.delta.text)
          ),
          index = blockDelta.index,
          finish_reason = None
        )
      ),
      usage = None
    )

  def toOpenAIAssistantMessage(content: ContentBlocks): AssistantMessage = {
    val textContents = content.blocks.collect { case TextBlock(text) => text }
    // TODO: log if there is more than one text content
    if (textContents.isEmpty) {
      throw new IllegalArgumentException("No text content found in the response")
    }
    val singleTextContent = concatenateMessages(textContents)
    AssistantMessage(singleTextContent, name = None)
  }

  def toOpenAIAssistantToolMessage(content: ContentBlocks): AssistantToolMessage = {
    println(content.toString)
    val textMessage = singleTextMessage(content)
    AssistantToolMessage(
      content = Some(textMessage),
      name = None,
      tool_calls = content.blocks.collect { case toolContent: ToolUseBlock =>
        toOpenAI(toolContent)
      }
    )
  }

  def toOpenAI(toolUseBlock: ToolUseBlock): (String, ToolCallSpec) = {
    val inputJson = JsonUtil.StringAnyMapFormat.writes(toolUseBlock.input).toString()
    toolUseBlock.id -> FunctionCallSpec(toolUseBlock.id, inputJson)
  }

//  def toOpenAI(toolContent: ToolUseBlock): (String, ToolCallSpec) = {
//    toolContent match {
//      case ToolUseBlockSuccess(toolUseId, content) =>
//        toolUseId -> FunctionCallSpec(toolUseId, content)
//      case ToolUseBlockFailure(toolUseId, content) =>
//        // TODO: how to map fail case?
//        ???
//    }
//  }

  private def singleTextMessage(content: ContentBlocks): String =
    concatenateMessages(content.blocks.collect { case TextBlock(text) => text })

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
