package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool}
import io.cequence.openaiscala.domain.response.{ChatChunk, ChatCompletionChunkResponse}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.wsclient.service.CloseableService

/**
 * Service that offers <b>ONLY</b> a streamed version of OpenAI chat completion endpoint.
 *
 * @since March
 *   2024
 */
trait OpenAIChatCompletionStreamedServiceExtra
    extends OpenAIServiceConsts
    with CloseableService {

  /**
   * Creates a completion for the chat message(s) with streamed results.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   *
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Source[ChatCompletionChunkResponse, NotUsed]

  /**
   * Streams a chat completion as provider-neutral typed chunks ([[ChatChunk]]): text, thinking
   * / reasoning, tool calls (start, argument fragments, and the assembled call), server-side
   * tool results, citations, the finish reason and usage. Only the first choice is mapped.
   *
   * The default implementation derives the typed stream from [[createChatCompletionStreamed]]
   * (text, reasoning, finish reason, usage) and does not support tools; provider services
   * (OpenAI, Anthropic, Gemini) override it with native mappings that also carry tools and
   * server-side tool results.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param tools
   *   Function tools the model may call (may be empty).
   * @param responseToolChoice
   *   Name of the function to force, if any (`None` means "auto").
   * @param settings
   * @return
   *   typed chat completion chunks as a stream (source)
   */
  def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool] = Nil,
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Source[ChatChunk, NotUsed] =
    if (tools.isEmpty)
      createChatCompletionStreamed(messages, settings).via(ChatChunks.fromOpenAIChunks)
    else
      Source.failed(
        new OpenAIScalaClientException(
          "createChatToolCompletionStreamed with tools is not supported by this service."
        )
      )

  /** The typed stream without tools - see [[createChatToolCompletionStreamed]]. */
  final def createChatCompletionStreamedTyped(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Source[ChatChunk, NotUsed] =
    createChatToolCompletionStreamed(messages, Nil, None, settings)
}
