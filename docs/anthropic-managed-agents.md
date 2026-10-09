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

See the [managedagents examples](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/anthropic/managedagents)
for end-to-end flows over every resource type.
