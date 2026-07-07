package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{TextBlock, ToolUseBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.tools.{
  CustomTool,
  ToolChoice => AnthropicToolChoice
}
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
import scala.concurrent.{ExecutionContext, Future}

private[service] class OpenAIAnthropicChatCompletionService(
  underlying: AnthropicService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra
    with OpenAIChatCompletionBatchService {

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
      .createMessageStreamed(
        toAnthropicSystemMessages(messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(messages.filter(!_.isSystem), settings),
        toAnthropicSettings(settings)
      )
      .map(toOpenAI)

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse] = {
    val anthropicTools = tools.collect { case ft: FunctionTool =>
      CustomTool(
        name = ft.name,
        inputSchema = ft.parameters,
        description = ft.description
      )
    }

    val disableParallel = settings.parallel_tool_calls.map(!_)

    val anthropicToolChoice = responseToolChoice match {
      case Some(name) =>
        Some(AnthropicToolChoice.Tool(name, disableParallelToolUse = disableParallel))
      case None =>
        Some(AnthropicToolChoice.Auto(disableParallelToolUse = disableParallel))
    }

    val anthropicSettings = toAnthropicSettings(settings).copy(
      tools = anthropicTools,
      tool_choice = anthropicToolChoice
    )

    underlying
      .createMessage(
        toAnthropicSystemMessages(messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(messages.filter(!_.isSystem), settings),
        anthropicSettings
      )
      .map(toOpenAIToolResponse)
      .recoverWith(repackAsOpenAIException)
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
