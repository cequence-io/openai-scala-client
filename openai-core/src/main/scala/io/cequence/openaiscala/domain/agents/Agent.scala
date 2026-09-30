package io.cequence.openaiscala.domain.agents

import io.cequence.openaiscala.domain.responsesapi.MultiAgentConfig
import io.cequence.openaiscala.domain.settings.{ReasoningEffort, ServiceTier}
import play.api.libs.json.JsObject

import java.{util => ju}

/**
 * The reasoning configuration of an agent.
 *
 * @param effort
 *   the reasoning effort (the model's default when absent)
 * @param summary
 *   `auto`, `concise` or `detailed` - reasoning summaries in the session's items / events
 */
final case class AgentReasoning(
  effort: Option[ReasoningEffort] = None,
  summary: Option[String] = None
)

/**
 * An agent's configuration: the model and how it works. Used inline for a session
 * ([[CreateAgentSessionSettings.agent]]) and, with a name and metadata, for a reusable agent
 * ([[CreateAgentSettings]]).
 *
 * @param model
 *   the model, e.g. `gpt-6-astra`, `gpt-6.1-sol`, `gpt-6-luna`
 * @param instructions
 *   custom instructions appended to the agent's default base instructions
 * @param reasoning
 *   the reasoning effort / summaries
 * @param text
 *   the text configuration (`format`, `verbosity`) as JSON
 * @param serviceTier
 *   the service tier of the agent's model requests (incl. `ultrafast`)
 * @param tools
 *   the agent's tools - None inherits (a referenced agent's tools), Some(Nil) clears them
 * @param multiAgent
 *   server-hosted multi-agent execution (subagents)
 */
final case class AgentConfig(
  model: Option[String] = None,
  instructions: Option[String] = None,
  reasoning: Option[AgentReasoning] = None,
  text: Option[JsObject] = None,
  serviceTier: Option[ServiceTier] = None,
  tools: Option[Seq[AgentTool]] = None,
  multiAgent: Option[MultiAgentConfig] = None
)

/**
 * A reusable agent (`POST /agents`) - sessions reference it by id.
 */
final case class CreateAgentSettings(
  model: String,
  name: Option[String] = None,
  instructions: Option[String] = None,
  reasoning: Option[AgentReasoning] = None,
  text: Option[JsObject] = None,
  serviceTier: Option[ServiceTier] = None,
  tools: Seq[AgentTool] = Nil,
  multiAgent: Option[MultiAgentConfig] = None,
  metadata: Map[String, String] = Map()
)

/**
 * An agent update (`POST /agents/{id}`) - only the set fields change.
 */
final case class UpdateAgentSettings(
  model: Option[String] = None,
  name: Option[String] = None,
  instructions: Option[String] = None,
  reasoning: Option[AgentReasoning] = None,
  text: Option[JsObject] = None,
  serviceTier: Option[ServiceTier] = None,
  tools: Option[Seq[AgentTool]] = None,
  multiAgent: Option[MultiAgentConfig] = None,
  metadata: Option[Map[String, String]] = None
)

/**
 * A reusable agent, or the agent of a session (then `createdAt` / `updatedAt` are absent).
 * `serviceTier` is the effective policy (`auto` by default).
 */
final case class Agent(
  id: String,
  model: String,
  name: Option[String] = None,
  instructions: Option[String] = None,
  reasoning: Option[AgentReasoning] = None,
  text: Option[JsObject] = None,
  serviceTier: Option[String] = None,
  tools: Seq[AgentTool] = Nil,
  multiAgent: Option[MultiAgentConfig] = None,
  metadata: Map[String, String] = Map(),
  createdAt: Option[ju.Date] = None,
  updatedAt: Option[ju.Date] = None
)

/**
 * A tool of an agent. [[AgentTool.Raw]] carries any tool this client does not model (sent and
 * read verbatim).
 */
sealed trait AgentTool

object AgentTool {

  /**
   * A client-side function: calling it pauses the session (`requires_action`) until the
   * application posts its result ([[AgentSessionInput.ToolResult]]).
   *
   * @param parameters
   *   the JSON schema of the arguments
   * @param deferLoading
   *   whether it is discovered through tool search instead of loaded up front
   */
  final case class Function(
    name: String,
    description: String,
    parameters: JsObject,
    deferLoading: Option[Boolean] = None
  ) extends AgentTool

  case object ToolSearch extends AgentTool

  /** Whether tools can be called from model-generated code (default true). */
  final case class ProgrammaticToolCalling(
    enabled: Option[Boolean] = None
  ) extends AgentTool

  /**
   * A remote (HTTP) or local (stdio, in the environment) MCP server.
   *
   * @param credentialId
   *   an attached vault credential authenticating the server
   * @param required
   *   whether the server must initialize before the first turn
   */
  final case class Mcp(
    serverLabel: String,
    transport: McpTransport,
    credentialId: Option[String] = None,
    allowedTools: Option[Seq[String]] = None,
    required: Option[Boolean] = None,
    requestMetadata: Option[JsObject] = None,
    connectionOrigin: Option[JsObject] = None
  ) extends AgentTool

  /**
   * @param mode
   *   `disabled`, `cached` or `live`
   * @param contextSize
   *   `low`, `medium` or `high`
   * @param location
   *   the approximate location localizing the results, as JSON
   */
  final case class WebSearch(
    mode: Option[String] = None,
    contextSize: Option[String] = None,
    allowedDomains: Option[Seq[String]] = None,
    location: Option[JsObject] = None
  ) extends AgentTool

  /** Operating software through its UI in an OpenAI-hosted browser. */
  final case class ComputerUse(
    includeScreenshots: Option[Boolean] = None
  ) extends AgentTool

  /** Any other tool, as its JSON (`type` included). */
  final case class Raw(json: JsObject) extends AgentTool
}

sealed trait McpTransport

object McpTransport {

  final case class Http(
    serverUrl: String,
    authorization: Option[String] = None,
    headers: Map[String, String] = Map()
  ) extends McpTransport

  /** A server started in the agent's environment. */
  final case class Stdio(
    command: String,
    cwd: String,
    args: Seq[String] = Nil,
    env: Map[String, String] = Map(),
    envVars: Seq[String] = Nil
  ) extends McpTransport
}

/**
 * Where a session's agent runs commands, edits files and loads skills.
 */
sealed trait AgentEnvironment

object AgentEnvironment {

  /** No compute or files - an agent that answers or uses remote tools only. */
  case object NoEnvironment extends AgentEnvironment

  /**
   * An OpenAI-hosted sandbox for the session.
   *
   * @param containerSize
   *   `small`, `medium` or `large`
   * @param extra
   *   any other field of the environment (`packages`, `network`, `desktop`, `skills`,
   *   `plugins`, `files`), merged into the request as is
   */
  final case class OpenAIHosted(
    environmentTemplateId: Option[String] = None,
    containerSize: Option[String] = None,
    setupCommands: Seq[String] = Nil,
    env: Map[String, String] = Map(),
    capabilityDirectories: Seq[String] = Nil,
    extra: JsObject = JsObject.empty
  ) extends AgentEnvironment

  /** The application's own machine, connected to the session. */
  final case class SelfHosted(
    workspaceDirectory: String,
    capabilityDirectories: Seq[String] = Nil
  ) extends AgentEnvironment
}
