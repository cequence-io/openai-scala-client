package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings._
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._

import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse,
  CompletionTokenDetails,
  PromptTokensDetails,
  UsageInfo => ChatUsageInfo
}
import io.cequence.openaiscala.domain.responsesapi._
import io.cequence.openaiscala.domain.responsesapi.OutputMessageContent.{OutputText, Refusal}
import io.cequence.openaiscala.domain.responsesapi.tools.{
  FunctionToolCall,
  FunctionToolCallOutput,
  FunctionToolOutput,
  ShellEnvironment,
  ShellSkill,
  ShellTool,
  Tool,
  ToolChoice,
  FunctionTool => ResponsesFunctionTool
}
import io.cequence.openaiscala.domain.responsesapi.tools.mcp.{
  MCPAllowedTools,
  MCPApprovalRequest,
  MCPApprovalResponse,
  MCPRequireApproval,
  MCPTool,
  MCPToolFilter
}
import io.cequence.openaiscala.service.{
  ResponsesToolApprovalsUnsupported,
  ChatChunks,
  OpenAIChatCompletionExtra,
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra,
  OpenAIResponsesService,
  OpenAIStreamedServiceExtra
}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response.{
  ChatChunk,
  ChatCompletionChunkResponse,
  ToolApprovalDecision
}
import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.wsclient.service.CloseableService
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

