package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.cequence.openaiscala.JsonFormats.chatCompletionChoiceChunkInfoFormat
import io.cequence.openaiscala.domain.{ChatRole, FunctionCallChunkSpec, ToolCallChunkSpec}
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response.{
  ChatChunk,
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChunkResponse,
  ChunkMessageSpec,
  CompletionTokenDetails,
  PromptTokensDetails,
  UsageInfo => ChatUsageInfo
}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.responsesapi.{
  Annotation,
  ResponseStreamEvent,
  UsageInfo => ResponsesUsageInfo
}
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import java.{util => ju}
import scala.collection.mutable

/**
 * Conversions between OpenAI-shaped chat-completion chunks ([[ChatCompletionChunkResponse]])
 * and the provider-neutral typed [[ChatChunk]]s. Used by every OpenAI-compatible provider and
 * by the default `createChatToolCompletionStreamed` of the streamed service trait.
 */
object ChatChunks {

  private final class PendingToolCall(
    val callId: String,
    val toolName: String
  ) {
    val arguments = new StringBuilder
  }

  /**
   * Maps OpenAI-shaped chunks to typed chunks (choice 0 only; other choices become
   * [[ChatChunk.Other]] with kind `choice[<index>]`). Streamed tool-call fragments are
   * assembled per `tool_calls[].index` and flushed as [[ChatChunk.ToolCall]]s when the
   * `finish_reason` arrives; `delta.reasoning_content` / `delta.reasoning` become
   * [[ChatChunk.Thinking]].
   */
  def fromOpenAIChunks: Flow[ChatCompletionChunkResponse, ChatChunk, NotUsed] =
    Flow[ChatCompletionChunkResponse].statefulMapConcat { () =>
      var started = false
      val pending = mutable.LinkedHashMap.empty[Int, PendingToolCall]
      // some providers (e.g. Groq) put the usage on the final content chunk AND on the trailing
      // usage-only chunk requested via stream_options - emit each distinct usage once
      var lastUsage: Option[ChatUsageInfo] = None

      (chunk: ChatCompletionChunkResponse) => {
        val out = mutable.ListBuffer.empty[ChatChunk]

        if (!started) {
          started = true
          out += Start(chunk.id, chunk.model)
        }

        chunk.choices.foreach { choice =>
          if (choice.index != 0)
            out += Other(s"choice[${choice.index}]", Json.toJson(choice))
          else {
            val delta = choice.delta

            delta.reasoningText.filter(_.nonEmpty).foreach(t => out += Thinking(t))
            delta.content.filter(_.nonEmpty).foreach(t => out += Text(t))

            delta.tool_calls.getOrElse(Nil).foreach { tc =>
              val name = tc.function.flatMap(_.name)
              val args = tc.function.flatMap(_.arguments).getOrElse("")

              pending.get(tc.index) match {
                case None =>
                  val call = new PendingToolCall(tc.id.getOrElse(""), name.getOrElse(""))
                  pending.put(tc.index, call)
                  out += ToolCallStart(
                    tc.index,
                    call.callId,
                    call.toolName,
                    serverSide = false
                  )
                  if (args.nonEmpty) {
                    call.arguments.append(args)
                    out += ToolCallDelta(tc.index, args)
                  }

                case Some(call) =>
                  if (args.nonEmpty) {
                    call.arguments.append(args)
                    out += ToolCallDelta(tc.index, args)
                  }
              }
            }

            choice.finish_reason.foreach { reason =>
              pending.toSeq.sortBy(_._1).foreach { case (index, call) =>
                out += ToolCall(
                  index,
                  call.callId,
                  call.toolName,
                  if (call.arguments.isEmpty) "{}" else call.arguments.toString,
                  serverSide = false
                )
              }
              pending.clear()
              out += Finish(FinishReason.fromOpenAI(reason), Some(reason))
            }
          }
        }

        chunk.usage.foreach { u =>
          if (!lastUsage.contains(u)) {
            lastUsage = Some(u)
            out += Usage(u)
          }
        }

        out.toList
      }
    }

  private final class ResponseItem(
    val ordinal: Int,
    val callId: String,
    val toolName: String,
    val serverSide: Boolean
  ) {
    val arguments = new StringBuilder
    var completed: Boolean = false
  }

