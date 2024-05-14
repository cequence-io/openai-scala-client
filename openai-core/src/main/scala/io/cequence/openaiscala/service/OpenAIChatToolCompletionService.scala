package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.{BaseMessage, ToolSpec}
import io.cequence.openaiscala.domain.response.ChatToolCompletionResponse
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings

import scala.concurrent.Future

trait OpenAIChatToolCompletionService extends OpenAIServiceConsts with CloseableService {

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
  def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ToolSpec],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse]

}
