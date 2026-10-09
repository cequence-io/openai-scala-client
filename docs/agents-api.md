# Agents API (beta) 🤖

[← back to the README](../README.md)

OpenAI's Agents API: durable cloud agents, sessions and their event streams, and the chat-completion adapter over them.

- **Agents API** (🔥 New, beta) - durable cloud agents: a session runs an agent (inline config or a reusable agent) with
  an optional environment (an OpenAI-hosted sandbox, your own machine, or none); the full service covers agents, sessions,
  input events, items / turns / subagents, artifacts and environments, and the streamed service the session events.

```scala
  import io.cequence.openaiscala.domain.agents._

  service // OpenAIServiceFactory.withStreaming()
    .createAgentSessionStreamed(
      CreateAgentSessionSettings(
        agent = Some(AgentConfig(model = Some(ModelId.gpt_6_luna), instructions = Some("Be brief."))),
        environment = AgentEnvironment.OpenAIHosted() // or NoEnvironment (the default)
      ),
      AgentInput.Text("Run `uname -s` and report the output.")
    )
    .runWith(Sink.seq) // the stream ends once the session idles
    .map(events => println(AgentSessionEvents.finalAnswer(events)))
```

  A client function tool (`AgentTool.Function`) pauses the session (`SessionUpdated.requiresAction`) - answer its
  `session.pendingFunctionCalls` with `sendAgentSessionEvents(sessionId, Seq(AgentSessionInput.toolResult(call, output)))`
  and keep consuming the same stream. Follow-up turns: subscribe with `streamAgentSessionEvents(sessionId).via(AgentSessionEvents.untilSettled())`,
  then `sendAgentSessionEvents(sessionId, Seq(AgentSessionInput.text("...")))`. As a chat-completion service:
  `service.agentsAsChatCompletion(environment = AgentEnvironment.OpenAIHosted())` - each call runs one session turn (the
  final answer as the text, the agent's commentary as thinking, commands / MCP / web search / subagents as server-side
  tool calls), and client function tools work as the usual chat tool loop (the session pauses and the call carrying the
  tool results resumes it). See
  [OpenAIAgentsApiSmokeTest](../openai-examples/src/main/scala/io/cequence/openaiscala/examples/agents/OpenAIAgentsApiSmokeTest.scala).
