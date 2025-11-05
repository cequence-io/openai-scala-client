package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.openaiscala.domain.responsesapi.{Input, ModelStatus}
import io.cequence.openaiscala.domain.responsesapi.Output

/**
 * The results of a web search tool call.
 *
 * @param action
 *   An object describing the specific action taken in this web search call
 * @param id
 *   The unique ID of the web search tool call
 * @param status
 *   The status of the web search tool call
 */
final case class WebSearchToolCall(
  action: WebSearchAction,
  id: String,
  status: ModelStatus
) extends ToolCall
    with Input
    with Output {
  override val `type`: String = "web_search_call"
}

/**
 * Represents the hierarchy of web search actions.
 */
sealed trait WebSearchAction {
  val `type`: String
}

object WebSearchAction {

  /**
   * A search action - Performs a web search query.
   *
   * @param query
   *   The search query (optional)
   * @param sources
   *   The sources used in the search
   */
  final case class Search(
    query: Option[String] = None,
    sources: Seq[WebSearchSource] = Nil
  ) extends WebSearchAction {
    val `type`: String = "search"
  }

  /**
   * An open page action - Opens a specific URL from search results.
   *
   * @param url
   *   The URL opened by the model
   */
  final case class OpenPage(
    url: String
  ) extends WebSearchAction {
    val `type`: String = "open_page"
  }

  /**
   * A find action - Searches for a pattern within a loaded page.
   *
   * @param pattern
   *   The pattern or text to search for within the page
   * @param url
   *   The URL of the page searched for the pattern
   */
  final case class Find(
    pattern: String,
    url: String
  ) extends WebSearchAction {
    val `type`: String = "find"
  }
}

/**
 * A source used in a web search.
 *
 * @param url
 *   The URL of the source
 */
final case class WebSearchSource(
  url: String
) {
  val `type`: String = "url"
}
