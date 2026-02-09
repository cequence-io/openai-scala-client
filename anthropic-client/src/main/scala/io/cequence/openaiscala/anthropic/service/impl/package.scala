package io.cequence.openaiscala.anthropic.service

import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaClientTimeoutException,
  OpenAIScalaClientUnknownHostException,
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException,
  OpenAIScalaServerErrorException,
  OpenAIScalaTokenCountExceededException,
  OpenAIScalaUnauthorizedException
}
import io.cequence.openaiscala.anthropic.domain.CacheControl.Ephemeral
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.TextBlock
import io.cequence.openaiscala.anthropic.domain.Content.{ContentBlockBase, ContentBlocks}
import io.cequence.openaiscala.anthropic.domain.Message.SystemMessageContent
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.{
  ContentBlockDelta,
  CreateMessageResponse,
  DeltaBlock
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMessageSettings,
  Speed,
  ThinkingSettings
}
import io.cequence.openaiscala.anthropic.domain.{CacheControl, Content, Message, OutputFormat}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChunkMessageSpec,
  CompletionTokenDetails,
  PromptTokensDetails,
  TracedBlock,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings,
  ReasoningEffort
}
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
import io.cequence.openaiscala.service.HasOpenAIConfig
import org.slf4j.LoggerFactory

import java.{util => ju}
import scala.concurrent.Future

