package io.cequence.openaiscala.anthropic.domain.skills

/**
 * Response from listing skill versions.
 *
 * @param data
 *   List of skill versions.
 * @param hasMore
 *   Indicates if there are more results in the requested page direction.
 * @param nextPage
 *   Token to provide as page in the subsequent request to retrieve the next page of data.
 */
case class ListSkillVersionsResponse(
  data: Seq[SkillVersion],
  hasMore: Boolean,
  nextPage: Option[String]
)
