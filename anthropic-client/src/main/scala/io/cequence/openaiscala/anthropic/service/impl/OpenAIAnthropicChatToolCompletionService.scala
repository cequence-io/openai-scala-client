package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.Message.UserMessage
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{BaseMessage, SystemMessage, ToolSpec}
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra,
  OpenAIChatToolCompletionService
}

import scala.concurrent.{ExecutionContext, Future}

private[service] class OpenAIAnthropicChatToolCompletionService(
  underlying: AnthropicService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatToolCompletionService {

  /**
   * Creates a model response for the given chat conversation expecting a tool call.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param tools
   *   A list of tools the model may call. Currently, only functions are supported as a tool.
   *   Use this to provide a list of functions the model may generate JSON inputs for.
   * @param responseToolChoice
   *   Controls which (if any) function/tool is called by the model. Specifying a particular
   *   function forces the model to call that function (must be listed in `tools`). Otherwise,
   *   the default "auto" mode is used where the model can pick between generating a message or
   *   calling a function.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ToolSpec],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] = {
    val anthropicResponseF: Future[CreateMessageResponse] = underlying.createToolMessage(
      toAnthropic(messages) ++ responseToolChoice.map(toAnthropicToolUseEncouragement),
      toAnthropicSystemPrompt(messages),
      toAnthropicToolSpecs(tools),
      toAnthropic(settings, messages)
    )
    anthropicResponseF.map(toOpenAIChatToolCompletionResponse)
  }

  // TODO: support streamed version?

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}
