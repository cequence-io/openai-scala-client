package io.cequence.openaiscala.gemini.domain.response

import io.cequence.openaiscala.gemini.domain.Model

/**
 * Response from ListModel containing a paginated list of Models.
 *
 * @param models
 *   the returned Models
 * @param nextPageToken
 *   A token, which can be sent as pageToken to retrieve the next page. If this field is
 *   omitted, there are no more pages.
 */
case class ListModelsResponse(
  models: Seq[Model],
  nextPageToken: Option[String]
)
