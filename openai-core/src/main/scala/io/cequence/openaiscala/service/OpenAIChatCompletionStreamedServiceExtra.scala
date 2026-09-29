package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool}
import io.cequence.openaiscala.domain.response.{
  ChatChunk,
  ChatCompletionChunkResponse,
  ToolApprovalDecision
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.wsclient.service.CloseableService

import scala.concurrent.{ExecutionContext, Future}

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

  /**
   * [[createChatToolCompletionStreamed]] with human approval handled by a callback: whenever
   * the run pauses for approval, `decide` answers each pending
   * [[ChatChunk.ToolApprovalRequest]] (one at a time, in order) and the run is resumed with
   * the decisions - all rounds joined into ONE stream that ends with the final answer (one
   * `Start`, no intermediate `Finish(approval_required)`, tool-call ordinals continuing across
   * rounds, usage summed). The answered requests are not in the joined stream (`decide` saw
   * them).
   *
   * {{{
   * service.createChatToolCompletionStreamedWithApprovals(messages, tools, settings = settings) {
   *   request =>
   *     if (request.toolName.startsWith("read_")) Future.successful(request.approve)
   *     else askTheUser(request) // Future[ToolApprovalDecision]
   * }
   * }}}
   *
   * Resuming is supported by the OpenAI Responses API (the full `OpenAIService`, sync or
   * `withStreaming`) and the Anthropic Managed Agents adapter; on any other service no run
   * ever pauses and this is the plain typed stream. The stream ends paused - the pending
   * requests and `Finish(approval_required)` passed through, as in the plain stream - when the
   * run pauses again after `maxApprovalRounds` resumes, or pauses together with client-side
   * function calls (run those, then resume with their tool messages and the decisions).
   * `decide` may take as long as a human needs; a failed Future fails the stream. See
   * [[ToolApprovalLoop]] for the details.
   *
   * @param settings
   *   the settings of every round (the first round uses them as they are, so they may carry
   *   decisions resuming a run paused earlier)
   * @param maxApprovalRounds
   *   how many times the run is resumed at most
   * @param decide
   *   `request.approve` / `request.deny(reason)` for a pending call
   */
  final def createChatToolCompletionStreamedWithApprovals(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool] = Nil,
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion,
    maxApprovalRounds: Int = ToolApprovalLoop.DefaultMaxRounds
  )(
    decide: ChatChunk.ToolApprovalRequest => Future[ToolApprovalDecision]
  )(
    implicit ec: ExecutionContext
  ): Source[ChatChunk, NotUsed] =
    ToolApprovalLoop(settings, decide, maxApprovalRounds)(roundSettings =>
      createChatToolCompletionStreamed(messages, tools, responseToolChoice, roundSettings)
    )

  /** The typed stream without tools - see [[createChatToolCompletionStreamed]]. */
  final def createChatCompletionStreamedTyped(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Source[ChatChunk, NotUsed] =
    createChatToolCompletionStreamed(messages, Nil, None, settings)
}
