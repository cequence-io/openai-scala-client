package io.cequence.openaiscala.service

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionResponse,
  ChatToolCompletionResponse
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.Future

/**
 * Service that offers <b>ONLY</b> OpenAI chat completion endpoint. Note that this trait is
 * usable also for OpenAI-API-compatible services such as FastChat, Ollama, or OctoML.
 *
 * @since March
 *   2024
 */
trait OpenAIChatCompletionService extends OpenAIServiceConsts with CloseableService {

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
  def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Future[ChatCompletionResponse]

  /**
   * Creates a model response for the given chat conversation expecting a tool call.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param tools
   *   A list of tools the model may call: function tools the caller executes
   *   ([[io.cequence.openaiscala.domain.AssistantTool.FunctionTool]]), and the
   *   provider-neutral [[io.cequence.openaiscala.domain.ChatCompletionTool.MCPServerTool]] /
   *   [[io.cequence.openaiscala.domain.ChatCompletionTool.SkillTool]] the PROVIDER runs itself
   *   (each adapter maps them onto its native feature or fails loudly; on OpenAI they route
   *   the call through the Responses API).
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
  def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse] =
    Future.failed(
      new OpenAIScalaClientException("createChatToolCompletion is not supported")
    )
}