  private def toChatUsage(usage: ResponsesUsageInfo): ChatUsageInfo =
    ChatUsageInfo(
      prompt_tokens = usage.inputTokens,
      total_tokens = usage.totalTokens,
      completion_tokens = Some(usage.outputTokens),
      prompt_tokens_details = usage.inputTokensDetails.map(d =>
        PromptTokensDetails(cached_tokens = d.cachedTokens.getOrElse(0), audio_tokens = None)
      ),
      completion_tokens_details = usage.outputTokensDetails.map(d =>
        CompletionTokenDetails(reasoning_tokens = Some(d.reasoningTokens))
      )
    )

  /**
   * Maps streamed Responses API events to typed chunks: output text -> Text, refusals ->
   * Refusal, reasoning summaries / text -> Thinking (the encrypted reasoning content ->
   * ThinkingSignature), function calls -> ToolCallStart / ToolCallDelta / ToolCall,
   * server-side calls (web search, code interpreter, MCP, file search, image generation) ->
   * tool-layer chunks plus WebSearch / CodeExecution / CodeExecutionResult / Image,
   * annotations -> Citation, completion -> Finish + Usage. A `response.failed` or `error`
   * event fails the stream with an [[OpenAIScalaClientException]]; lifecycle notifications and
   * unknown events pass through as [[ChatChunk.Other]].
   */
  def fromResponseEvents: Flow[ResponseStreamEvent, ChatChunk, NotUsed] =
    Flow[ResponseStreamEvent].statefulMapConcat { () =>
      import ResponseStreamEvent._

      var toolCount = 0
      var sawClientToolCall = false
      val items = mutable.Map.empty[String, ResponseItem]

      def register(
        itemId: String,
        callId: String,
        toolName: String,
        serverSide: Boolean
      ): List[ChatChunk] = {
        val item = new ResponseItem(toolCount, callId, toolName, serverSide)
        toolCount += 1
        items.put(itemId, item)
        if (!serverSide) sawClientToolCall = true
        List(ToolCallStart(item.ordinal, callId, toolName, serverSide))
      }

      // items are keyed by their id; an id-less item (never seen from OpenAI, but possible
      // from a gateway) is keyed by its output index instead
      def itemKey(
        itemId: Option[String],
        outputIndex: Int
      ): String = itemId.getOrElse(s"#$outputIndex")

      def lookup(
        itemId: String,
        outputIndex: Int
      ): Option[ResponseItem] =
        items.get(itemId).orElse(items.get(itemKey(None, outputIndex)))

      def delta(
        itemId: String,
        outputIndex: Int,
        fragment: String
      ): List[ChatChunk] =
        lookup(itemId, outputIndex).toList.map { item =>
          item.arguments.append(fragment)
          ToolCallDelta(item.ordinal, fragment)
        }

      // the assembled call - emitted once, either on the *_arguments.done / code.done event or
      // (when no such event was streamed) on output_item.done
      def complete(
        itemId: String,
        outputIndex: Int,
        arguments: Option[String]
      ): List[ChatChunk] =
        lookup(itemId, outputIndex).toList.flatMap { item =>
          if (item.completed) Nil
          else {
            item.completed = true
            val args = arguments.getOrElse(
              if (item.arguments.isEmpty) "{}" else item.arguments.toString
            )
            List(ToolCall(item.ordinal, item.callId, item.toolName, args, item.serverSide))
          }
        }

      def itemJson(raw: JsValue): JsValue = raw \ "item" match {
        case defined: play.api.libs.json.JsDefined => defined.value
        case _                                     => JsNull
      }

      def logsOf(outputs: Seq[JsValue]): Option[String] = {
        val logs = outputs.flatMap(o => (o \ "logs").asOpt[String])
        if (logs.isEmpty) None else Some(logs.mkString("\n"))
      }

      (event: ResponseStreamEvent) =>
        event match {
          case ResponseCreated(id, model, _) =>
            List(Start(id, model))

          case OutputItemAdded(outputIndex, itemType, itemIdOpt, _, raw) =>
            val item = itemJson(raw)
            val itemId = itemKey(itemIdOpt, outputIndex)
            itemType match {
              case "function_call" =>
                register(
                  itemId,
                  (item \ "call_id").asOpt[String].getOrElse(itemId),
                  (item \ "name").asOpt[String].getOrElse(""),
                  serverSide = false
                )
              case "custom_tool_call" =>
                register(
                  itemId,
                  (item \ "call_id").asOpt[String].getOrElse(itemId),
                  (item \ "name").asOpt[String].getOrElse("custom_tool"),
                  serverSide = false
                )
              case "computer_call" =>
                register(
                  itemId,
                  (item \ "call_id").asOpt[String].getOrElse(itemId),
                  "computer",
                  serverSide = false
                )
              case "local_shell_call" =>
                register(
                  itemId,
                  (item \ "call_id").asOpt[String].getOrElse(itemId),
                  "local_shell",
                  serverSide = false
                )
              case "web_search_call" =>
                register(itemId, itemId, "web_search", serverSide = true)
              case "code_interpreter_call" =>
                register(itemId, itemId, "code_interpreter", serverSide = true)
              case "file_search_call" =>
                register(itemId, itemId, "file_search", serverSide = true)
              case "image_generation_call" =>
                register(itemId, itemId, "image_generation", serverSide = true)
              case "mcp_call" =>
                register(
                  itemId,
                  itemId,
                  (item \ "name").asOpt[String].getOrElse("mcp"),
                  serverSide = true
                )
              case "message" | "reasoning" => Nil
              case other                   => List(Other(s"output_item.$other", raw))
            }

          case FunctionCallArgumentsDelta(itemId, outputIndex, fragment) =>
            delta(itemId, outputIndex, fragment)
          case FunctionCallArgumentsDone(itemId, outputIndex, arguments) =>
            complete(itemId, outputIndex, Some(arguments))
          case McpCallArgumentsDelta(itemId, outputIndex, fragment) =>
            delta(itemId, outputIndex, fragment)
          case McpCallArgumentsDone(itemId, outputIndex, arguments) =>
            complete(itemId, outputIndex, Some(arguments))
          case CodeInterpreterCodeDelta(itemId, outputIndex, fragment) =>
            delta(itemId, outputIndex, fragment)
          case CodeInterpreterCodeDone(itemId, outputIndex, code) =>
            complete(itemId, outputIndex, Some(Json.obj("code" -> code).toString)) :+
              CodeExecution(itemId, Some("python"), code)

          case OutputItemDone(outputIndex, itemType, itemIdOpt, _, raw) =>
            val item = itemJson(raw)
            val itemId = itemKey(itemIdOpt, outputIndex)
            itemType match {
              case "function_call" | "custom_tool_call" =>
                complete(
                  itemId,
                  outputIndex,
                  (item \ "arguments").asOpt[String].orElse((item \ "input").asOpt[String])
                )
              case "computer_call" | "local_shell_call" =>
                complete(
                  itemId,
                  outputIndex,
                  Some((item \ "action").toOption.getOrElse(JsNull).toString)
                )
              case "web_search_call" =>
                val action = (item \ "action").toOption.getOrElse(Json.obj())
                val queries = (action \ "queries")
                  .asOpt[Seq[String]]
                  .getOrElse((action \ "query").asOpt[String].toSeq)
                val sources = (action \ "sources")
                  .asOpt[Seq[JsObject]]
                  .getOrElse(Nil)
                  .flatMap(src =>
                    (src \ "url").asOpt[String].map(url => WebSearchResultItem(None, url))
                  )
                complete(itemId, outputIndex, Some(action.toString)) ++
                  List(WebSearch(itemId, queries)) ++
                  (if (sources.nonEmpty) List(WebSearchResult(itemId, sources, action))
                   else Nil)
              case "code_interpreter_call" =>
                val code = (item \ "code").asOpt[String].getOrElse("")
                val outputs = (item \ "outputs").asOpt[Seq[JsValue]].getOrElse(Nil)
                val completed =
                  complete(itemId, outputIndex, Some(Json.obj("code" -> code).toString))
                val codeChunk =
                  if (completed.nonEmpty) List(CodeExecution(itemId, Some("python"), code))
                  else Nil
                val images = outputs.flatMap(o =>
                  (o \ "url").asOpt[String].map(url => Image(None, None, Some(url)))
                )
                val result =
                  if (outputs.nonEmpty) {
                    val outputsJson = Json.toJson(outputs)
                    List(
                      ToolResult(
                        itemId,
                        "code_interpreter",
                        outputsJson,
                        logsOf(outputs),
                        isError = false
                      ),
                      CodeExecutionResult(
                        itemId,
                        logsOf(outputs),
                        isError = false,
                        outputsJson
                      )
                    )
                  } else Nil
                completed ++ codeChunk ++ result ++ images
              case "mcp_call" =>
                val error = (item \ "error").toOption.filterNot(_ == JsNull)
                val output = (item \ "output").asOpt[String]
                complete(itemId, outputIndex, (item \ "arguments").asOpt[String]) ++
                  (if (output.isDefined || error.isDefined)
                     List(
                       ToolResult(
                         itemId,
                         items.get(itemId).map(_.toolName).getOrElse("mcp"),
                         error.orElse(output.map(Json.toJson(_))).getOrElse(JsNull),
                         output.orElse(error.map(_.toString)),
                         isError = error.isDefined
                       )
                     )
                   else Nil)
              case "file_search_call" =>
                val queries = (item \ "queries").asOpt[Seq[String]].getOrElse(Nil)
                val results = (item \ "results").toOption.filterNot(_ == JsNull)
                complete(itemId, outputIndex, Some(Json.obj("queries" -> queries).toString)) ++
                  results
                    .map(r => ToolResult(itemId, "file_search", r, None, isError = false))
                    .toList
              case "image_generation_call" =>
                val result = (item \ "result").asOpt[String]
                complete(itemId, outputIndex, Some("{}")) ++
                  result.map(b64 => Image(None, Some(b64), None)).toList
              case "reasoning" =>
                (item \ "encrypted_content").asOpt[String].map(ThinkingSignature(_)).toList
              case "message" => Nil
              case other     => List(Other(s"output_item.$other", raw))
            }

          case OutputTextDelta(_, _, _, text)            => List(Text(text))
          case RefusalDelta(_, _, _, text)               => List(Refusal(text))
          case ReasoningSummaryTextDelta(_, _, _, text)  => List(Thinking(text))
          case ReasoningTextDelta(_, _, _, text)         => List(Thinking(text))
          case ImageGenerationPartialImage(_, _, _, b64) => List(Image(None, Some(b64), None))

          case OutputTextAnnotationAdded(_, _, _, _, annotation, raw) =>
            val annotationJson = (raw \ "annotation").toOption.getOrElse(JsNull)
            List(annotation match {
              case Some(Annotation.UrlCitation(_, _, url, title)) =>
                Citation(None, Some(url), Some(title), annotationJson)
              case Some(Annotation.FileCitation(_, _, filename)) =>
                Citation(None, None, Some(filename), annotationJson)
              case _ =>
                Citation(
                  None,
                  (annotationJson \ "url").asOpt[String],
                  (annotationJson \ "title")
                    .asOpt[String]
                    .orElse((annotationJson \ "filename").asOpt[String]),
                  annotationJson
                )
            })

          case ResponseCompleted(_, usage, _) =>
            val pending =
              items.values.toList.filterNot(_.completed).sortBy(_.ordinal).map { item =>
                item.completed = true
                ToolCall(
                  item.ordinal,
                  item.callId,
                  item.toolName,
                  if (item.arguments.isEmpty) "{}" else item.arguments.toString,
                  item.serverSide
                )
              }
            pending ++
              List(
                Finish(
                  if (sawClientToolCall) FinishReason.tool_calls else FinishReason.stop,
                  Some("completed")
                )
              ) ++ usage.map(u => Usage(toChatUsage(u))).toList

          case ResponseIncomplete(_, reason, usage, _) =>
            val normalized = reason match {
              case Some("max_output_tokens") => FinishReason.length
              case Some("content_filter")    => FinishReason.content_filter
              case _                         => FinishReason.unknown
            }
            List(Finish(normalized, reason.orElse(Some("incomplete")))) ++
              usage.map(u => Usage(toChatUsage(u))).toList

          case ResponseFailed(_, error, raw) =>
            throw new OpenAIScalaClientException(
              error.map(e => s"${e.code}: ${e.message}").getOrElse(s"Response failed: $raw")
            )

          case ErrorEvent(code, message, _, _) =>
            throw new OpenAIScalaClientException(s"${code.getOrElse("error")}: $message")

          case _: OutputTextDone | _: RefusalDone | _: ReasoningSummaryTextDone |
              _: ReasoningTextDone | _: ResponseInProgress | _: ResponseQueued =>
            Nil

          case ToolCallStatus(eventType, _, _, raw) => List(Other(eventType, raw))
          case UnknownEvent(eventType, raw)         => List(Other(eventType, raw))
        }
    }

