package io.cequence.openaiscala.perplexity.domain.agent

import io.cequence.openaiscala.domain.JsonSchema
import play.api.libs.json.JsObject

/**
 * A tool available to an Agent API run (`tools` of `POST /v1/agent`). Built-in tools run on
 * Perplexity's side; [[AgentTool.Function]] calls come back as `function_call` output items
 * for the caller to execute.
 */
sealed trait AgentTool

object AgentTool {

  /**
   * Web search (`type: web_search`).
   *
   * @param searchType
   *   `web` (standard) or `fast` (lower-latency Fast Search)
   * @param searchContextSize
   *   `low`, `medium` or `high`; explicit `maxTokens` / `maxTokensPerPage` override it
   * @param maxResults
   *   results collected per call, 1 to 50
   */
  final case class WebSearch(
    filters: Option[WebSearchFilters] = None,
    searchType: Option[String] = None,
    searchContextSize: Option[String] = None,
    maxResults: Option[Int] = None,
    maxTokens: Option[Int] = None,
    maxTokensPerPage: Option[Int] = None,
    userLocation: Option[UserLocation] = None
  ) extends AgentTool

  /** Fetches and extracts the content of URLs (`type: fetch_url`). */
  final case class FetchUrl(maxUrls: Option[Int] = None) extends AgentTool

  /** Structured financial and market data (`type: finance_search`). */
  case object FinanceSearch extends AgentTool

  /** Searches for professionals and employees (`type: people_search`). */
  case object PeopleSearch extends AgentTool

  /** Runs code in an isolated container (`type: sandbox`). */
  case object Sandbox extends AgentTool

  /**
   * A custom function (`type: function`) the caller executes; its calls arrive as
   * [[AgentOutputItem.FunctionCall]] and go back as [[AgentInputItem.FunctionCallOutput]].
   */
  final case class Function(
    name: String,
    description: Option[String] = None,
    parameters: Option[JsonSchema] = None,
    strict: Option[Boolean] = None
  ) extends AgentTool

  /**
   * A remote MCP server (`type: mcp`) - a Streamable HTTP endpoint (the legacy SSE transport
   * is not supported).
   *
   * @param serverLabel
   *   unique per request, `^[a-zA-Z0-9_-]{1,64}$`; namespaces the server's tools
   * @param authorization
   *   raw access token passed to the server
   * @param deferLoading
   *   keep the discovered tool definitions out of the initial context and let the model load
   *   them on demand
   */
  final case class Mcp(
    serverLabel: String,
    serverUrl: String,
    authorization: Option[String] = None,
    headers: Map[String, String] = Map.empty,
    allowedTools: Seq[String] = Nil,
    deferLoading: Option[Boolean] = None
  ) extends AgentTool

  /** A Perplexity-managed connector of the API organization (`type: connector`). */
  final case class Connector(
    id: String,
    serverLabel: String,
    serverDescription: Option[String] = None,
    allowedTools: Seq[String] = Nil
  ) extends AgentTool

  /** Any other tool, sent as the given JSON object (for tool types not modelled here). */
  final case class Raw(json: JsObject) extends AgentTool
}

/**
 * Domain and date filters of [[AgentTool.WebSearch]]. Dates are `MM/DD/YYYY`; `recency` is one
 * of `hour`, `day`, `week`, `month`, `year`.
 */
final case class WebSearchFilters(
  searchDomainFilter: Seq[String] = Nil,
  searchRecencyFilter: Option[String] = None,
  searchAfterDateFilter: Option[String] = None,
  searchBeforeDateFilter: Option[String] = None,
  lastUpdatedAfterFilter: Option[String] = None,
  lastUpdatedBeforeFilter: Option[String] = None
)

/** The user's location for search personalization; `country` is ISO 3166-1 alpha-2. */
final case class UserLocation(
  city: Option[String] = None,
  region: Option[String] = None,
  country: Option[String] = None,
  latitude: Option[Double] = None,
  longitude: Option[Double] = None
)

/** A skill the model may load on demand (`skills` of `POST /v1/agent`). */
sealed trait AgentSkill

object AgentSkill {

  /** `office`, `office/docx`, `office/pdf`, `office/pptx` or `office/xlsx`. */
  final case class Builtin(name: String) extends AgentSkill

  /** A request-scoped skill (lowercase hyphenated `name`). */
  final case class Inline(
    name: String,
    description: String,
    instructions: String
  ) extends AgentSkill

  /** An organization-owned skill (`skill_<id>`); `version` defaults to `latest`. */
  final case class Custom(
    id: String,
    version: Option[String] = None
  ) extends AgentSkill
}

/**
 * A saved, versioned configuration to run with (`profile`); `version` defaults to `latest`.
 */
final case class AgentProfileReference(
  id: String,
  version: Option[String] = None
)
