package io.cequence.openaiscala.anthropic.domain.tools

/**
 * Web search tool for searching the web. Name is always "web_search". Type is
 * "web_search_20250305".
 *
 * @param allowedDomains
 *   If provided, only these domains will be included in results. Cannot be used alongside
 *   blockedDomains.
 * @param blockedDomains
 *   If provided, these domains will never appear in results. Cannot be used alongside
 *   allowedDomains.
 * @param maxUses
 *   Maximum number of times the tool can be used in the API request (> 0).
 * @param userLocation
 *   Parameters for the user's location. Used to provide more relevant search results.
 */
case class WebSearchTool(
  allowedDomains: Seq[String] = Nil,
  blockedDomains: Seq[String] = Nil,
  maxUses: Option[Int] = None,
  userLocation: Option[UserLocation] = None
) extends Tool {
  override val name: String = "web_search"
  override val `type`: String = "web_search_20250305"
}

/**
 * User location for web search.
 *
 * @param `type`
 *   Type of location, always "approximate".
 * @param city
 *   The city of the user (1-255 characters).
 * @param country
 *   The two letter ISO country code of the user.
 * @param region
 *   The region of the user (1-255 characters).
 * @param timezone
 *   The IANA timezone of the user (1-255 characters).
 */
case class UserLocation(
  `type`: String = "approximate",
  city: Option[String] = None,
  country: Option[String] = None,
  region: Option[String] = None,
  timezone: Option[String] = None
)
