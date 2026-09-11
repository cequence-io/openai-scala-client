package io.cequence.openaiscala.vertexai.service.impl

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.google.cloud.vertexai.api.{Candidate, GenerateContentResponse}
import com.google.protobuf.util.JsonFormat
import io.cequence.openaiscala.domain.response.ChatChunk
import play.api.libs.json.Json

import java.util.Base64
import scala.collection.convert.ImplicitConversions.`list asScalaBuffer`
import scala.collection.mutable

/**
 * Converts streamed Vertex AI (Java SDK / protobuf) [[GenerateContentResponse]]s into typed
 * [[ChatChunk]]s - the Vertex counterpart of the Gemini-direct mapping: thought parts ->
 * Thinking (+ ThinkingSignature), text -> Text, `functionCall` -> ToolCallStart + ToolCall,
 * `executableCode` / `codeExecutionResult` -> server-side ToolCall / ToolResult plus
 * CodeExecution / CodeExecutionResult, inline images -> Image, grounding -> WebSearch /
 * WebSearchResult / Citation, the finish reason -> Finish + Usage. Unknown parts and further
 * candidates pass through as [[ChatChunk.Other]].
 */
private[impl] object VertexAIChatChunks {

  private def protoJson(message: com.google.protobuf.MessageOrBuilder) =
    Json.parse(JsonFormat.printer().omittingInsignificantWhitespace().print(message))

  def toChatChunks(model: String): Flow[GenerateContentResponse, ChatChunk, NotUsed] =
    Flow[GenerateContentResponse].statefulMapConcat { () =>
      var started = false
      var toolCount = 0
      var sawFunctionCall = false
      var lastCodeExecutionCallId: Option[String] = None

      (response: GenerateContentResponse) => {
        val out = mutable.ListBuffer.empty[ChatChunk]

        if (!started) {
          started = true
          out += ChatChunk.Start("vertexai", model)
        }

        response.getCandidatesList.toSeq.foreach { candidate =>
          if (candidate.getIndex != 0)
            out += ChatChunk.Other(s"candidate[${candidate.getIndex}]", protoJson(candidate))
          else {
            candidate.getContent.getPartsList.toSeq.foreach { part =>
              val signature =
                Option(part.getThoughtSignature)
                  .filterNot(_.isEmpty)
                  .map(bytes => Base64.getEncoder.encodeToString(bytes.toByteArray))

              // the signature's call id when the part is a function call - the signature is
              // emitted after the part's own chunks, whatever the part type
              var signatureCallId: Option[String] = None

              if (part.hasText) {
                if (part.getText.nonEmpty)
                  out += (if (part.getThought) ChatChunk.Thinking(part.getText)
                          else ChatChunk.Text(part.getText))
              } else if (part.hasFunctionCall) {
                val fc = part.getFunctionCall
                sawFunctionCall = true
                val ordinal = toolCount
                toolCount += 1
                // Vertex function calls carry no server-assigned id - synthesize a unique one
                val callId = java.util.UUID.randomUUID().toString
                val args =
                  JsonFormat.printer().omittingInsignificantWhitespace().print(fc.getArgs)
                out += ChatChunk.ToolCallStart(ordinal, callId, fc.getName, serverSide = false)
                out += ChatChunk.ToolCall(
                  ordinal,
                  callId,
                  fc.getName,
                  args,
                  serverSide = false
                )
                signatureCallId = Some(callId)
              } else if (part.hasExecutableCode) {
                val code = part.getExecutableCode
                val ordinal = toolCount
                toolCount += 1
                val callId = java.util.UUID.randomUUID().toString
                lastCodeExecutionCallId = Some(callId)
                val language = code.getLanguage.name.toLowerCase
                val arguments =
                  Json.obj("language" -> language, "code" -> code.getCode).toString
                out += ChatChunk.ToolCallStart(
                  ordinal,
                  callId,
                  "code_execution",
                  serverSide = true
                )
                out += ChatChunk.ToolCall(
                  ordinal,
                  callId,
                  "code_execution",
                  arguments,
                  serverSide = true
                )
                out += ChatChunk.CodeExecution(callId, Some(language), code.getCode)
              } else if (part.hasCodeExecutionResult) {
                val result = part.getCodeExecutionResult
                val callId = lastCodeExecutionCallId.getOrElse("")
                val output = Option(result.getOutput).filter(_.nonEmpty)
                val isError = result.getOutcome.name != "OUTCOME_OK"
                val content = Json.obj("outcome" -> result.getOutcome.name, "output" -> output)
                out += ChatChunk.ToolResult(callId, "code_execution", content, output, isError)
                out += ChatChunk.CodeExecutionResult(callId, output, isError, content)
              } else if (
                part.hasInlineData && part.getInlineData.getMimeType.startsWith("image/")
              ) {
                val blob = part.getInlineData
                out += ChatChunk.Image(
                  Some(blob.getMimeType),
                  Some(Base64.getEncoder.encodeToString(blob.getData.toByteArray)),
                  None
                )
              } else
                out += ChatChunk.Other("part", protoJson(part))

              signature.foreach(s => out += ChatChunk.ThinkingSignature(s, signatureCallId))
            }

            if (candidate.hasGroundingMetadata) {
              val grounding = candidate.getGroundingMetadata
              val queries = grounding.getWebSearchQueriesList.toSeq
              val webChunks =
                grounding.getGroundingChunksList.toSeq.filter(_.hasWeb).map(_.getWeb)

              if (queries.nonEmpty) out += ChatChunk.WebSearch("", queries)
              if (webChunks.nonEmpty) {
                val raw = Json.toJson(
                  webChunks.map(w => Json.obj("uri" -> w.getUri, "title" -> w.getTitle))
                )
                out += ChatChunk.WebSearchResult(
                  "",
                  webChunks.map(w =>
                    ChatChunk.WebSearchResultItem(Some(w.getTitle), w.getUri)
                  ),
                  raw
                )
                webChunks.foreach { w =>
                  out += ChatChunk.Citation(
                    None,
                    Some(w.getUri),
                    Some(w.getTitle),
                    Json.obj("uri" -> w.getUri, "title" -> w.getTitle)
                  )
                }
              }
            }

            candidate.getFinishReason match {
              case Candidate.FinishReason.FINISH_REASON_UNSPECIFIED |
                  Candidate.FinishReason.UNRECOGNIZED =>
                ()
              case finishReason =>
                out += ChatChunk.Finish(
                  ChatChunk.FinishReason.fromGemini(finishReason.name, sawFunctionCall),
                  Some(finishReason.name)
                )
                if (response.hasUsageMetadata)
                  out += ChatChunk.Usage(toOpenAI(response.getUsageMetadata))
            }
          }
        }

        out.toList
      }
    }
}
