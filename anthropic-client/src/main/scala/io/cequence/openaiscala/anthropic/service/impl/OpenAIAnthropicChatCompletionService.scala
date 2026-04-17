package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{TextBlock, ToolUseBlock}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlockBase
import io.cequence.openaiscala.anthropic.domain.tools.{
  CustomTool,
  ToolChoice => AnthropicToolChoice
}
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  BaseMessage,
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
    with OpenAIChatCompletionStreamedServiceExtra {

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
