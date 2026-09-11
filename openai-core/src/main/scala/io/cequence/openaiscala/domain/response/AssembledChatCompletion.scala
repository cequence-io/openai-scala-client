package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.{AssistantToolMessage, ToolCallSpec}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * The result of folding a typed chat-completion stream ([[ChatChunk]]s) - see
 * [[ChatChunk.assembleSink]] / `source.assembled`. `toolCallSignatures` pairs a thinking
 * signature with the tool call it was attached to (Gemini / Vertex AI `thoughtSignature` on a
 * `functionCall` part) - echo it back with that call when continuing the turn natively.
 *
 * [[ChatChunk.ToolCallDelta]]s are ignored on purpose: the assembled arguments ride on the
 * [[ChatChunk.ToolCall]] every provider emits once per call, so a stream carrying all three
 * tool-call events never double counts.
 */
final case class AssembledChatCompletion(
  id: Option[String] = None,
  model: Option[String] = None,
  text: String = "",
  thinking: String = "",
  thinkingSignatures: Seq[String] = Nil,
  toolCallSignatures: Map[String, String] = Map.empty,
  redactedThinking: Seq[String] = Nil,
  toolCalls: Seq[ChatChunk.ToolCall] = Nil,
  toolResults: Seq[ChatChunk.ToolResult] = Nil,
  codeExecutions: Seq[ChatChunk.CodeExecution] = Nil,
  codeExecutionResults: Seq[ChatChunk.CodeExecutionResult] = Nil,
  webSearches: Seq[ChatChunk.WebSearch] = Nil,
  webSearchResults: Seq[ChatChunk.WebSearchResult] = Nil,
  images: Seq[ChatChunk.Image] = Nil,
  refusal: String = "",
  citations: Seq[ChatChunk.Citation] = Nil,
  finishReason: Option[ChatChunk.FinishReason] = None,
  providerFinishReason: Option[String] = None,
  usage: Option[UsageInfo] = None,
  other: Seq[ChatChunk.Other] = Nil
) {

  def add(chunk: ChatChunk): AssembledChatCompletion =
    chunk match {
      case ChatChunk.Start(chunkId, chunkModel) =>
        copy(id = Some(chunkId), model = Some(chunkModel))
      case ChatChunk.Text(t)     => copy(text = text + t)
      case ChatChunk.Thinking(t) => copy(thinking = thinking + t)
      case ChatChunk.ThinkingSignature(s, callId) =>
        copy(
          thinkingSignatures = thinkingSignatures :+ s,
          toolCallSignatures =
            callId.fold(toolCallSignatures)(id => toolCallSignatures + (id -> s))
        )
      case ChatChunk.RedactedThinking(d) => copy(redactedThinking = redactedThinking :+ d)
      case _: ChatChunk.ToolCallStart    => this
      case _: ChatChunk.ToolCallDelta    => this
      case tc: ChatChunk.ToolCall        => copy(toolCalls = toolCalls :+ tc)
      case tr: ChatChunk.ToolResult      => copy(toolResults = toolResults :+ tr)
      case c: ChatChunk.Citation         => copy(citations = citations :+ c)
      case c: ChatChunk.CodeExecution    => copy(codeExecutions = codeExecutions :+ c)
      case c: ChatChunk.CodeExecutionResult =>
        copy(codeExecutionResults = codeExecutionResults :+ c)
      case w: ChatChunk.WebSearch       => copy(webSearches = webSearches :+ w)
      case w: ChatChunk.WebSearchResult => copy(webSearchResults = webSearchResults :+ w)
      case i: ChatChunk.Image           => copy(images = images :+ i)
      case ChatChunk.Refusal(t)         => copy(refusal = refusal + t)
      case ChatChunk.Finish(reason, providerReason) =>
        copy(finishReason = Some(reason), providerFinishReason = providerReason)
      case ChatChunk.Usage(u) => copy(usage = Some(u))
      case o: ChatChunk.Other => copy(other = other :+ o)
    }

  /** Tool calls the caller has to execute (provider-executed ones are excluded). */
  def clientToolCalls: Seq[ChatChunk.ToolCall] = toolCalls.filterNot(_.serverSide)

  /**
   * The assistant turn to append to the conversation in a tool loop, followed by one
   * `ToolMessage` per [[clientToolCalls]] entry.
   */
  def toAssistantToolMessage: AssistantToolMessage =
    AssistantToolMessage(
      content = if (text.nonEmpty) Some(text) else None,
      name = None,
      tool_calls =
        clientToolCalls.map(tc => tc.callId -> (tc.toFunctionCallSpec: ToolCallSpec))
    )
}