private[service] class OpenAIResponsesChatCompletionService(
  underlying: OpenAIResponsesService with CloseableService,
  // the same service when it also streams (OpenAIStreamedService) - enables the typed stream
  streamedUnderlying: Option[OpenAIStreamedServiceExtra],
  // whether the backend can pause a run for MCP tool approval (OpenAI) - one that cannot
  // (ResponsesToolApprovalsUnsupported, e.g. Perplexity) refuses approval decisions and
  // requireApproval tools. No default: Scala 2.12 would put a default getter with this
  // class's restricted access on the public companion object, making it inaccessible outside
  // the `service` package
  toolApprovals: Boolean
)(
  implicit ec: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra
    with OpenAIResponsesChatCompletionMappingExt {

  private val logger = LoggerFactory.getLogger(getClass)

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    createChatToolCompletionStreamed(messages, Nil, None, settings).via(
      ChatChunks.toOpenAIChunks
    )

  /**
   * Streams a Responses API call as typed chunks (reasoning summaries, text, function / server
   * tool calls, citations, ...) - the route used for GPT-6 streamed tool completions.
   */
  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] =
    streamedUnderlying match {
      case Some(streamed) =>
        Try(buildResponsesRequest(messages, tools, responseToolChoice, settings)) match {
          case Failure(e) => Source.failed(e)
          case Success(Right((items, responsesSettings))) =>
            streamResponse(streamed, items, responsesSettings, settings)
          case Success(Left(lookup)) =>
            // nothing is sent before the stream is materialized
            Source
              .lazySource(() =>
                Source.futureSource(
                  completeRequest(lookup).map { case (items, responsesSettings) =>
                    streamResponse(streamed, items, responsesSettings, settings)
                  }
                )
              )
              .mapMaterializedValue(_ => NotUsed)
        }

      case None =>
        Source.failed(
          new OpenAIScalaClientException(
            "Streamed Responses API calls require a streaming-capable service (e.g. OpenAIServiceFactory.withStreaming())."
          )
        )
    }

  private def streamResponse(
    streamed: OpenAIStreamedServiceExtra,
    items: Seq[Input],
    responsesSettings: CreateModelResponseSettings,
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] = {
    // the typed stream to surface thinking - ask for reasoning summaries whenever
    // reasoning is configured (unless the caller opted out, or runs multi-agent: the API
    // rejects summaries with it - live 2026-09-30)
    val multiAgent = responsesSettings.multiAgent.exists(_.enabled)
    val withSummaries =
      if (settings.responsesReasoningSummary.getOrElse(!multiAgent))
        responsesSettings.copy(
          reasoning = responsesSettings.reasoning.map(r =>
            r.copy(summary = r.summary.orElse(Some("auto")))
          )
        )
      else responsesSettings

    streamed
      .createModelResponseStreamed(Inputs.Items(items: _*), withSummaries)
      .via(ChatChunks.fromResponseEvents)
  }

  private def toResponsesRequest(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[(Seq[Input], CreateModelResponseSettings)] =
    Future
      .fromTry(Try(buildResponsesRequest(messages, tools, responseToolChoice, settings)))
      .flatMap {
        case Right(request) => Future.successful(request)
        case Left(lookup)   => completeRequest(lookup)
      }

  // a stateful resume carrying tool outputs: which of them answer the paused response's own
  // client function calls is known only from that (stored) response
  private final case class PausedCallsLookup(
    runId: String,
    complete: Set[String] => (Seq[Input], CreateModelResponseSettings)
  )

  private def completeRequest(
    lookup: PausedCallsLookup
  ): Future[(Seq[Input], CreateModelResponseSettings)] =
    underlying
      .getModelResponse(lookup.runId)
      .map(paused => lookup.complete(paused.outputFunctionCalls.map(_.callId).toSet))

  private def buildResponsesRequest(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Either[PausedCallsLookup, (Seq[Input], CreateModelResponseSettings)] = {
    val (instructions, items) = convertMessages(messages)

    val allTools = OpenAIResponsesChatCompletionService.toResponsesTools(tools, settings)
    val decisions = settings.toolApprovalDecisions

    if (!toolApprovals) {
      ToolApprovalSettingsOps.unsupportedDecisions(settings, serviceName).foreach(throw _)
      tools.collectFirst {
        case mcp: ChatCompletionTool.MCPServerTool if mcp.requireApproval => mcp
      }.foreach(mcp =>
        throw new OpenAIScalaClientException(
          s"$serviceName cannot pause for tool approval - MCPServerTool '${mcp.name}' has requireApproval = true (its calls would run unapproved)."
        )
      )
      // an explicit approval requirement on a raw MCP tool (unset = the backend's default)
      allTools.collectFirst {
        case mcp: MCPTool if mcp.requireApproval.isDefined && mayAskForApproval(mcp) => mcp
      }.foreach(mcp =>
        throw new OpenAIScalaClientException(
          s"$serviceName cannot pause for tool approval - MCPTool '${mcp.serverLabel}' requires approval for some calls (they would run unapproved); set requireApproval = never."
        )
      )
    }

    if (decisions.nonEmpty && allTools.isEmpty)
      throw new OpenAIScalaClientException(
        "Resuming a run paused for tool approval requires the same tools as the paused call " +
          "(tools are not carried over) - pass them to createChatToolCompletion(Streamed)."
      )

    // an explicit forced choice is always sent (without tools the API rejects it loudly rather
    // than the choice being dropped silently); 'auto' only when there are tools to choose from
    val responsesToolChoice = responseToolChoice match {
      case Some(name) =>
        if (allTools.isEmpty)
          logger.warn(
            s"Responses API adapter: tool choice '$name' requested but no tools were provided"
          )
        Some(ToolChoice.FunctionTool(name))
      case None =>
        if (allTools.isEmpty) None else Some(ToolChoice.Mode.Auto)
    }

    val responsesSettings =
      toResponsesSettings(settings, instructions, allTools, responsesToolChoice)

    // a run that may pause for approval is stored (unless the caller opted out), so its resume
    // can continue it by id - the server keeps its reasoning and executed MCP calls
    val approvalCapable =
      toolApprovals && decisions.nonEmpty || toolApprovals && allTools.exists {
        case mcp: MCPTool => mayAskForApproval(mcp)
        case _            => false
      }
    val stored = settings.store.getOrElse(approvalCapable)

    if (decisions.isEmpty)
      Right((items, responsesSettings.copy(store = Some(stored))))
    else if (stored) {
      // stateful resume: the paused response is continued by id - send only what is new: the
      // outputs of the paused response's own client function calls (among the trailing tool
      // messages - earlier outputs are already part of the stored conversation) and the answers
      val runId = pausedRunId(decisions)
      val toolOutputs = trailingToolMessages(messages)

      def resume(pausedCalls: Set[String]) = {
        val answering = toolOutputs.filter {
          case tool: ToolMessage => pausedCalls.contains(tool.tool_call_id)
          case _                 => false
        }
        if (answering.size < toolOutputs.size)
          logger.debug(
            s"Responses API adapter: ${toolOutputs.size - answering.size} trailing tool output(s) do not answer a function call of the paused response '$runId' - not sent again."
          )
        (
          convertMessages(answering)._2 ++ approvalResponses(decisions),
          responsesSettings.copy(store = Some(true), previousResponseId = Some(runId))
        )
      }

      if (toolOutputs.isEmpty) Right(resume(Set.empty))
      else Left(PausedCallsLookup(runId, resume))
    } else {
      // stateless (store = false): the pending requests are replayed after the history, each
      // followed by its answer - only one pause deep (a resumed run that pauses again cannot be
      // continued without the items the server did not keep)
      logger.warn(
        "Responses API adapter: resuming a tool-approval pause with store = false replays the pending requests after the history - a run that pauses again cannot be continued; leave `store` unset to resume by response id."
      )
      Right(
        (
          items ++ decisions.flatMap(decision =>
            approvalRequestItem(decision.request) +: approvalResponses(Seq(decision))
          ),
          responsesSettings.copy(store = Some(false))
        )
      )
    }
  }

  // whether a raw MCP tool may pause a run for approval: unset is the API default (always); a
  // filter asks unless nothing is 'always' and its 'never' names cover every allowed tool
  private def mayAskForApproval(mcp: MCPTool): Boolean =
    mcp.requireApproval match {
      case None | Some(MCPRequireApproval.Setting.Always) => true
      case Some(MCPRequireApproval.Setting.Never)         => false
      case Some(MCPRequireApproval.Filter(always, never)) =>
        val allowedNames = mcp.allowedTools.collect {
          case MCPAllowedTools.ToolNames(names)       => names
          case MCPAllowedTools.Filter(_, Some(names)) => names
        }
        val neverCoversAllowed = (allowedNames, never) match {
          case (Some(names), Some(MCPToolFilter(None, Some(neverNames)))) =>
            names.forall(neverNames.contains)
          case _ => false
        }
        always.exists(f => f.readOnly.isDefined || f.toolNames.exists(_.nonEmpty)) ||
        !neverCoversAllowed
    }

  private def serviceName =
    "This Responses API backend"

  // the trailing tool messages: the outputs of the paused response's client function calls
  private def trailingToolMessages(messages: Seq[BaseMessage]): Seq[BaseMessage] =
    messages.reverse.takeWhile(_.isInstanceOf[ToolMessage]).reverse

  private def openAIApprovalRequest(
    request: ChatChunk.ToolApprovalRequest
  ): ChatChunk.ToolApprovalRequest =
    if (
      (request.raw \ "type").asOpt[String].contains("mcp_approval_request") &&
      request.serverName.isDefined
    ) request
    else
      throw new OpenAIScalaClientException(
        s"Tool approval request '${request.requestId}' is not an OpenAI MCP approval request - it cannot resume an OpenAI Responses run."
      )

  // every decision must answer the same paused response
  private def pausedRunId(decisions: Seq[ToolApprovalDecision]): String =
    decisions.map(d => openAIApprovalRequest(d.request).runId).distinct match {
      case Seq(single) => single
      case several =>
        throw new OpenAIScalaClientException(
          s"Tool approval decisions must answer one paused response, got: ${several.mkString(", ")}."
        )
    }

  private def approvalRequestItem(request: ChatChunk.ToolApprovalRequest): Input = {
    val openAIRequest = openAIApprovalRequest(request)
    MCPApprovalRequest(
      openAIRequest.arguments,
      openAIRequest.requestId,
      openAIRequest.toolName,
      openAIRequest.serverName.get
    )
  }

  // a reason only with a denial - OpenAI rejects one on an approval
  private def approvalResponses(decisions: Seq[ToolApprovalDecision]): Seq[Input] =
    decisions.map { decision =>
      MCPApprovalResponse(
        openAIApprovalRequest(decision.request).requestId,
        decision.approve,
        reason = decision.reason.filterNot(_ => decision.approve)
      )
    }

  // Responses-native tools (setResponsesTools) are sent here too; function tools are not
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    toResponsesRequest(messages, Nil, None, settings).flatMap {
      case (items, responsesSettings) =>
        underlying
          .createModelResponse(Inputs.Items(items: _*), responsesSettings)
          .map(toOpenAIChatCompletionResponse)
    }

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    toResponsesRequest(messages, tools, responseToolChoice, settings).flatMap {
      case (items, responsesSettings) =>
        underlying
          .createModelResponse(Inputs.Items(items: _*), responsesSettings)
          .map(toOpenAIToolCompletionResponse)
    }

  override def convertMessages(
    messages: Seq[BaseMessage]
  ): (Option[String], Seq[Input]) = {
    val (instructionMessages, nonSystemMessages) = messages.partition {
      case _: SystemMessage | _: DeveloperMessage => true
      case _                                      => false
    }

    val instructions = {
      val texts = instructionMessages.collect {
        case SystemMessage(content, _)    => content
        case DeveloperMessage(content, _) => content
      }
      if (texts.nonEmpty) Some(texts.mkString("\n")) else None
    }

    val items: Seq[Input] = nonSystemMessages.flatMap {
      case UserMessage(content, _) =>
        Seq(Message.InputText(content, ChatRole.User))

      case UserSeqMessage(contents, _) =>
        val inputContents = contents.map {
          case TextContent(t) => InputMessageContent.Text(t)
          case ImageURLContent(url) =>
            InputMessageContent.Image(imageUrl = Some(url))
          case FileContent(fileId, fileData, filename) =>
            InputMessageContent.File(
              fileData = fileData,
              fileId = fileId,
              fileUrl = None,
              filename = filename
            )
        }
        Seq(Message.InputContent(inputContents, ChatRole.User))

      case AssistantMessage(content, _, _) =>
        if (content.nonEmpty) Seq(Message.InputText(content, ChatRole.Assistant))
        else Seq.empty

      case AssistantToolMessage(contentOpt, _, toolCalls) =>
        val contentItems = contentOpt
          .filter(_.nonEmpty)
          .map { content =>
            Message.InputText(content, ChatRole.Assistant)
          }
          .toSeq

        val toolCallItems = toolCalls.collect { case (callId, FunctionCallSpec(name, args)) =>
          FunctionToolCall(
            arguments = args,
            callId = callId,
            name = name
          )
        }

        contentItems ++ toolCallItems

      case ToolMessage(content, toolCallId, _) =>
        Seq(
          FunctionToolCallOutput(
            callId = toolCallId,
            output = FunctionToolOutput.StringOutput(content.getOrElse(""))
          )
        )

      case other =>
        logger.warn(
          s"Responses API adapter: unsupported message type ${other.getClass.getSimpleName}, skipping"
        )
        Seq.empty
    }

    (instructions, items)
  }

  override def toResponsesSettings(
    settings: CreateChatCompletionSettings,
    instructions: Option[String],
    tools: Seq[Tool],
    toolChoice: Option[ToolChoice]
  ): CreateModelResponseSettings = {
    if (settings.stop.nonEmpty)
      logger.warn("Responses API adapter: 'stop' parameter is not supported, ignoring")
    if (settings.n.exists(_ > 1))
      logger.warn("Responses API adapter: 'n' > 1 is not supported, ignoring")
    if (settings.frequency_penalty.isDefined)
      logger.warn(
        "Responses API adapter: 'frequency_penalty' parameter is not supported, ignoring"
      )
    if (settings.presence_penalty.isDefined)
      logger.warn(
        "Responses API adapter: 'presence_penalty' parameter is not supported, ignoring"
      )
    if (settings.logit_bias.nonEmpty)
      logger.warn("Responses API adapter: 'logit_bias' parameter is not supported, ignoring")
    if (settings.logprobs.isDefined)
      logger.warn("Responses API adapter: 'logprobs' parameter is not supported, ignoring")
    if (settings.seed.isDefined)
      logger.warn("Responses API adapter: 'seed' parameter is not supported, ignoring")
    if (settings.verbosity.isDefined)
      logger.warn("Responses API adapter: 'verbosity' parameter is not supported, ignoring")
    if (
      (settings.extra_params.keySet -- ResponsesChatCompletionSettingsOps.knownParams --
        ToolApprovalSettingsOps.knownParams).nonEmpty
    )
      logger.warn(
        "Responses API adapter: 'extra_params' parameter is not supported, ignoring"
      )

    val text: Option[TextResponseConfig] = settings.response_format_type.flatMap {
      case ChatCompletionResponseFormatType.text => None

      case ChatCompletionResponseFormatType.json_object =>
        Some(TextResponseConfig(ResponseFormat.JsonObject))

      case ChatCompletionResponseFormatType.json_schema =>
        settings.jsonSchema.map { schemaDef =>
          schemaDef.structure match {
            case Left(schema) =>
              val newSchema =
                if (schemaDef.strict)
                  OpenAIChatCompletionExtra.toStrictSchema(schema)
                else
                  schema

              TextResponseConfig(
                ResponseFormat.JsonSchemaSpec(
                  schema = newSchema,
                  name = Some(schemaDef.name),
                  strict = Some(schemaDef.strict)
                )
              )
            case Right(_) =>
              logger.warn(
                "Responses API adapter: Map-based JSON schema not supported, using json_object format instead"
              )
              TextResponseConfig(ResponseFormat.JsonObject)
          }
        }
    }

    val reasoning: Option[ReasoningConfig] = {
      val effort = settings.reasoning_effort.map(
        ChatCompletionSettingsConversions.responsesReasoningEffort(settings.model, _)
      )
      val mode = settings.responsesReasoningMode

      if (effort.isEmpty && mode.isEmpty) None
      else Some(ReasoningConfig(effort = effort, mode = mode))
    }

    val samplingUnsupported =
      ChatCompletionSettingsConversions.responsesSamplingUnsupported(settings.model)
    if (samplingUnsupported) {
      val dropped = Seq(
        settings.temperature.filter(_ != 1d).map(_ => "temperature"),
        settings.top_p.filter(_ != 1d).map(_ => "top_p"),
        settings.top_logprobs.map(_ => "top_logprobs")
      ).flatten
      if (dropped.nonEmpty)
        logger.warn(
          s"${settings.model} model doesn't support ${dropped.mkString(", ")} on the Responses API, dropping."
        )
    }

    CreateModelResponseSettings(
      model = settings.model,
      instructions = instructions,
      maxOutputTokens = settings.max_tokens,
      metadata = if (settings.metadata.nonEmpty) Some(settings.metadata) else None,
      parallelToolCalls = settings.parallel_tool_calls,
      reasoning = reasoning,
      // The chat-completion adapter is stateless (full history is sent each call), so default to
      // not persisting responses server-side (avoids the provider's default 30-day retention),
      // while still honoring an explicit `store` if the caller opts in.
      store = settings.store.orElse(Some(false)),
      temperature = if (samplingUnsupported) None else settings.temperature,
      text = text,
      toolChoice = toolChoice,
      tools = tools,
      topP = if (samplingUnsupported) None else settings.top_p,
      user = settings.user,
      serviceTier = settings.service_tier.map(_.toString),
      topLogprobs = if (samplingUnsupported) None else settings.top_logprobs,
      multiAgent = settings.responsesMultiAgent
    )
  }

  // ---- OpenAIResponsesChatCompletionMappingExt impl ----

  override def toOpenAIChatCompletionResponse(
    response: Response
  ): ChatCompletionResponse = {
    warnUnrepresentableOutputs(response)

    val (textOpt, refusalOpt) = extractTextAndRefusal(response)
    val finishReason =
      toFinishReason(response, refusalOpt.isDefined && textOpt.isEmpty).map {
        case "stop" if response.output.exists(_.isInstanceOf[MCPApprovalRequest]) =>
          "approval_required"
        case other => other
      }

    ChatCompletionResponse(
      id = response.id,
      created = response.createdAt,
      model = response.model,
      system_fingerprint = None,
      choices = Seq(
        ChatCompletionChoiceInfo(
          message = AssistantMessage(
            content = textOpt.getOrElse(""),
            refusal = refusalOpt
          ),
          index = 0,
          finish_reason = finishReason,
          logprobs = None
        )
      ),
      usage = response.usage.map(toUsageInfo),
      originalResponse = Some(response)
    )
  }

  private def toOpenAIToolCompletionResponse(
    response: Response
  ): ChatToolCompletionResponse = {
    warnUnrepresentableOutputs(response)

    val functionCalls = response.outputFunctionCalls
    val toolCalls: Seq[(String, ToolCallSpec)] = functionCalls.map { fc =>
      (fc.callId, FunctionCallSpec(fc.name, fc.arguments))
    }
    val (textOpt, refusalOpt) = extractTextAndRefusal(response)
    val refusalOnly = refusalOpt.isDefined && textOpt.isEmpty && toolCalls.isEmpty
    // chat completions report 'tool_calls' (not 'stop') when the model asked for tool calls;
    // a run paused for approval reports 'approval_required' (see toolApprovalRequests)
    val awaitingApproval =
      response.output.exists(_.isInstanceOf[MCPApprovalRequest])
    val finishReason = toFinishReason(response, refusalOnly).map {
      case "stop" if awaitingApproval   => "approval_required"
      case "stop" if toolCalls.nonEmpty => "tool_calls"
      case other                        => other
    }

    ChatToolCompletionResponse(
      id = response.id,
      created = response.createdAt,
      model = response.model,
      system_fingerprint = None,
      choices = Seq(
        ChatToolCompletionChoiceInfo(
          message = AssistantToolMessage(
            content = textOpt,
            name = None,
            tool_calls = toolCalls
          ),
          index = 0,
          finish_reason = finishReason
        )
      ),
      usage = response.usage.map(toUsageInfo),
      originalResponse = Some(response)
    )
  }

  private def extractTextAndRefusal(
    response: Response
  ): (Option[String], Option[String]) = {
    val texts = response.outputMessageContents.collect { case e: OutputText => e.text }
    val refusals = response.outputMessageContents.collect { case r: Refusal => r.refusal }
    val textOpt = if (texts.isEmpty) None else Some(texts.mkString("\n"))
    val refusalOpt = if (refusals.isEmpty) None else Some(refusals.mkString("\n"))
    (textOpt, refusalOpt)
  }

  private def warnUnrepresentableOutputs(response: Response): Unit = {
    val unrepresented = response.output.collect {
      case _: Message.OutputContent => None
      case _: FunctionToolCall      => None
      // surfaced via ToolApprovalSettingsOps (response.toolApprovalRequests)
      case _: MCPApprovalRequest => None
      // a multi-agent run's internal delegation traffic
      case _: MultiAgentCall | _: MultiAgentCallOutput | _: AgentMessage => None
      case other                                                         => Some(other.`type`)
    }.flatten

    if (unrepresented.nonEmpty) {
      logger.warn(
        "Responses API adapter: dropping output items not representable in a chat completion: " +
          unrepresented.distinct.mkString(", ") +
          " (full data preserved in ChatCompletionResponse.originalResponse)"
      )
    }
  }

  private def toFinishReason(
    response: Response,
    refusalOnly: Boolean
  ): Option[String] = {
    if (refusalOnly) Some("content_filter")
    else
      response.status match {
        case ModelStatus.Completed => Some("stop")
        case ModelStatus.Incomplete =>
          response.incompleteDetails.map(_.reason) match {
            case Some("content_filter")    => Some("content_filter")
            case Some("max_output_tokens") => Some("length")
            case _                         => Some("length")
          }
        case ModelStatus.Failed    => Some("error")
        case ModelStatus.Cancelled => Some("stop")
        case _                     => None
      }
  }

  private def toUsageInfo(usage: UsageInfo): ChatUsageInfo =
    ChatUsageInfo(
      prompt_tokens = usage.inputTokens,
      total_tokens = usage.totalTokens,
      completion_tokens = Some(usage.outputTokens),
      prompt_tokens_details = usage.inputTokensDetails.map(d =>
        PromptTokensDetails(
          cached_tokens = d.cachedTokens.getOrElse(0),
          audio_tokens = None
        )
      ),
      completion_tokens_details = usage.outputTokensDetails.map(d =>
        CompletionTokenDetails(
          reasoning_tokens = Some(d.reasoningTokens)
        )
      )
    )

  override def close(): Unit = {
    underlying.close()
  }
}

object OpenAIResponsesChatCompletionService {

  /**
   * The Responses tools of a chat-completion request: function tools as `function`, the
   * provider-neutral [[ChatCompletionTool.MCPServerTool]]s as `mcp` tools (approval required
   * only when asked for via `requireApproval` - the run then pauses with
   * `ChatChunk.ToolApprovalRequest`s, answered via `setToolApprovalDecisions`) and the
   * [[ChatCompletionTool.SkillTool]]s as ONE hosted `shell` tool with the skills loaded into
   * its `container_auto` environment, plus the Responses-native tools from
   * `settings.setResponsesTools(...)` (web search, code interpreter, file search, MCP, ...).
   */
  private[openaiscala] def toResponsesTools(
    tools: Seq[ChatCompletionTool],
    settings: CreateChatCompletionSettings
  ): Seq[Tool] = {
    val functionTools = tools.collect { case ft: AssistantTool.FunctionTool =>
      ResponsesFunctionTool(
        ft.name,
        ft.parameters,
        ft.strict.getOrElse(false),
        ft.description
      )
    }

    val mcpTools = tools.collect { case mcp: ChatCompletionTool.MCPServerTool =>
      MCPTool(
        serverLabel = mcp.name,
        serverUrl = Some(mcp.url),
        authorization = mcp.authorizationToken,
        headers = if (mcp.headers.nonEmpty) Some(mcp.headers) else None,
        allowedTools =
          if (mcp.allowedTools.nonEmpty) Some(MCPAllowedTools.ToolNames(mcp.allowedTools))
          else None,
        requireApproval = Some(
          if (mcp.requireApproval) MCPRequireApproval.Setting.Always
          else MCPRequireApproval.Setting.Never
        ),
        serverDescription = mcp.description
      )
    }

    val skills = tools.collect { case skill: ChatCompletionTool.SkillTool =>
      ShellSkill.Reference(skill.skillId, skill.version)
    }
    val shellTool =
      if (skills.nonEmpty) Seq(ShellTool(ShellEnvironment.ContainerAuto(skills = skills)))
      else Nil

    functionTools ++ mcpTools ++ shellTool ++ settings.responsesTools
  }

  /**
   * The chat-completion adapter over a Responses API backend. A backend marked
   * [[io.cequence.openaiscala.service.ResponsesToolApprovalsUnsupported]] (e.g. Perplexity)
   * gets one that refuses approval decisions and `MCPServerTool(requireApproval = true)`.
   */
  def apply(
    underlying: OpenAIResponsesService with CloseableService
  )(
    implicit ec: ExecutionContext
  ): OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra
    with OpenAIResponsesChatCompletionMappingExt =
    new OpenAIResponsesChatCompletionService(
      underlying,
      underlying match {
        case streamed: OpenAIStreamedServiceExtra => Some(streamed)
        case _                                    => None
      },
      toolApprovals = !underlying.isInstanceOf[ResponsesToolApprovalsUnsupported]
    )
}
