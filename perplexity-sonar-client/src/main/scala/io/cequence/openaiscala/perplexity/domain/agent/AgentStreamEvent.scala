package io.cequence.openaiscala.perplexity.domain.agent

import play.api.libs.json.JsObject

/**
 * A server-sent event of a streamed Agent API response, discriminated by `type`. Every event
 * carries a `sequenceNumber`, the cursor for resuming a dropped background stream
 * (`resumeAgentResponseStream(id, startingAfter = Some(sequenceNumber))`). Unknown event types
 * arrive as [[AgentStreamEvent.Unknown]].
 */
sealed trait AgentStreamEvent {
  def sequenceNumber: Int
}

object AgentStreamEvent {

  /** `response.created` - the initial response object. */
  final case class ResponseCreated(
    sequenceNumber: Int,
    response: Option[AgentResponse]
  ) extends AgentStreamEvent

  /** `response.in_progress` */
  final case class ResponseInProgress(
    sequenceNumber: Int,
    response: Option[AgentResponse]
  ) extends AgentStreamEvent

  /** `response.completed` - the full response (output, usage). */
  final case class ResponseCompleted(
    sequenceNumber: Int,
    response: Option[AgentResponse]
  ) extends AgentStreamEvent

  /** `response.failed` */
  final case class ResponseFailed(
    sequenceNumber: Int,
    error: AgentError
  ) extends AgentStreamEvent

  /** `response.output_item.added` - a message or tool item starts. */
  final case class OutputItemAdded(
    sequenceNumber: Int,
    outputIndex: Int,
    item: AgentOutputItem
  ) extends AgentStreamEvent

  /** `response.output_item.done` - a message or tool item is complete. */
  final case class OutputItemDone(
    sequenceNumber: Int,
    outputIndex: Int,
    item: AgentOutputItem
  ) extends AgentStreamEvent

  /** `response.output_text.delta` - a piece of the answer text. */
  final case class OutputTextDelta(
    sequenceNumber: Int,
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    delta: String
  ) extends AgentStreamEvent

  /** `response.output_text.done` - the final text of a content part. */
  final case class OutputTextDone(
    sequenceNumber: Int,
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    text: String
  ) extends AgentStreamEvent

  /** `response.reasoning.started` */
  final case class ReasoningStarted(
    sequenceNumber: Int,
    thought: Option[String] = None
  ) extends AgentStreamEvent

  /** `response.reasoning.search_queries` */
  final case class SearchQueries(
    sequenceNumber: Int,
    queries: Seq[String],
    thought: Option[String] = None
  ) extends AgentStreamEvent

  /** `response.reasoning.search_results` */
  final case class SearchResults(
    sequenceNumber: Int,
    results: Seq[AgentSearchResult],
    thought: Option[String] = None,
    usage: Option[AgentUsage] = None
  ) extends AgentStreamEvent

  /** `response.reasoning.fetch_url_queries` */
  final case class FetchUrlQueries(
    sequenceNumber: Int,
    urls: Seq[String],
    thought: Option[String] = None
  ) extends AgentStreamEvent

  /** `response.reasoning.fetch_url_results` */
  final case class FetchUrlResults(
    sequenceNumber: Int,
    contents: Seq[AgentUrlContent],
    thought: Option[String] = None
  ) extends AgentStreamEvent

  /** `response.reasoning.stopped` */
  final case class ReasoningStopped(
    sequenceNumber: Int,
    thought: Option[String] = None
  ) extends AgentStreamEvent

  /** Any other event type, kept as the raw JSON. */
  final case class Unknown(
    `type`: String,
    sequenceNumber: Int,
    json: JsObject
  ) extends AgentStreamEvent
}