package object impl extends AnthropicServiceConsts with HasOpenAIConfig {

  private val logger: Logger = Logger(
    LoggerFactory.getLogger("io.cequence.openaiscala.anthropic.service.impl")
  )

  def toAnthropicSystemMessages(
    messages: Seq[OpenAIBaseMessage],
    settings: CreateChatCompletionSettings
  ): Seq[Message] = {
    assert(
      messages.forall(_.isSystem),
      "All messages must be system messages"
    )

    val useSystemCache: Option[CacheControl] =
      if (settings.useAnthropicSystemMessagesCache) Some(Ephemeral()) else None

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

      case OpenAIAssistantMessage(content, _, _) => Message.AssistantMessage(content)

      // legacy message type
      case MessageSpec(role, content, _) if role == ChatRole.User =>
        Message.UserMessage(content)

      case _ => throw new OpenAIScalaClientException("Unsupported message type")
    }

    // apply cache control to user messages
    // crawl through anthropicMessages, and apply to the first N user messages cache control, where N = countUserMessagesToCache
    val countUserMessagesToCache = settings.anthropicCachedUserMessagesCount

    val anthropicMessagesWithCache: Seq[Message] = anthropicMessages
      .foldLeft((List.empty[Message], countUserMessagesToCache)) {
        case ((acc, userMessagesToCacheCount), message) =>
          message match {
            case Message.UserMessage(contentString, _) =>
              val newCacheControl =
                if (userMessagesToCacheCount > 0) Some(Ephemeral()) else None
              (
                acc :+ Message.UserMessage(contentString, newCacheControl),
                userMessagesToCacheCount - newCacheControl.map(_ => 1).getOrElse(0)
              )

            case Message.UserMessageContent(contentBlocks) =>
              val (newContentBlocks, remainingCache) =
                contentBlocks.foldLeft(
                  (Seq.empty[ContentBlockBase], userMessagesToCacheCount)
                ) { case ((acc, cacheLeft), content) =>
                  val cacheControl = if (cacheLeft > 0) Some(Ephemeral()) else None
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

  /**
   * Converts OpenAI's reasoning_effort to Anthropic's thinking budget using the configured
   * mapping.
   *
   * @param reasoningEffort
   *   The reasoning effort level from OpenAI settings
   * @return
   *   Thinking budget in tokens, or None if reasoning_effort is None or budget is 0
   */
  private def toThinkingBudget(
    reasoningEffort: Option[ReasoningEffort]
  ): Option[Int] = {
    import io.cequence.wsclient.ConfigImplicits._

    reasoningEffort.flatMap { effort =>
      val effortKey = effort.toString.toLowerCase
      val configPath =
        s"$configPrefix.reasoning-effort-thinking-budget-mapping.$effortKey.anthropic"

      clientConfig.optionalInt(configPath) match {
        case Some(budget) =>
          logger.debug(
            s"Converting reasoning effort '$effortKey' to Anthropic thinking budget: $budget"
          )

          if (budget == 0) {
            // budget = 0 means "don't enable extended thinking at all"
            // Return None to omit the thinking block instead of sending budget_tokens=0
            None
          } else if (budget < 1024) {
            // Anthropic minimum is 1024
            logger.warn(
              s"Thinking budget $budget is below Anthropic minimum of 1024. Clamping to 1024."
            )
            Some(1024)
          } else {
            Some(budget)
          }

        case None =>
          logger.warn(
            s"No thinking budget mapping found for reasoning effort '$effortKey' in config path: $configPath"
          )
          None
      }
    }
  }

  def toAnthropicSettings(
    settings: CreateChatCompletionSettings
  ): AnthropicCreateMessageSettings = {
    // Prioritize explicit thinking budget, fall back to reasoning_effort conversion
    val thinkingBudget = settings.anthropicThinkingBudgetTokens.orElse(
      toThinkingBudget(settings.reasoning_effort)
    )

    // handle json schema
    val responseFormat =
      settings.response_format_type.getOrElse(ChatCompletionResponseFormatType.text)

    val jsonSchema =
      if (
        responseFormat == ChatCompletionResponseFormatType.json_schema && settings.jsonSchema.isDefined
      ) {
        val jsonSchemaDef = settings.jsonSchema.get

        jsonSchemaDef.structure match {
          case Left(schema) =>
            if (jsonSchemaDef.strict)
              logger.warn(
                "OpenAI's 'strict' mode is not supported by Anthropic. The schema will be used without strict validation, and 'additionalProperties' will be set to false by default on all objects."
              )

            Some(schema)
          case Right(_) =>
            logger.warn(
              "Map-like legacy JSON schema format is not supported for Anthropic - only structured JsonSchema objects are supported"
            )
            None
        }
      } else
        None

    // When thinking is enabled, temperature must be 1.0
    val temperature = thinkingBudget match {
      case Some(_) =>
        // Thinking is enabled
        settings.temperature match {
          case Some(temp) if temp != 1.0 =>
            logger.warn(
              s"Temperature is set to $temp but thinking is enabled. Anthropic requires temperature=1 when using extended thinking. Overriding to 1.0."
            )
            Some(1.0)
          case other =>
            // No temperature set or already 1.0, keep as is
            other
        }
      case None =>
        // No thinking, use original temperature
        settings.temperature
    }

    AnthropicCreateMessageSettings(
      model = settings.model,
      max_tokens = settings.max_tokens.getOrElse(DefaultSettings.CreateMessage.max_tokens),
      metadata = Map.empty,
      stop_sequences = settings.stop,
      temperature = temperature,
      top_p = settings.top_p,
      top_k = None,
      thinking = thinkingBudget.map(ThinkingSettings.enabled),
      output_format = jsonSchema.map { schema =>
        OutputFormat.JsonSchemaFormat(schema)
      },
      speed = if (settings.anthropicFastSpeed) Some(Speed.fast) else None
    )
  }

  def toOpenAI(response: CreateMessageResponse): ChatCompletionResponse = {
    // Extract thinking blocks and estimate token count
    val thinkingText = response.thinkingText
    val thinkingTokens = if (thinkingText.nonEmpty) {
      Some(estimateTokenCount(thinkingText))
    } else {
      None
    }

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
      usage = Some(toOpenAI(response.usage, thinkingTokens)),
      originalResponse = Some(response)
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
            content = blockDelta.delta match {
              case DeltaBlock.DeltaText(text) => Some(text)
              case _                          => None
            }
          ),
          index = blockDelta.index,
          finish_reason = None
        )
      ),
      usage = None
    )

  def toOpenAIAssistantMessage(
    content: ContentBlocks,
    lastTextBlockOnly: Boolean = true
  ): OpenAIAssistantMessage = {
    val textContents = content.blocks.collect { case ContentBlockBase(TextBlock(text, _), _) =>
      text
    }

    if (textContents.isEmpty) {
      throw new IllegalArgumentException("No text content found in the response")
    }

    val singleTextContent =
      if (lastTextBlockOnly) textContents.last
      else concatenateMessages(textContents)

    OpenAIAssistantMessage(singleTextContent, name = None)
  }

  private def concatenateMessages(messageContent: Seq[String]): String =
    messageContent.mkString("\n")

  def toOpenAI(
    usageInfo: UsageInfo,
    thinkingTokens: Option[Int] = None
  ): OpenAIUsageInfo = {
    val promptTokens =
      usageInfo.input_tokens +
        usageInfo.cache_creation_input_tokens.getOrElse(0) +
        usageInfo.cache_read_input_tokens.getOrElse(0)

    OpenAIUsageInfo(
      prompt_tokens = promptTokens,
      completion_tokens = Some(usageInfo.output_tokens),
      total_tokens = promptTokens + usageInfo.output_tokens,
      prompt_tokens_details = Some(
        PromptTokensDetails(
          cached_tokens = usageInfo.cache_read_input_tokens.getOrElse(0),
          audio_tokens = None
        )
      ),
      completion_tokens_details = thinkingTokens.map { tokens =>
        CompletionTokenDetails(
          reasoning_tokens = Some(tokens),
          accepted_prediction_tokens = None,
          rejected_prediction_tokens = None
        )
      }
    )
  }

  /**
   * Estimates the number of tokens in a given text using a simple heuristic. This is an
   * approximation: tokens ≈ characters / 4 (average for English text) For more accurate
   * counting, a proper tokenizer should be used.
   */
  // TODO
  private def estimateTokenCount(text: String): Int = {
    math.max(1, text.length / 4)
  }

  /**
   * Repackages Anthropic exceptions as OpenAI exceptions for consistent error handling in
   * adapter services.
   */
  def repackAsOpenAIException[T]: PartialFunction[Throwable, Future[T]] = {
    case e: AnthropicScalaTokenCountExceededException =>
      Future.failed(new OpenAIScalaTokenCountExceededException(e.getMessage, e))
    case e: AnthropicScalaUnauthorizedException =>
      Future.failed(new OpenAIScalaUnauthorizedException(e.getMessage, e))
    case e: AnthropicScalaRateLimitException =>
      Future.failed(new OpenAIScalaRateLimitException(e.getMessage, e))
    case e: AnthropicScalaServerErrorException =>
      Future.failed(new OpenAIScalaServerErrorException(e.getMessage, e))
    case e: AnthropicScalaEngineOverloadedException =>
      Future.failed(new OpenAIScalaEngineOverloadedException(e.getMessage, e))
    case e: AnthropicScalaClientTimeoutException =>
      Future.failed(new OpenAIScalaClientTimeoutException(e.getMessage, e))
    case e: AnthropicScalaClientUnknownHostException =>
      Future.failed(new OpenAIScalaClientUnknownHostException(e.getMessage, e))
    case e: AnthropicScalaNotFoundException =>
      Future.failed(new OpenAIScalaClientException(e.getMessage, e))
    case e: AnthropicScalaClientException =>
      Future.failed(new OpenAIScalaClientException(e.getMessage, e))
  }

  def toTracedBlocks(response: CreateMessageResponse): Seq[TracedBlock] =
    response.blockContents.map { blockContent =>
      import Content.ContentBlock._

      def truncate(
        s: String,
        maxLen: Int = 50
      ): String =
        if (s.length <= maxLen) s else s.take(maxLen) + "..."

      val (content, trace) = blockContent match {
        case TextBlock(text, citations) =>
          val citationInfo = if (citations.nonEmpty) s" [${citations.size} citations]" else ""
          (Some(text), truncate(text) + citationInfo)
        case ThinkingBlock(thinking, _) =>
          (Some(thinking), truncate(thinking))
        case RedactedThinkingBlock(data) =>
          (Some(data), "[redacted]")
        case ToolUseBlock(id, name, input) =>
          (Some(input.toString), s"$name (id: $id)")
        case ServerToolUseBlock(id, name, input) =>
          (Some(input.toString), s"$name (id: $id)")
        case WebSearchToolResultBlock(content, toolUseId) =>
          (Some(content.toString), s"toolUseId: $toolUseId")
        case WebFetchToolResultBlock(content, toolUseId) =>
          (Some(content.toString), s"toolUseId: $toolUseId")
        case McpToolUseBlock(id, name, serverName, input) =>
          (Some(input.toString), s"$name @ $serverName (id: $id)")
        case McpToolResultBlock(content, isError, toolUseId) =>
          (Some(content.toString), s"toolUseId: $toolUseId${if (isError) " [error]" else ""}")
        case ContainerUploadBlock(fileId) =>
          (None, s"fileId: $fileId")
        case CodeExecutionToolResultBlock(content, toolUseId) =>
          (Some(content.toString), s"toolUseId: $toolUseId")
        case BashCodeExecutionToolResultBlock(content, toolUseId) =>
          (Some(content.toString), s"toolUseId: $toolUseId")
        case TextEditorCodeExecutionToolResultBlock(content, toolUseId) =>
          (Some(content.toString), s"toolUseId: $toolUseId")
        case MediaBlock(_, _, mediaType, data, title, _, _) =>
          (Some(data), s"$mediaType${title.map(t => s" ($t)").getOrElse("")}")
        case TextsContentBlock(texts, title, _, _) =>
          (Some(texts.mkString("\n")), title.getOrElse(s"${texts.size} text(s)"))
        case FileDocumentContentBlock(fileId, title, _, _) =>
          (None, title.getOrElse(s"fileId: $fileId"))
      }

      TracedBlock(blockContent.`type`.toString, text = content, summary = trace, blockContent)
    }
}
