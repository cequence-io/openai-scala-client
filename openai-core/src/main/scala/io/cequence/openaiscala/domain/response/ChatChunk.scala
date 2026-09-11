package io.cequence.openaiscala.domain.response

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import io.cequence.openaiscala.domain.FunctionCallSpec
import io.cequence.wsclient.domain.EnumValue
import play.api.libs.json.JsValue

import scala.concurrent.Future

/**
 * One provider-neutral event of a streamed chat completion, as returned by
 * `createChatToolCompletionStreamed` / `createChatCompletionStreamedTyped`.
 *
 * The same sealed hierarchy is produced for OpenAI (and OpenAI-compatible providers such as
 * DeepSeek, Grok, Groq, Cerebras, Mistral), Anthropic (direct and Bedrock) and Google Gemini,
 * so a consumer can pattern match on [[ChatChunk.Text]], [[ChatChunk.Thinking]],
 * [[ChatChunk.ToolCall]], [[ChatChunk.ToolResult]], ... regardless of the provider. Only the
 * first choice / candidate is mapped; further choices arrive as [[ChatChunk.Other]].
 *
 * Two layers describe tool activity:
 *   - the '''tool layer''' reports every tool invocation uniformly, client-side or
 *     server-side: [[ChatChunk.ToolCallStart]] as soon as the call id and tool name are known,
 *     zero or more [[ChatChunk.ToolCallDelta]] argument fragments, exactly one
 *     [[ChatChunk.ToolCall]] with the assembled JSON arguments once the call is complete
 *     (Gemini sends complete calls, so it emits Start + ToolCall without deltas), and
 *     [[ChatChunk.ToolResult]] for provider-executed tools whose result arrives in the same
 *     stream;
 *   - the '''semantic layer''' adds typed views of the common provider-executed capabilities,
 *     emitted ''in addition'' to the tool-layer chunks with the same `callId`:
 *     [[ChatChunk.CodeExecution]] / [[ChatChunk.CodeExecutionResult]], [[ChatChunk.WebSearch]]
 *     / [[ChatChunk.WebSearchResult]], plus [[ChatChunk.Image]], [[ChatChunk.Citation]] and
 *     [[ChatChunk.Refusal]]. Consumers pick the layer they want; [[AssembledChatCompletion]]
 *     keeps them in separate collections, so nothing is double counted.
 *
 * Use [[ChatChunk.ChatChunkSourceOps.assembled]] to fold a stream into an
 * [[AssembledChatCompletion]].
 *
 * Anything a provider sends that is not modeled here is passed through as [[ChatChunk.Other]]
 * (never dropped, never an error), so the hierarchy is forward compatible.
 */
sealed trait ChatChunk

object ChatChunk {

  /**
   * First chunk of every stream: the response id and model (OpenAI chunk id / model, Anthropic
   * `message_start`, Gemini `modelVersion`).
   */
  final case class Start(
    id: String,
    model: String
  ) extends ChatChunk

  /** A fragment of the visible answer text. */
  final case class Text(text: String) extends ChatChunk

  /**
   * A fragment of the model's reasoning: Anthropic `thinking_delta`, OpenAI-compatible
   * `delta.reasoning_content` (DeepSeek, Grok, Mistral, Fireworks) / `delta.reasoning` (Groq),
   * Gemini `thought = true` text parts (thought summaries).
   */
  final case class Thinking(text: String) extends ChatChunk

  /**
   * Opaque signature of a thinking block that must be echoed back verbatim in multi-turn tool
   * loops (Anthropic `signature_delta`, Gemini `thoughtSignature`). `callId` is set when the
   * signature rides on a function call (Gemini / Vertex AI attach `thoughtSignature` to the
   * `functionCall` part) so it can be paired with the [[ToolCall]] it belongs to.
   */
  final case class ThinkingSignature(
    signature: String,
    callId: Option[String] = None
  ) extends ChatChunk

  /** Anthropic `redacted_thinking` block - opaque data to echo back unchanged. */
  final case class RedactedThinking(data: String) extends ChatChunk