object AssembledChatCompletion {
  val empty: AssembledChatCompletion = AssembledChatCompletion()

  /**
   * Mutable accumulator behind [[ChatChunk.assembleSink]]: appends run in amortized constant
   * time, so folding a long stream is linear (the immutable [[AssembledChatCompletion.add]]
   * copies the accumulated text on every chunk).
   */
  final class Builder {
    private var id: Option[String] = None
    private var model: Option[String] = None
    private val text = new StringBuilder
    private val thinking = new StringBuilder
    private val thinkingSignatures = ListBuffer.empty[String]
    private val toolCallSignatures = mutable.LinkedHashMap.empty[String, String]
    private val redactedThinking = ListBuffer.empty[String]
    private val toolCalls = ListBuffer.empty[ChatChunk.ToolCall]
    private val toolResults = ListBuffer.empty[ChatChunk.ToolResult]
    private val codeExecutions = ListBuffer.empty[ChatChunk.CodeExecution]
    private val codeExecutionResults = ListBuffer.empty[ChatChunk.CodeExecutionResult]
    private val webSearches = ListBuffer.empty[ChatChunk.WebSearch]
    private val webSearchResults = ListBuffer.empty[ChatChunk.WebSearchResult]
    private val images = ListBuffer.empty[ChatChunk.Image]
    private val refusal = new StringBuilder
    private val citations = ListBuffer.empty[ChatChunk.Citation]
    private var finishReason: Option[ChatChunk.FinishReason] = None
    private var providerFinishReason: Option[String] = None
    private var usage: Option[UsageInfo] = None
    private val other = ListBuffer.empty[ChatChunk.Other]

    def add(chunk: ChatChunk): Builder = {
      chunk match {
        case ChatChunk.Start(chunkId, chunkModel) =>
          id = Some(chunkId)
          model = Some(chunkModel)
        case ChatChunk.Text(t)     => text.append(t)
        case ChatChunk.Thinking(t) => thinking.append(t)
        case ChatChunk.ThinkingSignature(s, callId) =>
          thinkingSignatures += s
          callId.foreach(toolCallSignatures.put(_, s))
        case ChatChunk.RedactedThinking(d)    => redactedThinking += d
        case _: ChatChunk.ToolCallStart       => ()
        case _: ChatChunk.ToolCallDelta       => ()
        case tc: ChatChunk.ToolCall           => toolCalls += tc
        case tr: ChatChunk.ToolResult         => toolResults += tr
        case c: ChatChunk.Citation            => citations += c
        case c: ChatChunk.CodeExecution       => codeExecutions += c
        case c: ChatChunk.CodeExecutionResult => codeExecutionResults += c
        case w: ChatChunk.WebSearch           => webSearches += w
        case w: ChatChunk.WebSearchResult     => webSearchResults += w
        case i: ChatChunk.Image               => images += i
        case ChatChunk.Refusal(t)             => refusal.append(t)
        case ChatChunk.Finish(reason, providerReason) =>
          finishReason = Some(reason)
          providerFinishReason = providerReason
        case ChatChunk.Usage(u) => usage = Some(u)
        case o: ChatChunk.Other => other += o
      }
      this
    }

    def result(): AssembledChatCompletion =
      AssembledChatCompletion(
        id = id,
        model = model,
        text = text.toString,
        thinking = thinking.toString,
        thinkingSignatures = thinkingSignatures.toList,
        toolCallSignatures = toolCallSignatures.toMap,
        redactedThinking = redactedThinking.toList,
        toolCalls = toolCalls.toList,
        toolResults = toolResults.toList,
        codeExecutions = codeExecutions.toList,
        codeExecutionResults = codeExecutionResults.toList,
        webSearches = webSearches.toList,
        webSearchResults = webSearchResults.toList,
        images = images.toList,
        refusal = refusal.toString,
        citations = citations.toList,
        finishReason = finishReason,
        providerFinishReason = providerFinishReason,
        usage = usage,
        other = other.toList
      )
  }
}
