package io.cequence.openaiscala.domain

sealed trait AssistantTool

sealed trait ChatCompletionTool

/**
 * Provider-neutral tools accepted by `createChatToolCompletion` /
 * `createChatToolCompletionStreamed` next to [[AssistantTool.FunctionTool]]: every adapter
 * maps them onto its provider's own feature (or fails loudly where the provider has none) -
 * see the README's "Provider-neutral MCP servers and skills" section for the per-provider
 * mapping.
 */
object ChatCompletionTool {

  /**
   * A remote MCP server the PROVIDER connects to and whose tools the model calls without a
   * client-side tool loop: OpenAI's Responses `mcp` tool, Anthropic's MCP connector
   * (`mcp_servers`), Gemini's `mcpServers` tool. Its calls arrive on the typed stream as
   * server-side `ToolCallStart` / `ToolCall` and their results as `ToolResult`.
   *
   * @param name
   *   the server's label - Gemini prefixes its tool names with it, so keep it identifier-like
   * @param url
   *   streamable-HTTP MCP endpoint
   * @param authorizationToken
   *   bearer token; sent as the provider's dedicated field, or as an `Authorization` header
   *   where there is none (Gemini)
   * @param headers
   *   further HTTP headers for the server (e.g. `x-api-key`); Anthropic's connector cannot
   *   send any - the Anthropic adapter fails loudly rather than dropping them
   * @param allowedTools
   *   restrict the server's tools to these names (`Nil` = all); Gemini cannot restrict - the
   *   Gemini adapter warns and offers all
   * @param description
   *   what the server is for (OpenAI only)
   * @param timeout
   *   per-request timeout for the server (Gemini only)
   * @param requireApproval
   *   ask before each call (OpenAI only; the adapters default to never asking, as the
   *   chat-completion shape has no way to answer an approval request)
   */
  final case class MCPServerTool(
    name: String,
    url: String,
    authorizationToken: Option[String] = None,
    headers: Map[String, String] = Map.empty,
    allowedTools: Seq[String] = Nil,
    description: Option[String] = None,
    timeout: Option[scala.concurrent.duration.FiniteDuration] = None,
    requireApproval: Boolean = false
  ) extends ChatCompletionTool

  /**
   * An agent skill (a `SKILL.md` bundle) the model can use in the PROVIDER's sandbox:
   * Anthropic loads it into the code-execution container (`container.skills`, the code
   * execution tool is added automatically), OpenAI into the hosted `shell` tool's
   * `container_auto` environment (Responses API, GPT-6). Gemini and Vertex AI have no skills -
   * their adapters fail loudly.
   *
   * @param skillId
   *   the provider's id of the skill - an uploaded skill's id, or for [[SkillSource.Provider]]
   *   a built-in one (Anthropic: `pptx`, `xlsx`, `docx`, `pdf`)
   * @param version
   *   a version id / number, or `latest`; the provider's default when unset
   * @param source
   *   built-in ([[SkillSource.Provider]], Anthropic only) or uploaded ([[SkillSource.Custom]])
   */
  final case class SkillTool(
    skillId: String,
    version: Option[String] = None,
    source: SkillSource = SkillSource.Custom
  ) extends ChatCompletionTool

  sealed trait SkillSource

  object SkillSource {

    /** A skill the provider ships (Anthropic's `anthropic` skills). */
    case object Provider extends SkillSource

    /** A skill uploaded to the provider (Anthropic `custom`, OpenAI skills). */
    case object Custom extends SkillSource
  }
}

object AssistantTool {
  case object CodeInterpreterTool extends AssistantTool

  final case class FileSearchTool(maxNumResults: Option[Int] = None) extends AssistantTool

  case class FunctionTool(
    // The name of the function to be called.
    // Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 64.
    name: String,

    // The description of what the function does.
    description: Option[String] = None,

    // The parameters the functions accepts, described as a JSON Schema object.
    // See the guide for examples, and the JSON Schema reference for documentation about the format.
    parameters: JsonSchema = JsonSchema.Object(Nil),

    //  Whether to enable strict schema adherence when generating the function call. If set to true, the model will
    //  follow the exact schema defined in the parameters field. Only a subset of JSON Schema is supported when strict
    //  is true. Learn more about Structured Outputs in the function calling guide.
    strict: Option[Boolean] = None
  ) extends AssistantTool
      with ChatCompletionTool
}
