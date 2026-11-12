package io.cequence.openaiscala.claudeagent.service

import io.cequence.openaiscala.claudeagent.domain.{
  ClaudeAgentEvent,
  ClaudeAgentProcessExit,
  InterruptResult,
  PermissionDecision
}
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.stream.AsyncStream
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.concurrent.Future

/**
 * A live, bidirectional session with a `claude` CLI subprocess. One instance = one running
 * process = one conversation. Unlike this library's HTTP-based streaming services, `events` is
 * NOT a cold stream that starts its work per run - the process is spawned once, eagerly, when
 * the session is created (see `ClaudeAgentServiceFactory.startSession`); `events` can still be
 * run more than once (it is backed by a broadcast hub), each run joining the live session.
 */
trait ClaudeAgentService extends CloseableService {

  /** Completes when the backing process exits, including its exit code and stderr tail. */
  def completion: Future[ClaudeAgentProcessExit]

  /**
   * Completes after the CLI has emitted its `system`/`init` handshake. `send` and
   * `sendControlRequest` wait for this automatically.
   */
  def ready: Future[ClaudeAgentEvent.SystemInit]

  /**
   * The full event stream: system/assistant/user/result/stream_event messages plus
   * tool-permission requests from the CLI. Every run starts with the `system`/`init` message
   * (replayed) and continues with the live events from the moment the run started - it is hot,
   * so run it before sending a turn whose events you need to observe. The slowest running
   * consumer paces the others; with none, events are read and dropped.
   */
  def events: AsyncStream[ClaudeAgentEvent]

  /** Sends a plain user-turn text message. */
  def send(text: String): Future[Unit]

  /**
   * Replies to a tool_use with its result (folded into a user-turn message on the wire - there
   * is no separate wire type for this).
   */
  def sendToolResult(
    toolUseId: String,
    content: String,
    isError: Boolean = false
  ): Future[Unit]

  /** Interrupts the current turn. */
  def interrupt(): Future[InterruptResult]

  /**
   * Answers a [[ClaudeAgentEvent.ToolPermissionRequest]] (match `requestId` to the event's
   * `requestId`).
   */
  def respondToolPermission(
    requestId: String,
    decision: PermissionDecision
  ): Future[Unit]

  /**
   * Escape hatch: issues an arbitrary `control_request` (subtype + payload) and returns the
   * matching `control_response`'s `response` field. Covers CLI operations not modeled as a
   * dedicated method (e.g. `set_permission_mode`, `get_context_usage`, ...).
   */
  def sendControlRequest(
    subtype: String,
    payload: JsObject = Json.obj()
  ): Future[JsValue]

  /**
   * The session id, once observed from the CLI's `system`/`init` message (`None` before that).
   */
  def sessionId: Option[String]
}
