package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.openaiscala.domain.responsesapi.{Input, ModelStatus}
import io.cequence.openaiscala.domain.responsesapi.Output

/**
 * The results of a web search tool call.
 *
 * @param id
 *   The unique ID of the web search tool call
 * @param status
 *   The status of the web search tool call
 */
final case class WebSearchToolCall(
  id: String,
  status: ModelStatus
) extends ToolCall
    with Input
    with Output {
  override val `type`: String = "web_search_call"
}
