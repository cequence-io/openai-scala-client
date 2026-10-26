# Claude Agent Client 🖥️

[← back to the README](../README.md)


`claude-agent-client` is a separate module that wraps the **`claude` CLI as a subprocess**
(NDJSON over stdin/stdout), giving a typed, bidirectional session API compatible with the
Claude Agent SDK protocol - including tool-permission callbacks and mid-turn interrupt. This
is a fundamentally different transport from the rest of this library: it does **not** provide
an `asOpenAI()` adapter and is not a drop-in `OpenAIChatCompletionService`. It's distinct from
the HTTP-based `AnthropicManagedAgentService` (part of `anthropic-client`), which talks
directly to Anthropic's Managed Agents REST API instead of spawning a local process.

Add the dependency:

```
"io.cequence" %% "openai-scala-claude-agent-client" % "1.4.0"
```

```scala
  import io.cequence.openaiscala.claudeagent.domain.ClaudeAgentSettings
  import io.cequence.openaiscala.claudeagent.service.ClaudeAgentServiceFactory

  val service = ClaudeAgentServiceFactory.startSession(
    ClaudeAgentSettings(model = Some("claude-haiku-4-5"))
  )

  val observed = service.events.runForeach(event => println(event)) // subscribe first
  service.ready.flatMap { _ =>
    service.send("Explain the difference between Scala's Option and Try in one sentence.")
  }
```

`ready` completes after the CLI's `system/init` handshake; `send` and control requests wait for
it automatically. `completion` exposes the eventual process exit code and a bounded stderr tail.
The event stream is hot after its initial init replay, so subscribe before sending a turn whose
events must be observed. To approve a tool call unchanged, reply with
`PermissionDecision.Allow(request.input)`; the CLI requires an explicit `updated_input` object.

**Working-directory configuration is not loaded.** The CLI is started with `--setting-sources user`
(`ClaudeAgentSettings.settingSources`, default `Some(Seq("user"))`): the integrator's own
`~/.claude/settings.json` (and user-level memory, skills, commands and agents) applies, but nothing the
working directory brings:
- `<cwd>/.claude/settings.json` and `settings.local.json` - their hooks, permission rules and environment. The
  hooks run host shell commands - with the process environment, keys included - at session start and outside
  `respondToolPermission`, so a repository the agent works on must not be able to configure it.
- The project's memory - `CLAUDE.md`, `.claude/CLAUDE.md`, `CLAUDE.local.md`, `.claude/rules/` - and its skills,
  slash commands and subagents (`.claude/skills`, `.claude/commands`, `.claude/agents`). Checked with the `claude`
  CLI 2.1.310 (2026-10-26): with `user` alone none of the project's CLAUDE.md, CLAUDE.local.md, skills, commands or
  subagents reached the agent's context; with `user,project,local` all of them did. The agent can still read such a
  file with its tools, like any other file.

`Some(Nil)` loads no filesystem settings at all (the official Agent SDK's default);
`Some(Seq("user", "project"))` adds the project's settings, memory, skills, commands and subagents (`"local"` its
`settings.local.json` and `CLAUDE.local.md`), and `None` passes no flag, so the CLI loads every source - only for a
working directory you trust as much as your own code.

See [ClaudeAgentOneShotQueryExample](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/claudeagent/ClaudeAgentOneShotQueryExample.scala)
for a complete runnable example, and
[ClaudeAgentToolPermissionExample](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/claudeagent/ClaudeAgentToolPermissionExample.scala)
for a full bidirectional session that handles tool-permission requests.

**Requires the `claude` CLI installed separately** (e.g. `npm install -g
@anthropic-ai/claude-code`) and authenticated - either via an interactive Claude subscription
login (`claude /login`) or one of `ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN` /
`CLAUDE_CODE_OAUTH_TOKEN` in the environment.
