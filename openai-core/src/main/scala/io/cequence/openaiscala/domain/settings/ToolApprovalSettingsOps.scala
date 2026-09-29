package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response.{
  ChatChunk,
  ChatCompletionResponse,
  ChatToolCompletionResponse,
  ToolApprovalDecision
}
import io.cequence.openaiscala.domain.responsesapi.Response
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.MCPApprovalRequest
import akka.NotUsed
import akka.stream.scaladsl.Source
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.util.Try

/**
 * Resuming a run paused for human approval: the decisions answering its
 * `ChatChunk.ToolApprovalRequest`s ride in `CreateChatCompletionSettings.extra_params`, so
 * they pass unchanged through wrapping adapters (routers, the merged streamed service). The
 * resume must reach the SAME OpenAI project / Anthropic workspace that paused the run - do not
 * send it through round-robin / random-order / parallel-take-first adapters over several of
 * them, and avoid retry adapters around it (a retried resume may re-send answers that were
 * already applied). Decisions are one-shot: set them on the resume call only, not on settings
 * reused for later turns.
 *
 * {{{
 * import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
 *
 * val first = service.createChatToolCompletionStreamed(messages, tools, None, settings)
 * // ... first.assembled: finishReason = approval_required, toolApprovalRequests = [r1, r2]
 * service.createChatToolCompletionStreamed(
 *   messages, tools, None, settings.setToolApprovalDecisions(Seq(r1.approve, r2.deny("no")))
 * )
 * }}}
 *
 * What the resume call sends besides the decisions is provider-specific:
 *   - OpenAI (Responses API): the same `tools` (never carried over) and `messages` - the
 *     paused response is stored (the default whenever a tool may ask for approval) and
 *     continued by id (`previous_response_id` = the requests' `runId`), so only the system
 *     messages (as instructions) and any trailing tool messages (outputs of client function
 *     calls of the paused response - earlier outputs are already stored and not sent again)
 *     are sent, plus the answers. Every pending request must be answered (OpenAI rejects a
 *     resume that leaves one out); a resumed run may pause again - resume it the same way.
 *     With an explicit `store = false` the answered requests are replayed after the history
 *     instead - one pause deep only, and a request left unanswered is simply dropped.
 *   - Anthropic Managed Agents: the history lives in the session (the requests' `runId`), so
 *     `messages` are ignored. A resume whose confirmations were not applied (the POST failed),
 *     or whose next pause could not be looked up, keeps the session (retry the decisions, or
 *     `deleteSession(runId)`); a paused session that is never resumed is kept too - delete it
 *     when abandoning the run. Any other failure of a resumed turn deletes it.
 *
 * Only these two support it; every other chat-completion adapter (and entry point) refuses a
 * call carrying decisions rather than silently starting a fresh run, and the providers that
 * cannot pause refuse an `MCPServerTool(requireApproval = true)` rather than running it
 * unapproved. The OpenAI-shaped `createChatCompletionStreamed` view reports the pause as
 * `finish_reason = "approval_required"` but cannot carry the requests - use the typed stream.
 *
 * The non-streamed `createChatToolCompletion` (OpenAI Responses) reports a paused run with
 * `finish_reason = "approval_required"`; its requests are `response.toolApprovalRequests`.
 *
 * `createChatToolCompletionStreamedWithApprovals` runs this loop for you: a callback answers
 * each pending request and the rounds are joined into one stream (`ToolApprovalLoop`).
 */
object ToolApprovalSettingsOps {

  val ToolApprovalDecisionsParam = "tool_approval_decisions"

  /** The extra-params keys consumed by the adapters (never sent to a provider API). */
  val knownParams: Set[String] = Set(ToolApprovalDecisionsParam)

