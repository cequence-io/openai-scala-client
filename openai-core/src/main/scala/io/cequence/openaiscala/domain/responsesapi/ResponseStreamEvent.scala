package io.cequence.openaiscala.domain.responsesapi

import play.api.libs.json.JsValue

/**
 * A server-sent event of a streamed Responses API call (`stream = true`), dispatched on the
 * JSON `type` field. The commonly consumed events are modeled with their fields; every event
 * this client does not model (yet) arrives as [[ResponseStreamEvent.UnknownEvent]] with the
 * raw JSON, and the tool-call lifecycle notifications (`response.web_search_call.searching`,
 * `response.code_interpreter_call.interpreting`, ...) as
 * [[ResponseStreamEvent.ToolCallStatus]].
 *
 * @see
 *   <a href="https://platform.openai.com/docs/api-reference/responses-streaming">OpenAI
 *   Doc</a>
 */
sealed abstract class ResponseStreamEvent(val eventType: String)

object ResponseStreamEvent {

  // ---- response lifecycle (the `response` object is kept raw; the fields a consumer needs
  // are lifted out) ----

  final case class ResponseCreated(
    responseId: String,
    model: String,
    raw: JsValue
  ) extends ResponseStreamEvent("response.created")

  final case class ResponseInProgress(
    responseId: String,
    raw: JsValue
  ) extends ResponseStreamEvent("response.in_progress")

  final case class ResponseQueued(
    responseId: String,
    raw: JsValue
  ) extends ResponseStreamEvent("response.queued")

  final case class ResponseCompleted(
    responseId: String,
    usage: Option[UsageInfo],
    raw: JsValue
  ) extends ResponseStreamEvent("response.completed")

  /** `reason` is `incomplete_details.reason`, e.g. `max_output_tokens` or `content_filter`. */
  final case class ResponseIncomplete(
    responseId: String,
    reason: Option[String],
    usage: Option[UsageInfo],
    raw: JsValue
  ) extends ResponseStreamEvent("response.incomplete")

  final case class ResponseFailed(
    responseId: String,
    error: Option[ResponseError],
    raw: JsValue
  ) extends ResponseStreamEvent("response.failed")

  // ---- output items ----

  /**
   * A new output item (message, reasoning, function_call, web_search_call,
   * code_interpreter_call, mcp_call, image_generation_call, ...). `item` is the parsed
   * [[Output]] when this client can parse it; `raw` is the whole event JSON (`raw \ "item"` is
   * always available).
   */
  final case class OutputItemAdded(
    outputIndex: Int,
    itemType: String,
    itemId: Option[String],
    item: Option[Output],
    raw: JsValue
  ) extends ResponseStreamEvent("response.output_item.added")

  final case class OutputItemDone(
    outputIndex: Int,
    itemType: String,
    itemId: Option[String],
    item: Option[Output],
    raw: JsValue
  ) extends ResponseStreamEvent("response.output_item.done")

  // ---- text / refusal / reasoning deltas ----

  final case class OutputTextDelta(
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    delta: String
  ) extends ResponseStreamEvent("response.output_text.delta")

  final case class OutputTextDone(
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    text: String
  ) extends ResponseStreamEvent("response.output_text.done")

  final case class RefusalDelta(
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    delta: String
  ) extends ResponseStreamEvent("response.refusal.delta")

  final case class RefusalDone(
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    refusal: String
  ) extends ResponseStreamEvent("response.refusal.done")

  final case class ReasoningSummaryTextDelta(
    itemId: String,
    outputIndex: Int,
    summaryIndex: Int,
    delta: String
  ) extends ResponseStreamEvent("response.reasoning_summary_text.delta")

  final case class ReasoningSummaryTextDone(
    itemId: String,
    outputIndex: Int,
    summaryIndex: Int,
    text: String
  ) extends ResponseStreamEvent("response.reasoning_summary_text.done")

  final case class ReasoningTextDelta(
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    delta: String
  ) extends ResponseStreamEvent("response.reasoning_text.delta")

  final case class ReasoningTextDone(
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    text: String
  ) extends ResponseStreamEvent("response.reasoning_text.done")

  // ---- tool calls ----

  final case class FunctionCallArgumentsDelta(
    itemId: String,
    outputIndex: Int,
    delta: String
  ) extends ResponseStreamEvent("response.function_call_arguments.delta")

  final case class FunctionCallArgumentsDone(
    itemId: String,
    outputIndex: Int,
    arguments: String
  ) extends ResponseStreamEvent("response.function_call_arguments.done")

  final case class McpCallArgumentsDelta(
    itemId: String,
    outputIndex: Int,
    delta: String
  ) extends ResponseStreamEvent("response.mcp_call_arguments.delta")

  final case class McpCallArgumentsDone(
    itemId: String,
    outputIndex: Int,
    arguments: String
  ) extends ResponseStreamEvent("response.mcp_call_arguments.done")

  final case class CodeInterpreterCodeDelta(
    itemId: String,
    outputIndex: Int,
    delta: String
  ) extends ResponseStreamEvent("response.code_interpreter_call_code.delta")

  final case class CodeInterpreterCodeDone(
    itemId: String,
    outputIndex: Int,
    code: String
  ) extends ResponseStreamEvent("response.code_interpreter_call_code.done")

  final case class ImageGenerationPartialImage(
    itemId: String,
    outputIndex: Int,
    partialImageIndex: Int,
    partialImageB64: String
  ) extends ResponseStreamEvent("response.image_generation_call.partial_image")

  /**
   * Lifecycle notification of a server-side tool call: `response.web_search_call.in_progress /
   * searching / completed`, `response.code_interpreter_call.in_progress / interpreting /
   * completed`, `response.file_search_call.*`, `response.mcp_call.*`,
   * `response.mcp_list_tools.*`, `response.image_generation_call.generating / in_progress /
   * completed`.
   */
  final case class ToolCallStatus(
    override val eventType: String,
    itemId: String,
    outputIndex: Int,
    raw: JsValue
  ) extends ResponseStreamEvent(eventType)

  // ---- annotations ----

  final case class OutputTextAnnotationAdded(
    itemId: String,
    outputIndex: Int,
    contentIndex: Int,
    annotationIndex: Int,
    annotation: Option[Annotation],
    raw: JsValue
  ) extends ResponseStreamEvent("response.output_text.annotation.added")

  // ---- errors and everything else ----

  final case class ErrorEvent(
    code: Option[String],
    message: String,
    param: Option[String],
    raw: JsValue
  ) extends ResponseStreamEvent("error")

  /** Forward-compatible fallback for an event type this client doesn't model yet. */
  final case class UnknownEvent(
    override val eventType: String,
    raw: JsValue
  ) extends ResponseStreamEvent(eventType)
}