  /**
   * [[toOpenAIChunks(id:String,model:String)*]] taking the id / model from the stream's own
   * [[ChatChunk.Start]] chunk.
   */
  def toOpenAIChunks: Flow[ChatChunk, ChatCompletionChunkResponse, NotUsed] =
    Flow[ChatChunk].statefulMapConcat { () =>
      var id = ""
      var model = ""
      val created = new ju.Date()

      (chunk: ChatChunk) => {
        chunk match {
          case Start(chunkId, chunkModel) =>
            id = chunkId
            model = chunkModel
          case _ =>
        }
        toOpenAIChunk(id, model, created, chunk).toList
      }
    }

  /**
   * The reverse view: typed chunks rendered as OpenAI-shaped chunks (text -> `delta.content`,
   * thinking -> `delta.reasoning_content`, tool-call start/deltas -> `delta.tool_calls`,
   * finish -> `finish_reason`, usage -> a trailing usage-only chunk). Assembled
   * [[ChatChunk.ToolCall]]s, tool results, citations and `Other` are not representable and are
   * skipped.
   */
  def toOpenAIChunks(
    id: String,
    model: String
  ): Flow[ChatChunk, ChatCompletionChunkResponse, NotUsed] = {
    val created = new ju.Date()
    Flow[ChatChunk].mapConcat(chunk => toOpenAIChunk(id, model, created, chunk).toList)
  }

