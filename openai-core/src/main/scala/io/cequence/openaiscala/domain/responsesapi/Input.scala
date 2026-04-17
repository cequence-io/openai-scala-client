package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.ChatRole
import io.cequence.openaiscala.domain.responsesapi.tools._
import io.cequence.openaiscala.domain.responsesapi.tools.mcp._

/**
 * Input types hierarchy:
 *
 *   - Text input (string)
 *   - Input item list (array)
 *     - Input message (object) - content, role
 *       - Content - Text input (string) or Input item content list (array)
 *         - Text input (object)
 *         - Image input (object)
 *         - File input (object)
 *     - Item (object)
 *       - Input message (object) - content, role, status
 *       - Output message (object)
 *       - File search tool call (object)
 *       - Computer tool call (object)
 *       - Computer tool call output (object)
 *       - Web search tool call (object)
 *       - Function tool call (object)
 *       - Function tool call output (object)
 *       - Reasoning (object)
 *       - Reasoning output (object) - deprecated?
 *       - Image generation call (object)
 *       - Code interpreter tool call (object)
 *       - Local shell call (object)
 *       - Local shell call output (object)
 *       - MCP list tools (object)
 *       - MCP approval request (object)
 *       - MCP approval response (object)
 *       - MCP tool call (object)
 *       - Custom tool call output (object)
 *       - Custom tool call (object)
 *     - Item reference (object)
 */
trait Input {
  val `type`: String
}

// shortcuts for creating Inputs
object Input {

  def ofInputTextMessage(
    content: String,
    role: ChatRole
  ): Message.InputText = Message.InputText(content, role)

  def ofInputSystemTextMessage(
    content: String
  ): Message.InputText = Message.System(content)

  def ofInputDeveloperTextMessage(
    content: String
  ): Message.InputText = Message.Developer(content)

  def ofInputUserTextMessage(
    content: String
  ): Message.InputText = Message.User(content)

  def ofInputAssistantTextMessage(
    content: String
  ): Message.InputText = Message.Assistant(content)

  def ofInputMessage(
    content: Seq[InputMessageContent],
    role: ChatRole,
    status: Option[ModelStatus] = None
  ): Message.InputContent = Message.InputContent(content, role, status)

  def ofOutputMessage(
    content: Seq[OutputMessageContent],
    id: String,
    status: ModelStatus
  ): Message.OutputContent = Message.OutputContent(content, id, status)

  def ofFileSearchToolCall(
    id: String,
    queries: Seq[String] = Nil,
    status: ModelStatus,
    results: Seq[FileSearchResult] = Nil
  ): FileSearchToolCall = FileSearchToolCall(id, queries, status, results)

  def ofComputerToolCall(
    action: ComputerToolAction,
    callId: String,
    id: String,
    pendingSafetyChecks: Seq[PendingSafetyCheck] = Nil,
    status: ModelStatus
  ): ComputerToolCall = ComputerToolCall(
    action,
    callId,
    id,
    pendingSafetyChecks,
    status
  )

  def ofComputerToolCallOutput(
    callId: String,
    output: ComputerScreenshot,
    acknowledgedSafetyChecks: Seq[AcknowledgedSafetyCheck] = Nil,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ): ComputerToolCallOutput = ComputerToolCallOutput(
    callId,
    output,
    acknowledgedSafetyChecks,
    id,
    status
  )

  def ofWebSearchToolCall(
    action: WebSearchAction,
    id: String,
    status: ModelStatus
  ): WebSearchToolCall = WebSearchToolCall(action, id, status)

  def ofFunctionToolCall(
    arguments: String,
    callId: String,
    name: String,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ): FunctionToolCall = FunctionToolCall(
    arguments,
    callId,
    name,
    id,
    status
  )

  def ofFunctionToolCallOutput(
    callId: String,
    output: FunctionToolOutput,
    id: Option[String] = None,
    status: Option[ModelStatus] = None
  ): FunctionToolCallOutput = FunctionToolCallOutput(
    callId,
    output,
    id,
    status
  )

  def ofReasoning(
    id: String,
    summary: Seq[SummaryText],
    status: Option[ModelStatus] = None
  ): Reasoning = Reasoning(id, summary, Nil, None, status)

  def ofImageGenerationToolCall(
    id: String,
    result: String,
    status: String
  ): ImageGenerationToolCall = ImageGenerationToolCall(
    id,
    result,
    status
  )

  def ofCodeInterpreterToolCall(
    id: String,
    code: Option[String],
    containerId: String,
    outputs: Seq[CodeInterpreterOutput],
    status: String
  ): CodeInterpreterToolCall = CodeInterpreterToolCall(
    id,
    code,
    containerId,
    outputs,
    status
  )

  def ofCodeInterpreterOutputLogs(
    logs: String
  ): CodeInterpreterOutputLogs = CodeInterpreterOutputLogs(logs)

  def ofCodeInterpreterOutputImage(
    url: String
  ): CodeInterpreterOutputImage = CodeInterpreterOutputImage(url)

  def ofLocalShellToolCall(
    action: LocalShellAction,
    callId: String,
    id: String,
    status: String
  ): LocalShellToolCall = LocalShellToolCall(
    action,
    callId,
    id,
    status
  )

  def ofLocalShellAction(
    command: Seq[String],
    env: Map[String, String],
    timeoutMs: Option[Int] = None,
    user: Option[String] = None,
    workingDirectory: Option[String] = None
  ): LocalShellAction = LocalShellAction(
    command,
    env,
    timeoutMs,
    user,
    workingDirectory
  )

  def ofLocalShellCallOutput(
    id: String,
    output: String,
    status: Option[String] = None
  ): LocalShellToolCallOutput = LocalShellToolCallOutput(
    id,
    output,
    status
  )

  def ofMCPListTools(
    id: String,
    serverLabel: String,
    tools: Seq[MCPToolRef],
    error: Option[String] = None
  ): MCPListTools = MCPListTools(
    id,
    serverLabel,
    tools,
    error
  )

  def ofMCPToolCall(
    arguments: String,
    id: String,
    name: String,
    serverLabel: String,
    approvalRequestId: Option[String] = None,
    error: Option[MCPToolError] = None,
    output: Option[String] = None,
    status: Option[ModelStatus] = None
  ): MCPToolCall = MCPToolCall(
    arguments,
    id,
    name,
    serverLabel,
    approvalRequestId,
    error,
    output,
    status
  )

  def ofCustomToolCall(
    callId: String,
    input: String,
    name: String,
    id: Option[String] = None
  ): CustomToolCall = CustomToolCall(
    callId,
    input,
    name,
    id
  )

  def ofCustomToolCallOutput(
    callId: String,
    output: FunctionToolOutput,
    id: Option[String] = None
  ): CustomToolCallOutput = CustomToolCallOutput(
    callId,
    output,
    id
  )

  def ofItemReference(
    id: String
  ): ItemReference = ItemReference(id)
}
