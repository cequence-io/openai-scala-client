package io.cequence.openaiscala.perplexity.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.cequence.openaiscala.perplexity.domain.agent._
import io.cequence.openaiscala.perplexity.domain.response.{
  SonarChatCompletionChunkResponse,
  SonarChatCompletionResponse
}
import io.cequence.wsclient.service.CloseableService
import io.cequence.openaiscala.perplexity.domain.Message

import scala.concurrent.Future
import io.cequence.openaiscala.perplexity.domain.settings.SonarCreateChatCompletionSettings

/**
 * Perplexity API: the Agent API (`/v1/agent`, `/v1/models`) and the Sonar chat completions
 * API, which Perplexity supports only until 2026-09-27.
 */
trait SonarService extends CloseableService with SonarServiceConsts {

  /**
   * Generates a model’s response for the given chat conversation..
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://https://docs.perplexity.ai/api-reference/chat-completions">Perplexity
   *   Docs</a>
   */
  @deprecated(
    "Perplexity ends support for the Sonar chat completions API on 2026-09-27 - use the Agent API: createAgentResponse / createAgentResponseStreamed",
    "1.3.1"
  )
  def createChatCompletion(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Future[SonarChatCompletionResponse]

  /**
   * Generates a model’s response for the given chat conversation with streamed results.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://https://docs.perplexity.ai/api-reference/chat-completions">Perplexity
   *   Docs</a>
   */
  @deprecated(
    "Perplexity ends support for the Sonar chat completions API on 2026-09-27 - use the Agent API: createAgentResponse / createAgentResponseStreamed",
    "1.3.1"
  )
  def createChatCompletionStreamed(
    messages: Seq[Message],
    settings: SonarCreateChatCompletionSettings = DefaultSettings.CreateChatCompletion
  ): Source[SonarChatCompletionChunkResponse, NotUsed]

  // ---- Agent API ----

  /**
   * Runs the agent on the input: a preset, model or profile (see
   * [[io.cequence.openaiscala.perplexity.domain.agent.CreateAgentResponseSettings]]) with web
   * search and the other tools, returning the full response - the answer (`outputText`,
   * `citations`), the tool results, the custom function calls to execute, and usage with cost.
   * With `settings.background = true` it returns at once with status `queued`; poll with
   * [[retrieveAgentResponse]].
   *
   * @see
   *   <a href="https://docs.perplexity.ai/api-reference/agent-post">Perplexity Docs</a>
   */
  def createAgentResponse(
    input: AgentInput,
    settings: CreateAgentResponseSettings = DefaultSettings.CreateAgentResponse
  ): Future[AgentResponse]

  /**
   * [[createAgentResponse]] as a stream of typed server-sent events (text deltas, search
   * queries and results, output items, the final `response.completed`). With
   * `settings.background = true` a dropped stream can be resumed with
   * [[resumeAgentResponseStream]].
   *
   * @see
   *   <a href="https://docs.perplexity.ai/docs/agent-api/output-control">Perplexity Docs</a>
   */
  def createAgentResponseStreamed(
    input: AgentInput,
    settings: CreateAgentResponseSettings = DefaultSettings.CreateAgentResponse
  ): Source[AgentStreamEvent, NotUsed]

  /**
   * The current snapshot of a response (`GET /v1/agent/{id}`), or `None` if it does not exist,
   * belongs to another account or was created with `store = false`.
   *
   * @see
   *   <a href="https://docs.perplexity.ai/api-reference/agent-get">Perplexity Docs</a>
   */
  def retrieveAgentResponse(responseId: String): Future[Option[AgentResponse]]

  /**
   * Streams a background response (`GET /v1/agent/{id}?stream=true`), resuming after the event
   * with sequence number `startingAfter`. Only valid within the response's reconnect window -
   * after that the API answers 400 and [[retrieveAgentResponse]] gives the final snapshot.
   *
   * @see
   *   <a href="https://docs.perplexity.ai/docs/agent-api/background-mode">Perplexity Docs</a>
   */
  def resumeAgentResponseStream(
    responseId: String,
    startingAfter: Option[Int] = None
  ): Source[AgentStreamEvent, NotUsed]

  /**
   * Requests cancellation of a running (background) response. Asynchronous: the answer has
   * status `cancelling`; poll [[retrieveAgentResponse]] for the terminal `cancelled`.
   * Cancelling a finished response is a 400, an unknown id a 404.
   *
   * @see
   *   <a href="https://docs.perplexity.ai/api-reference/agent-cancel-post">Perplexity Docs</a>
   */
  def cancelAgentResponse(responseId: String): Future[AgentCancelResponse]

  /**
   * The files a response shared from its sandbox (`share_file`).
   *
   * @see
   *   <a href="https://docs.perplexity.ai/api-reference/agent-files-get">Perplexity Docs</a>
   */
  def listAgentResponseFiles(responseId: String): Future[Seq[AgentResponseFile]]

  /**
   * The raw bytes of a shared file, or `None` if it does not exist.
   *
   * @see
   *   <a href="https://docs.perplexity.ai/api-reference/agent-file-content-get">Perplexity
   *   Docs</a>
   */
  def downloadAgentResponseFile(
    responseId: String,
    fileId: String
  ): Future[Option[Source[ByteString, _]]]

  /**
   * The models of the Agent API (`provider/model` ids, usable as `model` / `models`).
   *
   * @see
   *   <a href="https://docs.perplexity.ai/api-reference/models-get">Perplexity Docs</a>
   */
  def listAgentModels: Future[Seq[AgentModel]]
}