  /**
   * A tool call has begun: the call id and tool name are known; its JSON arguments follow as
   * [[ToolCallDelta]]s and the complete call as [[ToolCall]].
   *
   * @param index
   *   ordinal of the call within this response (equals OpenAI `tool_calls[].index`)
   * @param serverSide
   *   true for provider-executed tools whose result arrives in the same stream as a
   *   [[ToolResult]] (Anthropic `server_tool_use` / `mcp_tool_use`, Gemini code execution);
   *   false for client-side function calls the caller has to execute
   */
  final case class ToolCallStart(
    index: Int,
    callId: String,
    toolName: String,
    serverSide: Boolean
  ) extends ChatChunk

  /** A streamed fragment of a tool call's JSON arguments - concatenate per `index`. */
  final case class ToolCallDelta(
    index: Int,
    argumentsFragment: String
  ) extends ChatChunk

  /**
   * A complete tool call. Emitted exactly once per call by every provider.
   *
   * @param arguments
   *   the JSON-object arguments text (`"{}"` when the provider streamed none)
   */
  final case class ToolCall(
    index: Int,
    callId: String,
    toolName: String,
    arguments: String,
    serverSide: Boolean
  ) extends ChatChunk {
    def toFunctionCallSpec: FunctionCallSpec = FunctionCallSpec(toolName, arguments)
  }

  /**
   * The result of a provider-executed (server-side) tool: Anthropic web_search / web_fetch /
   * code_execution / bash / text_editor / MCP result blocks, Gemini `codeExecutionResult`.
   *
   * @param callId
   *   the id of the [[ToolCall]] this result answers
   * @param content
   *   the provider-shaped result payload (Anthropic content block JSON, Gemini `{"outcome":
   *   ..., "output": ...}`)
   * @param text
   *   a best-effort textual view of the result (stdout, fetched document text, MCP text items,
   *   "title - url" lines for web search results), if any
   */
  final case class ToolResult(
    callId: String,
    toolName: String,
    content: JsValue,
    text: Option[String],
    isError: Boolean
  ) extends ChatChunk

  /**
   * Server-side code execution (Anthropic `code_execution` / `bash_code_execution`, Gemini
   * `executableCode`, OpenAI `code_interpreter_call`) - the code about to run. Emitted in
   * addition to the tool-layer [[ToolCall]] with the same `callId`.
   */
  final case class CodeExecution(
    callId: String,
    language: Option[String],
    code: String
  ) extends ChatChunk

  /**
   * The result of a server-side code execution (Gemini `codeExecutionResult`, Anthropic
   * `code_execution_tool_result` / `bash_code_execution_tool_result`, OpenAI code interpreter
   * outputs). Emitted in addition to the tool-layer [[ToolResult]].
   */
  final case class CodeExecutionResult(
    callId: String,
    output: Option[String],
    isError: Boolean,
    raw: JsValue
  ) extends ChatChunk

  /**
   * A server-side web search (Anthropic `web_search` server tool, OpenAI `web_search_call`,
   * Gemini Google Search grounding queries). Emitted in addition to the tool-layer
   * [[ToolCall]].
   */
  final case class WebSearch(
    callId: String,
    queries: Seq[String]
  ) extends ChatChunk

  /**
   * The results of a server-side web search (Anthropic `web_search_tool_result`, Gemini
   * grounding chunks, OpenAI web-search sources when provided). Emitted in addition to the
   * tool-layer [[ToolResult]].
   */
  final case class WebSearchResult(
    callId: String,
    results: Seq[WebSearchResultItem],
    raw: JsValue
  ) extends ChatChunk

  final case class WebSearchResultItem(
    title: Option[String],
    url: String
  )

  /**
   * An image produced by the model or a tool (OpenAI image generation partial / final images,
   * code interpreter image outputs, Gemini inline image parts) - as base64 data and/or a URL.
   */
  final case class Image(
    mimeType: Option[String],
    base64Data: Option[String],
    url: Option[String]
  ) extends ChatChunk

  /** A refusal message fragment (OpenAI `refusal` content). */
  final case class Refusal(text: String) extends ChatChunk

  /**
   * A citation / grounding reference attached to the answer (Anthropic `citations_delta`,
   * Gemini `groundingMetadata.groundingChunks`, OpenAI output-text annotations); `raw` carries
   * the provider-shaped JSON.
   */
  final case class Citation(
    citedText: Option[String],
    url: Option[String],
    title: Option[String],
    raw: JsValue
  ) extends ChatChunk

