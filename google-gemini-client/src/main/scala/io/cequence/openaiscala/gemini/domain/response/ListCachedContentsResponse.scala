package io.cequence.openaiscala.gemini.domain.response

import io.cequence.openaiscala.gemini.domain.CachedContent

/**
 * Response from ListCachedContents containing a paginated list.
 *
 * @param cachedContents
 *   list of cached contents.
 * @param nextPageToken
 *   A token, which can be sent as pageToken to retrieve the next page. If this field is
 *   omitted, there are no more pages.
 */
case class ListCachedContentsResponse(
  cachedContents: Seq[CachedContent] = Nil,
  nextPageToken: Option[String] = None
)