  private def toOpenAIChunk(
    id: String,
    model: String,
    created: ju.Date,
    typed: ChatChunk
  ): Option[ChatCompletionChunkResponse] = {
    def chunk(
      delta: Option[ChunkMessageSpec],
      finishReason: Option[String] = None,
      usage: Option[ChatUsageInfo] = None
    ): ChatCompletionChunkResponse =
      ChatCompletionChunkResponse(
        id = id,
        created = created,
        model = model,
        system_fingerprint = None,
        choices =
          delta.toSeq.map(d => ChatCompletionChoiceChunkInfo(d, index = 0, finishReason)),
        usage = usage
      )

    def toolDelta(spec: ToolCallChunkSpec): ChunkMessageSpec =
      ChunkMessageSpec(role = None, content = None, tool_calls = Some(Seq(spec)))

    typed match {
      case Start(_, _) =>
        Some(
          chunk(Some(ChunkMessageSpec(role = Some(ChatRole.Assistant), content = Some(""))))
        )
      case Text(t) =>
        Some(chunk(Some(ChunkMessageSpec(role = None, content = Some(t)))))
      case Thinking(t) =>
        Some(
          chunk(
            Some(ChunkMessageSpec(role = None, content = None, reasoning_content = Some(t)))
          )
        )
      case ToolCallStart(index, callId, name, _) =>
        Some(
          chunk(
            Some(
              toolDelta(
                ToolCallChunkSpec(
                  index = index,
                  id = Some(callId),
                  `type` = Some("function"),
                  function =
                    Some(FunctionCallChunkSpec(name = Some(name), arguments = Some("")))
                )
              )
            )
          )
        )
      case ToolCallDelta(index, fragment) =>
        Some(
          chunk(
            Some(
              toolDelta(
                ToolCallChunkSpec(
                  index = index,
                  function = Some(FunctionCallChunkSpec(arguments = Some(fragment)))
                )
              )
            )
          )
        )
      case Finish(reason, providerReason) =>
        Some(
          chunk(
            Some(ChunkMessageSpec(role = None, content = None)),
            // the OpenAI vocabulary; a provider's own reason is kept only when it already is one
            finishReason = Some(
              providerReason
                .filter(r => FinishReason.fromOpenAI(r) == reason)
                .getOrElse(reason.toString)
            )
          )
        )
      case Usage(u) =>
        Some(chunk(None, usage = Some(u)))
      case _ =>
        None
    }
  }
}