  /**
   * The stream's stop reason, normalized, plus the provider's own value (`end_turn`,
   * `tool_use`, `STOP`, `MAX_TOKENS`, ...).
   */
  final case class Finish(
    reason: FinishReason,
    providerReason: Option[String]
  ) extends ChatChunk

  /** Token usage in OpenAI shape (the cross-provider usage currency of this library). */
  final case class Usage(usage: UsageInfo) extends ChatChunk

  /**
   * Anything not modeled above, e.g. `ping`, `container_upload`, `inlineData`, additional
   * choices (`choice[1]`) / candidates (`candidate[1]`), or a provider event type this client
   * does not know yet. `kind` is the provider's own event / block / part type.
   */
  final case class Other(
    kind: String,
    raw: JsValue
  ) extends ChatChunk

  sealed trait FinishReason extends EnumValue

  object FinishReason {
    // stop / end_turn / stop_sequence / STOP
    case object stop extends FinishReason
    // tool_calls / function_call / tool_use / a Gemini STOP that carried function calls
    case object tool_calls extends FinishReason
    // length / max_tokens / MAX_TOKENS
    case object length extends FinishReason
    // content_filter / refusal / SAFETY / RECITATION / PROHIBITED_CONTENT / BLOCKLIST
    case object content_filter extends FinishReason
    // anything else - see Finish.providerReason
    case object unknown extends FinishReason

    def values: Seq[FinishReason] = Seq(stop, tool_calls, length, content_filter, unknown)

    /** Normalizes an OpenAI / OpenAI-compatible `finish_reason`. */
    /**
     * Gemini-family finish reasons (shared by the Gemini and Vertex AI mappers, keyed by the
     * enum name): `STOP` is `tool_calls` when a function call was streamed.
     */
    def fromGemini(
      finishReason: String,
      sawFunctionCall: Boolean
    ): FinishReason =
      finishReason match {
        case "STOP"       => if (sawFunctionCall) tool_calls else stop
        case "MAX_TOKENS" => length
        case "SAFETY" | "RECITATION" | "BLOCKLIST" | "PROHIBITED_CONTENT" | "SPII" |
            "IMAGE_SAFETY" | "MODEL_ARMOR" =>
          content_filter
        case _ => unknown
      }

    def fromOpenAI(reason: String): FinishReason =
      reason match {
        case "stop"                         => stop
        case "tool_calls" | "function_call" => tool_calls
        case "length"                       => length
        case "content_filter"               => content_filter
        case _                              => unknown
      }
  }

  /**
   * Folds a typed stream into an [[AssembledChatCompletion]] (linear in the streamed size - a
   * fresh mutable builder per materialization).
   */
  def assembleSink: Sink[ChatChunk, Future[AssembledChatCompletion]] =
    Flow[ChatChunk]
      .fold(Option.empty[AssembledChatCompletion.Builder]) {
        (
          acc,
          chunk
        ) =>
          Some(acc.getOrElse(new AssembledChatCompletion.Builder).add(chunk))
      }
      .map(_.fold(AssembledChatCompletion.empty)(_.result()))
      .toMat(Sink.head)(Keep.right)

  /**
   * Convenience views over a typed stream. Being defined in the companion, they are found by
   * implicit scope without an import.
   */
  implicit class ChatChunkSourceOps[Mat](private val source: Source[ChatChunk, Mat])
      extends AnyVal {

    /** The legacy text-only view (`Source[String, _]` of answer fragments). */
    def texts: Source[String, Mat] = source.collect { case Text(t) => t }

    def thinkingTexts: Source[String, Mat] = source.collect { case Thinking(t) => t }

    def toolCalls: Source[ToolCall, Mat] = source.collect { case tc: ToolCall => tc }

    def toolResults: Source[ToolResult, Mat] = source.collect { case tr: ToolResult => tr }

    def citations: Source[Citation, Mat] = source.collect { case c: Citation => c }

    def codeExecutions: Source[CodeExecution, Mat] = source.collect { case c: CodeExecution =>
      c
    }

    def webSearches: Source[WebSearch, Mat] = source.collect { case w: WebSearch => w }

    def images: Source[Image, Mat] = source.collect { case i: Image => i }

    def assembled(
      implicit materializer: Materializer
    ): Future[AssembledChatCompletion] =
      source.runWith(assembleSink)
  }
}
