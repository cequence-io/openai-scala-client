package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.wsclient.domain.NamedEnumValue

/**
 * This tool searches the web for relevant results to use in a response.
 *
 * @param searchContextSize
 *   Optional high level guidance for the amount of context window space to use for the search.
 * @param userLocation
 *   Optional approximate location parameters for the search.
 * @param webSearchType
 *   The type of web search tool to use.
 */
case class WebSearchTool(
  searchContextSize: Option[String] = None,
  userLocation: Option[WebSearchUserLocation] = None,
  `type`: WebSearchType = WebSearchType.Preview
) extends Tool {
  override def typeString: String = `type`.value
}

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
  val `type`: String = "approximate" // TODO: compare with ApproximateLocation
}

sealed abstract class WebSearchType(value: String) extends NamedEnumValue(value)

object WebSearchType {
  case object Preview extends WebSearchType("web_search_preview")
  case object Preview20250311 extends WebSearchType("web_search_preview_2025_03_11")

  def values = Seq(Preview, Preview20250311)
}
