package io.cequence.openaiscala.perplexity.domain.agent

import play.api.libs.json.{JsObject, JsValue}

/**
 * An Agent API response (`POST /v1/agent`, `GET /v1/agent/{id}`, and the `response` of the
 * lifecycle stream events).
 *
 * @param status
 *   `queued`, `in_progress`, `completed`, `failed`, `incomplete`, `cancelled` or
 *   `requires_action` - kept as a string, new values must not break parsing
 */
final case class AgentResponse(
  id: String,
  createdAt: Long,
  model: String,
  status: String,
  output: Seq[AgentOutputItem] = Nil,
  usage: Option[AgentUsage] = None,
  error: Option[AgentError] = None,
  background: Option[Boolean] = None,
  previousResponseId: Option[String] = None,
  store: Option[Boolean] = None
) {

  /** The text of all assistant messages, in order. */
  def outputText: String =
    output.collect { case m: AgentOutputItem.Message => m.text }.mkString

  /** Every URL citation of the assistant messages. */
  def citations: Seq[AgentAnnotation] =
    output.collect { case m: AgentOutputItem.Message =>
      m.content.flatMap(_.annotations)
    }.flatten

  /** Every web / people search result the run collected. */
  def searchResults: Seq[AgentSearchResult] =
    output.collect {
      case s: AgentOutputItem.SearchResults       => s.results
      case p: AgentOutputItem.PeopleSearchResults => p.results
    }.flatten

  /** The custom function calls the caller must execute (the run then needs their outputs). */
  def functionCalls: Seq[AgentOutputItem.FunctionCall] =
    output.collect { case f: AgentOutputItem.FunctionCall => f }

  def isTerminal: Boolean = AgentResponseStatus.terminal.contains(status)
}

object AgentResponseStatus {
  val queued = "queued"
  val in_progress = "in_progress"
  val completed = "completed"
  val failed = "failed"
  val incomplete = "incomplete"
  val cancelled = "cancelled"
  val requires_action = "requires_action"

  val terminal: Set[String] = Set(completed, failed, incomplete, cancelled)
}

/**
 * An item of [[AgentResponse.output]]; unknown item types arrive as
 * [[AgentOutputItem.Unknown]].
 */
sealed trait AgentOutputItem

object AgentOutputItem {

  final case class Message(
    id: String,
    status: String,
    role: String,
    content: Seq[AgentOutputText]
  ) extends AgentOutputItem {
    def text: String = content.map(_.text).mkString
  }

  final case class SearchResults(
    results: Seq[AgentSearchResult],
    queries: Seq[String] = Nil
  ) extends AgentOutputItem

  final case class FetchUrlResults(contents: Seq[AgentUrlContent]) extends AgentOutputItem

  final case class FinanceResults(
    results: Seq[AgentFinanceResult],
    categories: Seq[String] = Nil,
    tickers: Seq[String] = Nil
  ) extends AgentOutputItem

  final case class PeopleSearchResults(
    results: Seq[AgentSearchResult],
    queries: Seq[String] = Nil
  ) extends AgentOutputItem

  /**
   * A custom function call to execute; answer with [[AgentInputItem.FunctionCallOutput]]. In a
   * STREAM, Perplexity's own tool calls also arrive as `function_call` items (e.g.
   * `search_web`, status `completed`, live-verified 2026-09-25) - they are not in the final
   * response's output and need no answer.
   */
  final case class FunctionCall(
    id: String,
    status: String,
    name: String,
    callId: String,
    arguments: String,
    thoughtSignature: Option[String] = None
  ) extends AgentOutputItem

  /** @param status `completed`, `timed_out` or `failed` */
  final case class SandboxResults(
    status: String,
    code: Option[String] = None,
    stdout: Option[String] = None,
    stderr: Option[String] = None,
    exitCode: Option[Int] = None,
    durationMs: Option[Long] = None
  ) extends AgentOutputItem

  /** The tools discovered on one MCP server; `error` is set when they could not be listed. */
  final case class McpListTools(
    id: String,
    serverLabel: String,
    tools: Seq[AgentMcpToolDef],
    connectorId: Option[String] = None,
    error: Option[String] = None
  ) extends AgentOutputItem

  /** One call executed against an MCP server; `error` is set when it failed. */
  final case class McpCall(
    id: String,
    serverLabel: String,
    name: String,
    arguments: String,
    output: Option[String] = None,
    error: Option[String] = None,
    connectorId: Option[String] = None
  ) extends AgentOutputItem

  /** Anything else (e.g. `tool_search_output`), kept as the raw JSON. */
  final case class Unknown(
    `type`: String,
    json: JsObject
  ) extends AgentOutputItem
}

final case class AgentOutputText(
  text: String,
  annotations: Seq[AgentAnnotation] = Nil,
  `type`: String = "output_text"
)

/** A URL citation of a span of the answer text. */
final case class AgentAnnotation(
  `type`: Option[String] = None,
  url: Option[String] = None,
  title: Option[String] = None,
  startIndex: Option[Int] = None,
  endIndex: Option[Int] = None
)

final case class AgentSearchResult(
  id: Int,
  url: String,
  title: String,
  snippet: String,
  date: Option[String] = None,
  lastUpdated: Option[String] = None,
  source: Option[String] = None
)

final case class AgentUrlContent(
  url: String,
  title: String,
  snippet: String
)

final case class AgentFinanceResult(
  category: String,
  content: String,
  sources: Seq[String] = Nil,
  tickers: Seq[String] = Nil
)

final case class AgentMcpToolDef(
  name: String,
  inputSchema: JsValue,
  description: Option[String] = None
)

/**
 * Token usage and cost. `inputTokensDetails` / `toolCallsDetails` are kept as raw JSON (their
 * shape is not specified).
 */
final case class AgentUsage(
  inputTokens: Int,
  outputTokens: Int,
  totalTokens: Int,
  cost: Option[AgentCost] = None,
  inputTokensDetails: Option[JsObject] = None,
  toolCallsDetails: Option[JsObject] = None
)

/** Cost breakdown in `currency` (USD). */
final case class AgentCost(
  currency: String,
  inputCost: Double,
  outputCost: Double,
  totalCost: Double,
  cacheCreationCost: Option[Double] = None,
  cacheReadCost: Option[Double] = None,
  toolCallsCost: Option[Double] = None
)

final case class AgentError(
  message: String,
  code: Option[String] = None,
  `type`: Option[String] = None
)

/** The acknowledgement of `POST /v1/agent/{id}/cancel` (status `cancelling`). */
final case class AgentCancelResponse(
  responseId: String,
  status: String
)

/** A file a response shared from its sandbox (`GET /v1/agent/{id}/files`). */
final case class AgentResponseFile(
  id: String,
  filename: String,
  bytes: Long,
  createdAt: Long
)

/** A model of the Agent API (`GET /v1/models`); `id` is `provider/model`. */
final case class AgentModel(
  id: String,
  created: Long,
  ownedBy: String
)