  implicit class RichToolApprovalCreateChatCompletionSettings(
    settings: CreateChatCompletionSettings
  ) {

    /** Resumes the paused run the decisions' requests belong to. */
    def setToolApprovalDecisions(
      decisions: Seq[ToolApprovalDecision]
    ): CreateChatCompletionSettings =
      if (decisions.isEmpty)
        settings.copy(extra_params = settings.extra_params - ToolApprovalDecisionsParam)
      else
        settings.copy(
          extra_params = settings.extra_params + (ToolApprovalDecisionsParam -> decisions)
        )

    def toolApprovalDecisions: Seq[ToolApprovalDecision] =
      settings.extra_params.get(ToolApprovalDecisionsParam) match {
        case Some(decisions: Seq[_]) =>
          decisions.collect { case d: ToolApprovalDecision => d }
        case _ => Nil
      }
  }

  implicit class ToolApprovalChatToolCompletionResponseOps(
    response: ChatToolCompletionResponse
  ) {

    /**
     * The pending calls of a run paused for approval (`finish_reason = approval_required`).
     */
    def toolApprovalRequests: Seq[ChatChunk.ToolApprovalRequest] =
      ToolApprovalSettingsOps.toolApprovalRequests(response.originalResponse)
  }

  implicit class ToolApprovalChatCompletionResponseOps(response: ChatCompletionResponse) {

    /**
     * The pending calls of a run paused for approval (`finish_reason = approval_required`).
     */
    def toolApprovalRequests: Seq[ChatChunk.ToolApprovalRequest] =
      ToolApprovalSettingsOps.toolApprovalRequests(response.originalResponse)
  }

  /**
   * The approval requests in a provider response carried as `originalResponse` (an OpenAI
   * Responses API `Response`), in output order.
   */
  def toolApprovalRequests(originalResponse: Option[Any]): Seq[ChatChunk.ToolApprovalRequest] =
    originalResponse.toSeq.flatMap {
      case response: Response =>
        response.output.collect { case request: MCPApprovalRequest =>
          ChatChunk.ToolApprovalRequest(
            requestId = request.id,
            toolName = request.name,
            arguments = request.arguments,
            serverName = Some(request.serverLabel),
            runId = response.id,
            raw = Json.obj(
              "id" -> request.id,
              "type" -> request.`type`,
              "name" -> request.name,
              "arguments" -> request.arguments,
              "server_label" -> request.serverLabel
            )
          )
        }
      case _ => Nil
    }

  /**
   * The exception for a call carrying approval decisions on a service that cannot resume a
   * paused run (None when there are no decisions) - adapters fail with it instead of silently
   * starting a fresh run.
   */
  def unsupportedDecisions(
    settings: CreateChatCompletionSettings,
    service: String
  ): Option[OpenAIScalaClientException] =
    if (settings.toolApprovalDecisions.isEmpty) None
    else
      Some(
        new OpenAIScalaClientException(
          s"$service cannot resume a run paused for tool approval - approval decisions " +
            "(setToolApprovalDecisions) are supported by the OpenAI Responses API (the full " +
            "OpenAIService, sync or withStreaming) and the Anthropic Managed Agents adapter only."
        )
      )

  /**
   * `body`, unless the settings carry approval decisions `service` cannot resume (a `body`
   * that throws while building the call fails the Future).
   */
  def refusingDecisions[T](
    settings: CreateChatCompletionSettings,
    service: String
  )(
    body: => Future[T]
  ): Future[T] =
    unsupportedDecisions(settings, service).fold(Future.fromTry(Try(body)).flatten)(
      Future.failed
    )

  /**
   * `body`, unless the settings carry approval decisions `service` cannot resume (a `body`
   * that throws while building the call fails the stream).
   */
  def refusingDecisionsStream[T](
    settings: CreateChatCompletionSettings,
    service: String
  )(
    body: => Source[T, NotUsed]
  ): Source[T, NotUsed] =
    unsupportedDecisions(settings, service).fold(
      Try(body).fold(e => Source.failed[T](e), identity)
    )(e => Source.failed[T](e))
}
