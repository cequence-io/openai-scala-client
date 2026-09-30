package io.cequence.openaiscala.domain.responsesapi

import play.api.libs.json.JsObject

/**
 * Server-hosted multi-agent execution for a Responses API call (beta, GPT-6.1 Sol): the model
 * may spawn subagents, message them and wait for them, all on the server. With `enabled` it
 * adds the `OpenAI-Beta: responses_multi_agent=v1` header the API then requires (see
 * [[CreateModelResponseSettings.betaHeaders]]; `enabled = false` needs none). Live-verified
 * 2026-09-30: it cannot be combined with `reasoning.summary` (400).
 *
 * @param enabled
 *   whether multi-agent execution is on
 * @param maxConcurrentSubagents
 *   the maximum number of subagents active at the same time across the whole agent tree (the
 *   root agent excluded); the API default is 3
 */
final case class MultiAgentConfig(
  enabled: Boolean = true,
  maxConcurrentSubagents: Option[Int] = None
)

/**
 * The agent that produced an item of a multi-agent response - `/root` for the root agent,
 * `/root/<task>` for a subagent.
 */
final case class AgentTag(
  agentName: String
) {
  def isRoot: Boolean = agentName == AgentTag.RootName
}

object AgentTag {
  val RootName = "/root"
}

/**
 * An action of the root (or a parent) agent on its subagents - `action` is one of
 * `spawn_agent`, `send_message`, `followup_task`, `wait_agent`, `list_agents`,
 * `interrupt_agent` (kept as a string, the list may grow); `arguments` is its JSON (a spawned
 * agent's `task_name` and its encrypted `message`).
 */
final case class MultiAgentCall(
  id: String,
  callId: String,
  action: String,
  arguments: String,
  agent: Option[AgentTag] = None
) extends Input
    with Output {
  val `type`: String = "multi_agent_call"
}

/**
 * The result of a [[MultiAgentCall]] (e.g. `{"task_name": "/root/fruits"}` for a spawned
 * agent, `{"message": "Wait completed.", "timed_out": false}` for a wait).
 */
final case class MultiAgentCallOutput(
  id: String,
  callId: String,
  action: String,
  output: Seq[OutputMessageContent] = Nil,
  agent: Option[AgentTag] = None
) extends Input
    with Output {
  val `type`: String = "multi_agent_call_output"

  def outputText: String =
    output.collect { case text: OutputMessageContent.OutputText => text.text }.mkString
}

/**
 * A message between agents of a multi-agent run - its content is typically encrypted
 * (`{"type": "encrypted_content", "encrypted_content": ...}`), so it is kept as raw JSON.
 */
final case class AgentMessage(
  id: String,
  content: Seq[JsObject] = Nil,
  author: Option[String] = None,
  recipient: Option[String] = None,
  agent: Option[AgentTag] = None
) extends Input
    with Output {
  val `type`: String = "agent_message"
}
