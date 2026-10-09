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

See [ClaudeAgentOneShotQueryExample](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/claudeagent/ClaudeAgentOneShotQueryExample.scala)
for a complete runnable example, and
[ClaudeAgentToolPermissionExample](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/claudeagent/ClaudeAgentToolPermissionExample.scala)
for a full bidirectional session that handles tool-permission requests.

**Requires the `claude` CLI installed separately** (e.g. `npm install -g
@anthropic-ai/claude-code`) and authenticated - either via an interactive Claude subscription
login (`claude /login`) or one of `ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN` /
`CLAUDE_CODE_OAUTH_TOKEN` in the environment.
