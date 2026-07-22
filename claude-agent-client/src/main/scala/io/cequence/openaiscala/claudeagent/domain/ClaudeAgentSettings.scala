package io.cequence.openaiscala.claudeagent.domain

/**
 * Settings for a [[io.cequence.openaiscala.claudeagent.service.ClaudeAgentService]] session -
 * mirrors the CLI flags / initial control_request fields of `claude --output-format
 * stream-json --input-format stream-json`.
 *
 * @param model
 *   Maps to `--model`.
 * @param systemPrompt
 *   Maps to `--system-prompt`.
 * @param appendSystemPrompt
 *   Maps to `--append-system-prompt`.
 * @param allowedTools
 *   Maps to `--allowedTools` (comma-joined).
 * @param disallowedTools
 *   Maps to `--disallowedTools` (comma-joined).
 * @param permissionMode
 *   Maps to `--permission-mode` (values: `"default"|"acceptEdits"|"bypassPermissions"|"plan"|
 *   "dontAsk"|"auto"`).
 * @param permissionPromptToolName
 *   Maps to `--permission-prompt-tool`. Defaults to the CLI's `stdio` handler so unmatched
 *   tool calls are delivered as [[ClaudeAgentEvent.ToolPermissionRequest]] events and can be
 *   answered with `respondToolPermission`.
 * @param maxTurns
 *   Maps to `--max-turns`.
 * @param cwd
 *   Process working directory for the spawned `claude` subprocess.
 * @param resume
 *   Maps to `--resume=<id>`.
 * @param continueSession
 *   Maps to `--continue`.
 * @param forkSession
 *   Maps to `--fork-session`.
 * @param includePartialMessages
 *   Maps to `--include-partial-messages` (enables `stream_event` token-level deltas).
 * @param executablePath
 *   Override for locating the `claude` binary (default: resolve via `PATH`).
 * @param env
 *   Merged OVER the inherited process environment (caller wins on conflicting keys) - this is
 *   how callers set `ANTHROPIC_API_KEY`/`ANTHROPIC_AUTH_TOKEN`/`CLAUDE_CODE_OAUTH_TOKEN`/
 *   `ANTHROPIC_BASE_URL` if they don't want to rely on the parent process's env.
 * @param extraArgs
 *   Escape hatch, `None` value => bare `--key`, `Some(v)` => `--key v`.
 */
case class ClaudeAgentSettings(
  model: Option[String] = None,
  systemPrompt: Option[String] = None,
  appendSystemPrompt: Option[String] = None,
  allowedTools: Seq[String] = Nil,
  disallowedTools: Seq[String] = Nil,
  permissionMode: Option[String] = None,
  permissionPromptToolName: Option[String] = Some("stdio"),
  maxTurns: Option[Int] = None,
  cwd: Option[String] = None,
  resume: Option[String] = None,
  continueSession: Boolean = false,
  forkSession: Boolean = false,
  includePartialMessages: Boolean = false,
  executablePath: Option[String] = None,
  env: Map[String, String] = Map.empty,
  extraArgs: Map[String, Option[String]] = Map.empty
) {
  require(
    resume.isEmpty || !continueSession,
    "resume and continueSession cannot both be set"
  )
  require(maxTurns.forall(_ > 0), "maxTurns must be positive")
}
