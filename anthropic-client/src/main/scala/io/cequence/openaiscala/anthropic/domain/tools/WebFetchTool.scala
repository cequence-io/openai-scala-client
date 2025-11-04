package io.cequence.openaiscala.anthropic.domain.tools

/**
 * Web fetch tool for fetching web pages. Name is always "web_fetch". Type is
 * "web_fetch_20250910".
 *
 * @param allowedDomains
 *   List of domains to allow fetching from.
 * @param blockedDomains
 *   List of domains to block fetching from.
 * @param citations
 *   Citations configuration for fetched documents. Citations are disabled by default.
 * @param maxContentTokens
 *   Maximum number of tokens used by including web page text content in the context. The limit
 *   is approximate and does not apply to binary content such as PDFs (> 0).
 * @param maxUses
 *   Maximum number of times the tool can be used in the API request (> 0).
 */
case class WebFetchTool(
  allowedDomains: Seq[String] = Nil,
  blockedDomains: Seq[String] = Nil,
  citations: Option[Citations] = None,
  maxContentTokens: Option[Int] = None,
  maxUses: Option[Int] = None
) extends Tool {
  override val name: String = "web_fetch"
  override val `type`: String = "web_fetch_20250910"
}

/**
 * Citations configuration for web fetch tool.
 */
case class Citations(
  enabled: Boolean = true
)
