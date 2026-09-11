package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{TextBlock, ToolUseBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.tools.CustomTool
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMessageSettings,
  ThinkingDisplay
}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps.RichCreateChatCompletionSettings
import io.cequence.openaiscala.anthropic.domain.{
  MessageBatch,
  MessageBatchProcessingStatus,
  MessageBatchRequest,
  MessageBatchResult
}
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  BaseMessage,
  ChatCompletionBatchError,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionBatchStatus,
  ChatCompletionTool,
  FunctionCallSpec
}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}

import java.{util => ju}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

private[service] class OpenAIAnthropicChatCompletionService(
  underlying: AnthropicService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra
    with OpenAIChatCompletionBatchService {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Creates a model response for the given chat conversation.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    underlying
      .createMessage(
        toAnthropicSystemMessages(messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(messages.filter(!_.isSystem), settings),
        toAnthropicSettings(settings)
      )
      .map(toOpenAI)
      .recoverWith(repackAsOpenAIException)
  }

  /**
   * Creates a completion for the chat message(s) with streamed results.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    underlying
      .createMessageStreamedEvents(
        toAnthropicSystemMessages(messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(messages.filter(!_.isSystem), settings),
        toAnthropicSettings(settings)
      )
      .via(toOpenAIChunks)
      .mapError(toOpenAIException)

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse] = {
    val (anthropicMessages, anthropicSettings) =
      toAnthropicToolRequest(messages, tools, responseToolChoice, settings)

    underlying
      .createMessage(anthropicMessages, anthropicSettings)
      .map(toOpenAIToolResponse)
      .recoverWith(repackAsOpenAIException)
  }

  /**
   * Typed streaming with tools. Besides the OpenAI function tools, Anthropic-native tools set
   * via `settings.setAnthropicTools(...)` (web search, code execution, ...) are sent, and
   * their server-side results arrive as [[ChatChunk.ToolResult]]s. Thinking text is requested
   * with `display = summarized` whenever thinking is configured (the 5-series models omit it
   * by default), unless the caller pinned a display mode.
   */
  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] = {
    val (anthropicMessages, anthropicSettings) =
      toAnthropicToolRequest(messages, tools, responseToolChoice, settings)

    val settingsFinal = anthropicSettings.copy(
      thinking = anthropicSettings.thinking.map(thinking =>
        thinking.copy(display = thinking.display.orElse(Some(ThinkingDisplay.summarized)))
      )
    )

    underlying
      .createMessageStreamedEvents(anthropicMessages, settingsFinal)
      .via(toChatChunks)
      .mapError(toOpenAIException)
  }

  // OpenAI function tools -> Anthropic custom tools (+ Anthropic-native tools from
  // extra_params), tool choice with its forced-tool fallback, and the converted messages
  private def toAnthropicToolRequest(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): (Seq[Message], AnthropicCreateMessageSettings) = {
    val functionTools = tools.collect { case ft: FunctionTool =>
      CustomTool(
        name = ft.name,
        inputSchema = ft.parameters,
        description = ft.description
      )
    }
    val allTools = functionTools ++ settings.anthropicTools

    if (allTools.isEmpty && responseToolChoice.isDefined)
      logger.warn(
        s"Anthropic adapter: tool choice '${responseToolChoice.get}' requested but no tools were provided"
      )

    // an explicit forced choice is always sent (without tools the API rejects it loudly rather
    // than the choice being dropped silently)
    val (anthropicToolChoice, extraSystemMessages) =
      if (allTools.nonEmpty || responseToolChoice.isDefined) {
        val disableParallel = settings.parallel_tool_calls.map(!_)
        val (toolChoice, extra) =
          toAnthropicToolChoice(settings.model, responseToolChoice, disableParallel)
        (Some(toolChoice), extra)
      } else
        (None, Nil)

    val anthropicSettings = toAnthropicSettings(settings).copy(
      tools = allTools,
      tool_choice = anthropicToolChoice
    )

    val anthropicMessages =
      toAnthropicSystemMessages(
        messages.filter(_.isSystem) ++ extraSystemMessages,
        settings
      ) ++
        toAnthropicMessages(messages.filter(!_.isSystem), settings)

    (anthropicMessages, anthropicSettings)
  }

  // -- Batch processing (provider-agnostic) --

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] = {
    val anthropicSettings = toAnthropicSettings(settings)

    val anthropicRequests = requests.map { request =>
      MessageBatchRequest(
        customId = request.customId,
        messages = toAnthropicSystemMessages(request.messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(request.messages.filter(!_.isSystem), settings),
        settings = anthropicSettings
      )
    }

    underlying
      .createMessageBatch(anthropicRequests)
      .map(toBatchInfo)
      .recoverWith(repackAsOpenAIException)
  }

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    underlying.getMessageBatch(batchId).map(toBatchInfo).recoverWith(repackAsOpenAIException)

  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    underlying
      .retrieveMessageBatchResults(batchId)
      .map(_.map { item =>
        val result = item.result match {
          case MessageBatchResult.Succeeded(message) =>
            Right(toOpenAI(message))

          case MessageBatchResult.Errored(error, _) =>
            Left(ChatCompletionBatchError(error.message, Some(error.`type`)))

          case MessageBatchResult.Canceled =>
            Left(
              ChatCompletionBatchError(
                "The request was canceled before it could be processed.",
                Some("canceled")
              )
            )

          case MessageBatchResult.Expired =>
            Left(
              ChatCompletionBatchError(
                "The batch expired before the request could be processed.",
                Some("expired")
              )
            )
        }

        ChatCompletionBatchResultItem(item.customId, result)
      })
      .recoverWith(repackAsOpenAIException)

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    underlying
      .cancelMessageBatch(batchId)
      .map(toBatchInfo)
      .recoverWith(repackAsOpenAIException)

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] =
    underlying.deleteMessageBatch(batchId).map(_ => ()).recoverWith(repackAsOpenAIException)

  private def toBatchInfo(batch: MessageBatch): ChatCompletionBatchInfo = {
    val status = batch.processingStatus match {
      case MessageBatchProcessingStatus.ended =>
        // canceled batches also end as `ended`; per-request outcomes ride on the results
        ChatCompletionBatchStatus.Completed
      case _ =>
        // in_progress, canceling
        ChatCompletionBatchStatus.InProgress
    }

    ChatCompletionBatchInfo(batch.id, status, batch.processingStatus.toString)
  }

  private def toOpenAIToolResponse(
    response: io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
  ): ChatToolCompletionResponse = {
    val toolCalls = response.content.blocks.collect {
      case ContentBlockBase(ToolUseBlock(id, name, input), _) =>
        (
          id,
          FunctionCallSpec(name, input.toString): io.cequence.openaiscala.domain.ToolCallSpec
        )
    }

    val textContent = response.content.blocks.collect {
      case ContentBlockBase(TextBlock(text, _), _) => text
    }

    val message = AssistantToolMessage(
      content = if (textContent.nonEmpty) Some(textContent.mkString("\n")) else None,
      name = None,
      tool_calls = toolCalls
    )

    ChatToolCompletionResponse(
      id = response.id,
      created = new ju.Date(),
      model = response.model,
      system_fingerprint = response.stop_reason,
      choices = Seq(
        ChatToolCompletionChoiceInfo(
          message = message,
          index = 0,
          finish_reason = response.stop_reason
        )
      ),
      usage = Some(toOpenAI(response.usage))
    )
  }

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}

object OpenAIAnthropicChatCompletionService {
  def apply(
    underlying: AnthropicService
  )(
    implicit executionContext: ExecutionContext
  ): OpenAIChatCompletionService with OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIAnthropicChatCompletionService(underlying)
}
