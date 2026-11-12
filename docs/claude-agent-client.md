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
"io.cequence" %% "openai-scala-claude-agent-client" % "2.0.0"
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
To approve a tool call unchanged, reply with `PermissionDecision.Allow(request.input)`; the CLI
requires an explicit `updated_input` object.

**`events` is hot** - an exception to this library's cold streams. `startSession` spawns the process right away (🔥 it
takes only the implicit execution context, no `Materializer`), and the process's output is read whether or not anyone
consumes it: one broadcast hub per service fans it out. Each run of `events` begins with the replayed `system/init`
event, then gets the live events from the moment the run starts (or from the oldest event still buffered for a slower
run) - earlier ones are not replayed - so start it before sending a turn whose events you need. Any number of runs
may be active at once; the slowest one paces the others (the hub buffers up to 256 events), and events nobody consumes
are dropped. Consumed as an Akka, Pekko, fs2 or ZIO stream, it is bridged once, at the hub.

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
