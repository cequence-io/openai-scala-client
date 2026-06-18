package io.cequence.openaiscala.anthropic.domain.managedagents

/**
 * Uniform page wrapper for Managed Agents list endpoints (`{ data, next_page }`).
 *
 * @param data
 *   The page of items.
 * @param nextPage
 *   Opaque cursor for the next page, or `None` when there are no more results.
 */
final case class PagedResponse[T](
  data: Seq[T],
  nextPage: Option[String] = None
)
