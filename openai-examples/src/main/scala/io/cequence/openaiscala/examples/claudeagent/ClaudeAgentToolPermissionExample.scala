package io.cequence.openaiscala.examples.claudeagent

import io.cequence.openaiscala.claudeagent.domain.{
  ClaudeAgentEvent,
  ClaudeAgentSettings,
  PermissionDecision
}
import io.cequence.openaiscala.claudeagent.service.{
  ClaudeAgentService,
  ClaudeAgentServiceFactory
}
import io.cequence.openaiscala.examples.ExampleBase
import play.api.libs.json.Json

import scala.concurrent.Future

/**
 * Demonstrates the differentiator of [[ClaudeAgentService]] over this library's HTTP-based
 * `AnthropicManagedAgentService`: a full bidirectional session where the host can be asked,
 * mid-turn, to approve or deny a tool call - via `permissionMode = "default"` plus handling
 * [[ClaudeAgentEvent.ToolPermissionRequest]] and replying through
 * `ClaudeAgentService.respondToolPermission`.
 *
 * Requires the same setup as [[ClaudeAgentOneShotQueryExample]]: the `claude` CLI installed
 * and on `PATH` (or `ClaudeAgentSettings(executablePath = ...)`), and authenticated via a
 * Claude subscription login (`claude /login`) or one of `ANTHROPIC_API_KEY` /
 * `ANTHROPIC_AUTH_TOKEN` / `CLAUDE_CODE_OAUTH_TOKEN`.
 */
object ClaudeAgentToolPermissionExample extends ExampleBase[ClaudeAgentService] {

  override protected val service: ClaudeAgentService =
    ClaudeAgentServiceFactory.startSession(
      ClaudeAgentSettings(
        // "default" leaves Bash unapproved, so the transport's stdio permission-prompt
        // handler emits a ToolPermissionRequest instead of the CLI auto-running it.
        permissionMode = Some("default"),
        // An explicit ask rule wins over any inherited allow rule, keeping this demo
        // deterministic on machines that have their own Claude Code permissions.
        extraArgs = Map("settings" -> Some("""{"permissions":{"ask":["Bash"]}}"""))
      )
    )

  override protected def run: Future[_] = {
    // Same stream-first ordering as ClaudeAgentOneShotQueryExample: subscribe before
    // sending, since `events` is a BroadcastHub fed by an already-running subprocess.
    val consumed = service.events
      .takeWhile(event => !isTurnEnd(event), inclusive = true)
      .runForeach(printEvent(service, _))

    service
      .send("List the files in the current directory using the bash tool.")
      .flatMap(_ => consumed)

    // service.interrupt(): Future[InterruptResult] cancels the current turn mid-flight -
    // not called here since it would race with (and obscure) the permission-request demo
    // above, but it's available at any point on an active session, e.g.:
    //   service.interrupt().foreach(r => println(s"stillQueued=${r.stillQueued}"))
  }

  private def isTurnEnd(event: ClaudeAgentEvent): Boolean = event match {
    case _: ClaudeAgentEvent.ResultSuccess => true
    case _: ClaudeAgentEvent.ResultError   => true
    case _                                 => false
  }

  private def printEvent(
    service: ClaudeAgentService,
    event: ClaudeAgentEvent
  ): Unit = event match {
    case e: ClaudeAgentEvent.SystemInit =>
      println(s"[system/init] session=${e.sessionId} model=${e.model} tools=${e.tools}")

    case e: ClaudeAgentEvent.ToolPermissionRequest =>
      println(
        s"[tool-permission] tool=${e.toolName} toolUseId=${e.toolUseId} " +
          s"input=${Json.stringify(e.input)}"
      )
      // DEMO ONLY: blindly approving every request is unsafe. A real application must inspect
      // `e.toolName` / `e.input` here (e.g. check the actual bash command, restrict to a
      // sandboxed cwd, require human-in-the-loop confirmation for destructive operations, ...)
      // and only then decide between PermissionDecision.Allow(...) and
      // PermissionDecision.Deny(message, interrupt = ...).
      service.respondToolPermission(e.requestId, PermissionDecision.Allow(e.input))
      println(s"[tool-permission] auto-approved (DEMO ONLY) requestId=${e.requestId}")

    case e: ClaudeAgentEvent.ResultSuccess =>
      println(s"[result] ${e.result} (totalCostUsd=${e.totalCostUsd})")

    case e: ClaudeAgentEvent.ResultError =>
      println(s"[result/error] subtype=${e.subtype} errors=${e.errors.mkString("; ")}")

    case e: ClaudeAgentEvent.Unknown =>
      println(s"[unknown] type=${e.`type`} subtype=${e.subtype.getOrElse("n/a")}")

    case _ =>
    // Assistant / UserEcho / StreamDelta - not the focus of this example; see
    // ClaudeAgentOneShotQueryExample for extracting readable text out of Assistant events.
  }
}
