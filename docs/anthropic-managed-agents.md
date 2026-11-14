# Anthropic Managed Agents 🤝

[← back to the README](../README.md)


The `openai-scala-anthropic-client` module covers Anthropic's **Managed Agents** REST API (beta `managed-agents-2026-04-01`,
not available on Bedrock) as part of the native `AnthropicService`: agents (`createAgent`, `listAgents`, `getAgent`,
`updateAgent`, `archiveAgent`, `listAgentVersions`), environments and their work queue (`createEnvironment`, `pollWork`,
`acknowledgeWork`, `recordWorkHeartbeat`, `stopWork`, `getWorkQueueStats`, ...), sessions with events, resources and
threads (`createSession`, `sendSessionEvents`, `streamSessionEvents`, `addSessionResource`, ...), deployments and runs
(`createDeployment`, `runDeployment`, `pauseDeployment`, `listDeploymentRuns`, ...), vaults and credentials
(`createVault`, `createCredential`, `mcpOAuthValidateCredential`, ...), and memory stores with versioned memories
(`createMemoryStore`, `createMemory`, `listMemoryVersions`, `redactMemoryVersion`, ...).

```scala
  import io.cequence.openaiscala.anthropic.domain.managedagents._
  import io.cequence.openaiscala.anthropic.domain.settings.{AnthropicCreateAgentSettings}

  val service: AnthropicService = AnthropicServiceFactory() // ANTHROPIC_API_KEY, or forAuthToken() / forOAuthProfile()

  for {
    agent <- service.createAgent(
      AnthropicCreateAgentSettings(
        name = "docs assistant",
        model = AgentModelConfig(NonOpenAIModelId.claude_opus_5),
        system = Some("You are a concise assistant."),
        tools = Seq(AgentTool.Toolset())
      )
    )
    versions <- service.listAgentVersions(agent.id)
    _ <- service.archiveAgent(agent.id)
  } yield versions
```

To drive a managed agent through the regular OpenAI chat-completion interface (routers, retries, streaming, ...) use
the adapter - each `createChatCompletion` call runs one session turn, agents/environments are created lazily and cached
when not given explicitly:

```scala
  val service = AnthropicServiceFactory.managedAgentAsOpenAI(
    agentId = None,        // or Some("agent_...") to pin a pre-created agent (its model wins over settings.model)
    environmentId = None   // or Some("env_...")
  )
```

🔥 The adapter takes only the implicit execution context (no `Materializer` since 2.0.0), and its streams are cold: a
streamed call that starts a turn creates the turn's session when the stream is run - each run a new session and a new,
billed turn - so a stream that is never run creates nothing.

## Session events

A session delivers only the events emitted while a subscription is live - no replay, no resume cursor. Anthropic's
guidance is to open the stream first, then send: a user message sent ~0.4 s before the stream opened lost the turn's
first four events (`session.status_running`, `session.thread_status_running`, the `user.message` echo,
`span.model_request_start`; live 2026-11-13).

- `streamSessionEvents(sessionId)` is a cold `AsyncStream` (see [Streaming](streaming.md#asyncstream)): it connects
  when it runs, a new subscription per run - for following a session, not for a turn you start.
- `subscribeSessionEvents(sessionId)` 🔥 connects when it is called. Its `Future` completes once the server confirmed
  the subscription (its first frame, a `: connected` comment, comes with the response headers), with the events from
  then on as a **single-run** stream - the shape of the official SDKs' `events.stream(...)`. Send the events that start
  or resume a turn after it completed, then run the stream. Run it once, or cancel it: an abandoned one holds its
  connection (the server keeps it open with a `:keepalive` comment every 30 s).

```scala
  import io.cequence.openaiscala.anthropic.domain.managedagents.{SessionEvent, SessionEventEnvelope}

  val turn: Future[Seq[SessionEventEnvelope]] =
    service.subscribeSessionEvents(sessionId).flatMap { events =>
      service
        .sendSessionEvents(sessionId, Seq(SessionEvent.UserMessage.text("Say hello.")))
        .recoverWith { case e =>
          events.run(_ => ()).cancel() // releases the subscription
          Future.failed(e)
        }
        .flatMap(_ => events.takeWhile(_.`type` != "session.status_idle", inclusive = true).runToSeq)
    }
```

Several subscriptions to one session each get every event (live 2026-11-13). A second run of a subscription fails - it
could not be the same subscription. To reconnect without missing events, Anthropic's recipe: subscribe, list the
history, then skip the events the history already had (by id):

```scala
  import io.cequence.wsclient.stream.AsyncStream

  // the session's whole history, page by page
  def history(sessionId: String, page: Option[String] = None): Future[Seq[SessionEventEnvelope]] =
    service.listSessionEvents(sessionId, limit = Some(100), page = page).flatMap { response =>
      response.nextPage match {
        case Some(next) => history(sessionId, Some(next)).map(response.data ++ _)
        case None       => Future.successful(response.data)
      }
    }

  def reconnect(sessionId: String): Future[AsyncStream[SessionEventEnvelope]] =
    service.subscribeSessionEvents(sessionId).flatMap { live =>
      history(sessionId).transform(
        past => {
          val seen = past.flatMap(_.id).toSet
          live.filterNot(_.id.exists(seen.contains))
        },
        e => {
          live.run(_ => ()).cancel() // releases the subscription
          e
        }
      )
    }
```

The adapter subscribes the same way before it sends a turn's kickoff or tool confirmations.

See the [managedagents examples](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/managedagents)
for end-to-end flows over every resource type.
