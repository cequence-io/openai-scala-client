package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.wsclient.domain.NamedEnumValue

/**
 * This tool searches the web for relevant results to use in a response. Also serves as web
 * search preview.
 *
 * @param filters
 *   Optional filters for the search, including allowed domains.
 * @param searchContextSize
 *   Optional high level guidance for the amount of context window space to use for the search.
 *   One of low, medium, or high. Defaults to medium.
 * @param userLocation
 *   Optional approximate location parameters for the search.
 * @param `type`
 *   The type of web search tool to use. One of web_search, web_search_2025_08_26,
 *   web_search_preview, or web_search_preview_2025_03_11.
 */
case class WebSearchTool(
  filters: Option[WebSearchFilters] = None,
  searchContextSize: Option[String] = None,
  userLocation: Option[WebSearchUserLocation] = None,
  override val `type`: WebSearchType = WebSearchType.WebSearch
) extends Tool

/**
 * Filters for web search.
 *
 * @param allowedDomains
 *   Allowed domains for the search. If not provided, all domains are allowed. Subdomains of
 *   the provided domains are allowed as well.
 */
case class WebSearchFilters(
  allowedDomains: Seq[String] = Nil
)

/**
 * Approximate location parameters for web search.
 *
 * @param city
 *   Optional free text input for the city of the user.
 * @param country
 *   Optional two-letter ISO country code of the user.
 * @param region
 *   Optional free text input for the region of the user.
 * @param timezone
 *   Optional IANA timezone of the user.
 */
case class WebSearchUserLocation(
  city: Option[String] = None,
  country: Option[String] = None,
  region: Option[String] = None,
  timezone: Option[String] = None
) {
  // TODO: compare with ApproximateLocation
  val `type`: String = "approximate"
}

sealed abstract class WebSearchType(value: String) extends NamedEnumValue(value)

object WebSearchType {
  case object WebSearch extends WebSearchType("web_search")
  case object WebSearch20250826 extends WebSearchType("web_search_2025_08_26")
  case object WebSearchPreview extends WebSearchType("web_search_preview")
  case object WebSearchPreview20250311 extends WebSearchType("web_search_preview_2025_03_11")

  def values = Seq(
    WebSearch,
    WebSearch20250826,
    WebSearchPreview,
    WebSearchPreview20250311
  )
}
