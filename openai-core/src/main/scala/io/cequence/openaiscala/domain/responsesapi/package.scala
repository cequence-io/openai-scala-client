package io.cequence.openaiscala.domain

import io.cequence.openaiscala.domain.response.TracedBlock
import io.cequence.openaiscala.domain.responsesapi.tools._
import io.cequence.openaiscala.domain.responsesapi.tools.mcp._
import io.cequence.openaiscala.domain.responsesapi.OutputMessageContent._

package object responsesapi {

  private def truncate(
    s: String,
    maxLen: Int = 50
  ): String =
    if (s.length <= maxLen) s else s.take(maxLen) + "..."

  def toTracedBlocks(response: Response): Seq[TracedBlock] =
    response.output.map { output =>
      val (content, trace) = output match {
        case Message.OutputContent(msgContent, _, _) =>
          val texts = msgContent.collect { case OutputText(_, text) => text }
          val refusals = msgContent.collect { case Refusal(refusal) => refusal }
          val joinedText = texts.mkString("\n")
          val traceText = if (refusals.nonEmpty) {
            s"${truncate(joinedText)} [${refusals.size} refusal(s)]"
          } else {
            truncate(joinedText)
          }
          (if (joinedText.nonEmpty) Some(joinedText) else None, traceText)

        case Reasoning(_, summary, _, encryptedContent, _) =>
          val summaryText = summary.map(_.text).mkString("\n")
          val traceText = encryptedContent match {
            case Some(_) => "[encrypted]"
            case None    => truncate(summaryText)
          }
          (if (summaryText.nonEmpty) Some(summaryText) else None, traceText)

        case FunctionToolCall(arguments, callId, name, _, _) =>
          (Some(arguments), s"$name (callId: $callId)")

        case FileSearchToolCall(_, queries, _, results) =>
          val queryText = if (queries.nonEmpty) Some(queries.mkString(", ")) else None
          (queryText, s"${queries.size} queries, ${results.size} results")

        case WebSearchToolCall(action, _, _) =>
          action match {
            case WebSearchAction.Search(query, sources) =>
              val q = query.getOrElse("")
              (
                if (q.nonEmpty) Some(q) else None,
                s"search: ${truncate(q)} [${sources.size} sources]"
              )
            case WebSearchAction.OpenPage(url) =>
              (Some(url), s"open_page: ${truncate(url)}")
            case WebSearchAction.Find(pattern, url) =>
              (Some(s"$pattern in $url"), s"find: ${truncate(pattern)} in ${truncate(url)}")
          }

        case ComputerToolCall(action, callId, _, _, _) =>
          (Some(action.`type`), s"${action.`type`} (callId: $callId)")

        case ImageGenerationToolCall(id, _, status) =>
          (None, s"id: $id, status: $status")

        case CodeInterpreterToolCall(_, code, containerId, outputs, _) =>
          (code, s"${outputs.size} outputs, container: $containerId")

        case LocalShellToolCall(action, _, _, _) =>
          val commandText = action.command.mkString(" ")
          val traceText = action.workingDirectory match {
            case Some(dir) => s"${truncate(commandText)} in $dir"
            case None      => truncate(commandText)
          }
          (Some(commandText), traceText)

        case CustomToolCall(callId, input, name, _) =>
          (Some(input), s"$name (callId: $callId)")

        case MCPToolCall(arguments, id, name, serverLabel, _, error, mcpOutput, _) =>
          val contentText = mcpOutput.orElse(Some(arguments))
          val errorInfo = error.map(e => s" [error: $e]").getOrElse("")
          (contentText, s"$name @ $serverLabel (id: $id)$errorInfo")

        case MCPListTools(_, serverLabel, tools, error) =>
          val errorInfo = error.map(e => s" [error: $e]").getOrElse("")
          (None, s"$serverLabel: ${tools.size} tools$errorInfo")

        case MCPApprovalRequest(arguments, id, name, serverLabel) =>
          (Some(arguments), s"$name @ $serverLabel (id: $id)")

        case _ =>
          (None, output.`type`)
      }

      TracedBlock(
        blockType = output.`type`,
        text = content,
        summary = trace,
        originalBlock = output
      )
    }
}
