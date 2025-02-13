package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.CacheControl.Ephemeral
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.TextBlock
import io.cequence.openaiscala.anthropic.domain.Content.{ContentBlockBase, ContentBlocks}
import io.cequence.openaiscala.anthropic.domain.Message.SystemMessageContent
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.AnthropicCreateMessageSettings
import io.cequence.openaiscala.anthropic.domain.{CacheControl, Content, Message}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChunkMessageSpec,
  PromptTokensDetails,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps.RichCreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  ChatRole,
  MessageSpec,
  SystemMessage,
  AssistantMessage => OpenAIAssistantMessage,
  BaseMessage => OpenAIBaseMessage,
  Content => OpenAIContent,
  ImageURLContent => OpenAIImageContent,
  TextContent => OpenAITextContent,
  UserMessage => OpenAIUserMessage,
  UserSeqMessage => OpenAIUserSeqMessage
}

import java.{util => ju}

package object impl extends AnthropicServiceConsts {

  def toAnthropicSystemMessages(
    messages: Seq[OpenAIBaseMessage],
    settings: CreateChatCompletionSettings
  ): Seq[Message] = {
    assert(
      messages.forall(_.isSystem),
      "All messages must be system messages"
    )

    val useSystemCache: Option[CacheControl] =
      if (settings.useAnthropicSystemMessagesCache) Some(Ephemeral) else None

    val messageStrings =
      messages.zipWithIndex.collect { case (SystemMessage(content, _), index) =>
        useSystemCache match {
          case Some(cacheControl) =>
            if (index == messages.size - 1)
              ContentBlockBase(TextBlock(content), Some(cacheControl))
            else ContentBlockBase(TextBlock(content), None)

          case None => ContentBlockBase(TextBlock(content))
        }
      }

    if (messageStrings.isEmpty)
      Seq.empty
    else
      Seq(SystemMessageContent(messageStrings))
  }

  def toAnthropicMessages(
    messages: Seq[OpenAIBaseMessage],
    settings: CreateChatCompletionSettings
  ): Seq[Message] = {

    val anthropicMessages: Seq[Message] = messages.collect {
      case OpenAIUserMessage(content, _) => Message.UserMessage(content)

      case OpenAIUserSeqMessage(contents, _) =>
        Message.UserMessageContent(contents.map(toAnthropic))

      case OpenAIAssistantMessage(content, _) => Message.AssistantMessage(content)

      // legacy message type
      case MessageSpec(role, content, _) if role == ChatRole.User =>
        Message.UserMessage(content)
    }

    // apply cache control to user messages
    // crawl through anthropicMessages, and apply to the first N user messages cache control, where N = countUserMessagesToCache
    val countUserMessagesToCache = settings.anthropicCachedUserMessagesCount

    val anthropicMessagesWithCache: Seq[Message] = anthropicMessages
      .foldLeft((List.empty[Message], countUserMessagesToCache)) {
        case ((acc, userMessagesToCacheCount), message) =>
          message match {
            case Message.UserMessage(contentString, _) =>
              val newCacheControl = if (userMessagesToCacheCount > 0) Some(Ephemeral) else None
              (
                acc :+ Message.UserMessage(contentString, newCacheControl),
                userMessagesToCacheCount - newCacheControl.map(_ => 1).getOrElse(0)
              )

            case Message.UserMessageContent(contentBlocks) =>
              val (newContentBlocks, remainingCache) =
                contentBlocks.foldLeft(
                  (Seq.empty[ContentBlockBase], userMessagesToCacheCount)
                ) { case ((acc, cacheLeft), content) =>
                  val cacheControl = if (cacheLeft > 0) Some(Ephemeral) else None
                  val newCacheLeft = cacheLeft - cacheControl.map(_ => 1).getOrElse(0)
                  val block = content.copy(cacheControl = cacheControl)
                  (acc :+ block, newCacheLeft)
                }
              (acc :+ Message.UserMessageContent(newContentBlocks), remainingCache)

            case assistant: Message.AssistantMessage =>
              (acc :+ assistant, userMessagesToCacheCount)

            case assistants: Message.AssistantMessageContent =>
              (acc :+ assistants, userMessagesToCacheCount)
          }
      }
      ._1
    anthropicMessagesWithCache
  }

  def toAnthropic(content: OpenAIContent): Content.ContentBlockBase = {
    content match {
      case OpenAITextContent(text) =>
        ContentBlockBase(TextBlock(text))

      case OpenAIImageContent(url) =>
        if (url.startsWith("data:")) {
          val mediaTypeEncodingAndData = url.drop(5)
          val mediaType = mediaTypeEncodingAndData.takeWhile(_ != ';')
          val encodingAndData = mediaTypeEncodingAndData.drop(mediaType.length + 1)
          val encoding = encodingAndData.takeWhile(_ != ',')
          val data = encodingAndData.drop(encoding.length + 1)

          val `type` = if (mediaType.startsWith("image/")) "image" else "document"

          ContentBlockBase(
            Content.ContentBlock.MediaBlock(`type`, encoding, mediaType, data)
          )
        } else {
          throw new IllegalArgumentException(
            "Image content only supported by providing image data directly."
          )
        }
    }
  }

  def toAnthropicSettings(
    settings: CreateChatCompletionSettings
  ): AnthropicCreateMessageSettings =
    AnthropicCreateMessageSettings(
      model = settings.model,
      max_tokens = settings.max_tokens.getOrElse(DefaultSettings.CreateMessage.max_tokens),
      metadata = Map.empty,
      stop_sequences = settings.stop,
      temperature = settings.temperature,
      top_p = settings.top_p,
      top_k = None
    )

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

  def toOpenAIAssistantMessage(content: ContentBlocks): OpenAIAssistantMessage = {
    val textContents = content.blocks.collect { case ContentBlockBase(TextBlock(text, _), _) =>
      text
    } // TODO
    // TODO: log if there is more than one text content
    if (textContents.isEmpty) {
      throw new IllegalArgumentException("No text content found in the response")
    }
    val singleTextContent = concatenateMessages(textContents)
    OpenAIAssistantMessage(singleTextContent, name = None)
  }

  private def concatenateMessages(messageContent: Seq[String]): String =
    messageContent.mkString("\n")

  def toOpenAI(usageInfo: UsageInfo): OpenAIUsageInfo = {
    val promptTokens =
      usageInfo.input_tokens + usageInfo.cache_creation_input_tokens + usageInfo.cache_read_input_tokens

    OpenAIUsageInfo(
      prompt_tokens = promptTokens,
      completion_tokens = Some(usageInfo.output_tokens),
      total_tokens = promptTokens + usageInfo.output_tokens,
      prompt_tokens_details = Some(
        PromptTokensDetails(
          cached_tokens = usageInfo.cache_read_input_tokens,
          audio_tokens = 0
        )
      ),
      completion_tokens_details = None
    )
  }
}
