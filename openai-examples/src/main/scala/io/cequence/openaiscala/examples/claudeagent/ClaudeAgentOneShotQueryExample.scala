package io.cequence.openaiscala.examples.claudeagent

import io.cequence.openaiscala.claudeagent.domain.{ClaudeAgentEvent, ClaudeAgentSettings}
import io.cequence.openaiscala.claudeagent.service.{
  ClaudeAgentService,
  ClaudeAgentServiceFactory
}
import io.cequence.openaiscala.examples.ExampleBase
import play.api.libs.json.JsObject

import scala.concurrent.Future

/**
 * Minimal one-shot query against a `claude` CLI subprocess session via
 * [[io.cequence.openaiscala.claudeagent.service.ClaudeAgentService]] - the typed,
 * bidirectional NDJSON-over-stdio session API, distinct from this library's HTTP-based
 * `AnthropicManagedAgentService` (see
 * `io.cequence.openaiscala.examples.anthropic.managedagents` for the latter).
 *
 * Requires:
 *   - the `claude` CLI installed and resolvable on `PATH` (e.g. `npm install -g
 * @anthropic-ai/claude-code`),
 *   or pass `ClaudeAgentSettings(executablePath = Some("/path/to/claude"))`
 *   - the CLI authenticated, either via an active Claude subscription login (run `claude
 *     /login` once beforehand, interactively) OR one of `ANTHROPIC_API_KEY` /
 *     `ANTHROPIC_AUTH_TOKEN` / `CLAUDE_CODE_OAUTH_TOKEN` present in the environment
 */
object ClaudeAgentOneShotQueryExample extends ExampleBase[ClaudeAgentService] {

  override protected val service: ClaudeAgentService =
    ClaudeAgentServiceFactory.startSession(
      ClaudeAgentSettings(model = Some("claude-haiku-4-5"))
    )

  override protected def run: Future[_] = {
    // Subscribe to `events` BEFORE sending the first turn. `system`/`init` is typically
    // the very first line the CLI emits, and since the subprocess is already running,
    // sending before attaching a consumer risks the CLI processing (and emitting events
    // for) the turn faster than we start reading - a plain sequential subscribe-then-send
    // avoids that race without needing any concurrent-start trick.
    val consumed = service.events
      // ResultSuccess/ResultError both mark the natural end of a single turn - the
      // process itself stays alive afterwards, so without this the stream would never
      // complete on its own and the example Future would never resolve.
      .takeWhile(event => !isTurnEnd(event), inclusive = true)
      .runForeach(printEvent)

    service
      .send("Explain the difference between Scala's Option and Try in one sentence.")
      .flatMap(_ => consumed)
  }

  private def isTurnEnd(event: ClaudeAgentEvent): Boolean = event match {
    case _: ClaudeAgentEvent.ResultSuccess => true
    case _: ClaudeAgentEvent.ResultError   => true
    case _                                 => false
  }

  private def printEvent(event: ClaudeAgentEvent): Unit = event match {
    case e: ClaudeAgentEvent.SystemInit =>
      println(s"[system/init] session=${e.sessionId} model=${e.model}")

    case e: ClaudeAgentEvent.Assistant =>
      val texts = (e.message \ "content")
        .asOpt[Seq[JsObject]]
        .getOrElse(Nil)
        .collect {
          case block if (block \ "type").asOpt[String].contains("text") =>
            (block \ "text").asOpt[String].getOrElse("")
        }
        .mkString(" ")
        .trim

      println(s"[assistant] $texts")

    case e: ClaudeAgentEvent.ResultSuccess =>
      println(s"[result] ${e.result} (totalCostUsd=${e.totalCostUsd})")

    case e: ClaudeAgentEvent.ResultError =>
      println(s"[result/error] subtype=${e.subtype} errors=${e.errors.mkString("; ")}")

    case e: ClaudeAgentEvent.Unknown =>
      println(s"[unknown] type=${e.`type`} subtype=${e.subtype.getOrElse("n/a")}")

    case _ =>
    // UserEcho / StreamDelta / ToolPermissionRequest - not relevant for this simple example;
    // see ClaudeAgentToolPermissionExample for handling ToolPermissionRequest.
  }
}
