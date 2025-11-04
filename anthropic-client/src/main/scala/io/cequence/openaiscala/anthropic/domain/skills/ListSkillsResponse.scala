package io.cequence.openaiscala.anthropic.domain.skills

/**
 * Response from listing skills.
 *
 * @param data
 *   List of skills.
 * @param hasMore
 *   Whether there are more results available. If true, there are additional results that can
 *   be fetched using the nextPage token.
 * @param nextPage
 *   Token for fetching the next page of results. If null, there are no more results available.
 *   Pass this value to the page parameter in the next request to get the next page.
 */
case class ListSkillsResponse(
  data: Seq[Skill],
  hasMore: Boolean,
  nextPage: Option[String]
)
