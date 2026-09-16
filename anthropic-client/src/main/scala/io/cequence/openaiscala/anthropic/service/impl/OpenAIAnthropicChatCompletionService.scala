package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import io.cequence.openaiscala.anthropic.domain.Content.ContentBlock.{
  McpToolUseBlock,
  ServerToolUseBlock,
  TextBlock,
  ToolUseBlock
}
import io.cequence.openaiscala.anthropic.domain.Content.{ContentBlockBase, ContentBlocks}
import io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse.UsageInfo
import io.cequence.openaiscala.anthropic.domain.response.{
  CreateMessageResponse,
  MessageStreamEvent
}
import io.cequence.openaiscala.anthropic.domain.skills.{Container, SkillParams, SkillSource}
import io.cequence.openaiscala.anthropic.domain.tools.{
  CodeExecutionTool,
  CustomTool,
  MCPServerURLDefinition,
  MCPToolConfiguration,
  Tool
}
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.anthropic.domain.Message
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMessageSettings,
  ThinkingDisplay
}
import io.cequence.openaiscala.domain.response.ChatChunk
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettingsOps.RichCreateChatCompletionSettings
import io.cequence.openaiscala.anthropic.domain.{
  MessageBatch,
  MessageBatchProcessingStatus,
  MessageBatchRequest,
  MessageBatchResult
}
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.domain.{
  AssistantToolMessage,
  BaseMessage,
  ChatCompletionBatchError,
  ChatCompletionBatchInfo,
  ChatCompletionBatchRequest,
  ChatCompletionBatchResultItem,
  ChatCompletionBatchStatus,
  ChatCompletionTool,
  FunctionCallSpec
}
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionBatchService,
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}

import java.{util => ju}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

private[service] class OpenAIAnthropicChatCompletionService(
  underlying: AnthropicService
)(
  implicit executionContext: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra
    with OpenAIChatCompletionBatchService {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Creates a model response for the given chat conversation.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] = {
    createMessageWithContinuation(
      toAnthropicSystemMessages(messages.filter(_.isSystem), settings) ++
        toAnthropicMessages(messages.filter(!_.isSystem), settings),
      toAnthropicSettings(settings),
      maxContinuations(settings)
    ).map(toOpenAI).recoverWith(repackAsOpenAIException)
  }

  /**
   * Creates a completion for the chat message(s) with streamed results.
   *
   * @param messages
   *   A list of messages comprising the conversation so far.
   * @param settings
   * @return
   *   chat completion response
   * @see
   *   <a href="https://platform.openai.com/docs/api-reference/chat/create">OpenAI Doc</a>
   */
  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    underlying
      .createMessageStreamedEvents(
        toAnthropicSystemMessages(messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(messages.filter(!_.isSystem), settings),
        toAnthropicSettings(settings)
      )
      .via(toOpenAIChunks)
      .mapError(toOpenAIException)

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String] = None,
    settings: CreateChatCompletionSettings = DefaultSettings.CreateChatToolCompletion
  ): Future[ChatToolCompletionResponse] = {
    val (anthropicMessages, anthropicSettings) =
      toAnthropicToolRequest(messages, tools, responseToolChoice, settings)

    createMessageWithContinuation(
      anthropicMessages,
      anthropicSettings,
      maxContinuations(settings)
    ).map(toOpenAIToolResponse).recoverWith(repackAsOpenAIException)
  }

  /**
   * Typed streaming with tools. Besides the OpenAI function tools, Anthropic-native tools set
   * via `settings.setAnthropicTools(...)` (web search, code execution, ...) and remote MCP
   * servers set via `settings.setAnthropicMcpServers(...)` are sent, and their server-side
   * calls / results arrive as server-side [[ChatChunk.ToolCall]]s / [[ChatChunk.ToolResult]]s.
   * Thinking text is requested with `display = summarized` whenever thinking is configured
   * (the 5-series models omit it by default), unless the caller pinned a display mode.
   *
   * A `pause_turn` stop (a server-side tool run that outlived its turn budget) is continued
   * transparently - see [[OpenAIAnthropicChatCompletionService.ContinuationPrompt]] - so the
   * returned `Source` is ONE turn: a single `Start`, no `Finish` at the round boundaries, one
   * final `Finish` with the last round's stop reason, and a single `Usage` summing all rounds.
   * Tool ordinals keep counting across rounds. `settings.setAnthropicMaxContinuations` caps
   * the rounds (default 6); at the cap the `pause_turn` is reported as the `Finish`.
   */
  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] = {
    val (anthropicMessages, anthropicSettings) =
      toAnthropicToolRequest(messages, tools, responseToolChoice, settings)

    val settingsFinal = anthropicSettings.copy(
      thinking = anthropicSettings.thinking.map(thinking =>
        thinking.copy(display = thinking.display.orElse(Some(ThinkingDisplay.summarized)))
      )
    )

    streamWithContinuation(anthropicMessages, settingsFinal, maxContinuations(settings))
      .mapError(toOpenAIException)
  }

  private def maxContinuations(settings: CreateChatCompletionSettings): Int =
    settings.anthropicMaxContinuations.getOrElse(
      OpenAIAnthropicChatCompletionService.DefaultMaxContinuations
    )

  // -- pause_turn continuation --

  /**
   * Whether the API asked for the turn to be continued: `pause_turn`, or a `tool_use` stop
   * whose last block is a server-side / MCP call the API never got to run (no client-side tool
   * is involved, so there is nothing for the caller to do but resume).
   */
  private def shouldContinue(
    stopReason: Option[String],
    blocks: Seq[ContentBlockBase]
  ): Boolean =
    stopReason.contains("pause_turn") ||
      (stopReason.contains("tool_use") && hasTrailingServerToolUse(blocks))

  private def hasTrailingServerToolUse(blocks: Seq[ContentBlockBase]): Boolean =
    blocks.lastOption.exists(_.content match {
      case _: ServerToolUseBlock | _: McpToolUseBlock => true
      case _                                          => false
    })

  /**
   * The conversation to resume with: the assistant turn so far echoed back as is (thinking
   * blocks with their signatures, server-side calls and results included), minus a trailing
   * server-side call that has no result yet (the API rejects it unmatched), followed by the
   * continuation user turn.
   */
  private def continuationMessages(
    messages: Seq[Message],
    blocks: Seq[ContentBlockBase]
  ): Seq[Message] = {
    val replayable = if (hasTrailingServerToolUse(blocks)) blocks.dropRight(1) else blocks
    val assistantTurn =
      if (replayable.nonEmpty) Seq(Message.AssistantMessageContent(replayable)) else Nil

    messages ++ assistantTurn :+
      Message.UserMessage(OpenAIAnthropicChatCompletionService.ContinuationPrompt)
  }

  private def createMessageWithContinuation(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings,
    maxContinuations: Int
  ): Future[CreateMessageResponse] = {
    def loop(
      current: Seq[Message],
      continuation: Int,
      priorBlocks: Seq[ContentBlockBase],
      priorUsage: Option[UsageInfo]
    ): Future[CreateMessageResponse] =
      underlying.createMessage(current, settings).flatMap { response =>
        val blocks = response.content.blocks
        val usage = priorUsage.fold(response.usage)(sumUsage(_, response.usage))
        val continueRequested = shouldContinue(response.stop_reason, blocks)

        if (continueRequested && continuation < maxContinuations) {
          logger.info(
            s"Anthropic adapter: stop_reason ${response.stop_reason.getOrElse("-")} after " +
              s"${blocks.size} block(s), continuing the turn (${continuation + 1}/$maxContinuations)"
          )
          loop(
            continuationMessages(current, blocks),
            continuation + 1,
            priorBlocks ++ blocks,
            Some(usage)
          )
        } else {
          if (continueRequested)
            logger.warn(
              s"Anthropic adapter: stop_reason ${response.stop_reason.getOrElse("-")} but the " +
                s"continuation cap ($maxContinuations) is reached - returning the turn as is"
            )
          Future.successful(
            response.copy(content = ContentBlocks(priorBlocks ++ blocks), usage = usage)
          )
        }
      }

    loop(messages, 0, Nil, None)
  }

  private def sumUsage(
    a: UsageInfo,
    b: UsageInfo
  ): UsageInfo =
    UsageInfo(
      input_tokens = a.input_tokens + b.input_tokens,
      output_tokens = a.output_tokens + b.output_tokens,
      cache_creation_input_tokens =
        sumOpt(a.cache_creation_input_tokens, b.cache_creation_input_tokens),
      cache_read_input_tokens = sumOpt(a.cache_read_input_tokens, b.cache_read_input_tokens)
    )

  private def sumOpt(
    a: Option[Int],
    b: Option[Int]
  ): Option[Int] =
    (a, b) match {
      case (None, None) => None
      case _            => Some(a.getOrElse(0) + b.getOrElse(0))
    }

  private def sumOpenAIUsage(
    a: OpenAIUsageInfo,
    b: OpenAIUsageInfo
  ): OpenAIUsageInfo =
    OpenAIUsageInfo(
      prompt_tokens = a.prompt_tokens + b.prompt_tokens,
      completion_tokens = sumOpt(a.completion_tokens, b.completion_tokens),
      total_tokens = a.total_tokens + b.total_tokens,
      prompt_tokens_details = (a.prompt_tokens_details, b.prompt_tokens_details) match {
        case (Some(x), Some(y)) =>
          Some(
            x.copy(
              cached_tokens = x.cached_tokens + y.cached_tokens,
              audio_tokens = sumOpt(x.audio_tokens, y.audio_tokens)
            )
          )
        case (x, y) => x.orElse(y)
      },
      completion_tokens_details =
        (a.completion_tokens_details, b.completion_tokens_details) match {
          case (Some(x), Some(y)) =>
            Some(
              x.copy(
                reasoning_tokens = sumOpt(x.reasoning_tokens, y.reasoning_tokens),
                accepted_prediction_tokens =
                  sumOpt(x.accepted_prediction_tokens, y.accepted_prediction_tokens),
                rejected_prediction_tokens =
                  sumOpt(x.rejected_prediction_tokens, y.rejected_prediction_tokens)
              )
            )
          case (x, y) => x.orElse(y)
        }
    )

  // what a finished round hands to the next one
  private final case class ContinueTurn(
    blocks: Seq[ContentBlockBase],
    nextToolOrdinal: Int,
    usage: Option[OpenAIUsageInfo]
  )

  /**
   * One typed stream over as many streamed requests as the turn needs. Each round runs its own
   * [[StreamedChunkMapper]]; at `message_delta` the round decides whether to hand over -
   * dropping its own `Finish` / `Usage` (and the next round drops its `Start`) - or to end the
   * turn, in which case its `Usage` is replaced by the sum over all rounds.
   */
  private def streamWithContinuation(
    messages: Seq[Message],
    settings: AnthropicCreateMessageSettings,
    maxContinuations: Int
  ): Source[ChatChunk, NotUsed] = {
    def round(
      current: Seq[Message],
      continuation: Int,
      toolOrdinal: Int,
      priorUsage: Option[OpenAIUsageInfo]
    ): Source[ChatChunk, NotUsed] =
      underlying
        .createMessageStreamedEvents(current, settings)
        .via(Flow[MessageStreamEvent].statefulMapConcat { () =>
          val mapper = new StreamedChunkMapper(toolOrdinal)

          (event: MessageStreamEvent) =>
            event match {
              // one Start per turn
              case _: MessageStreamEvent.MessageStart if continuation > 0 =>
                mapper(event)
                Nil

              case delta: MessageStreamEvent.MessageDelta =>
                val chunks = mapper(delta)
                val blocks = mapper.contentBlocks
                val continueRequested = shouldContinue(delta.stopReason, blocks)
                val totalUsage =
                  chunks.collectFirst { case ChatChunk.Usage(u) => u }.map { roundUsage =>
                    priorUsage.fold(roundUsage)(sumOpenAIUsage(_, roundUsage))
                  }

                if (continueRequested && continuation < maxContinuations) {
                  logger.info(
                    s"Anthropic adapter: stop_reason ${delta.stopReason.getOrElse("-")} after " +
                      s"${blocks.size} block(s), continuing the streamed turn " +
                      s"(${continuation + 1}/$maxContinuations)"
                  )
                  val boundaryFree = chunks.filter {
                    case _: ChatChunk.Finish | _: ChatChunk.Usage => false
                    case _                                        => true
                  }
                  List(
                    Left(boundaryFree),
                    Right(
                      ContinueTurn(
                        blocks,
                        mapper.nextToolOrdinal,
                        totalUsage.orElse(priorUsage)
                      )
                    )
                  )
                } else {
                  if (continueRequested)
                    logger.warn(
                      s"Anthropic adapter: stop_reason ${delta.stopReason.getOrElse("-")} but " +
                        s"the continuation cap ($maxContinuations) is reached - ending the " +
                        "streamed turn as is"
                    )
                  List(Left(chunks.map {
                    case ChatChunk.Usage(_) if totalUsage.isDefined =>
                      ChatChunk.Usage(totalUsage.get)
                    case chunk => chunk
                  }))
                }

              case other =>
                List(Left(mapper(other)))
            }
        })
        .flatMapConcat {
          case Left(chunks) =>
            Source(chunks)
          case Right(next) =>
            round(
              continuationMessages(current, next.blocks),
              continuation + 1,
              next.nextToolOrdinal,
              next.usage
            )
        }

    round(messages, 0, 0, None)
  }

  /**
   * OpenAI function tools -> Anthropic custom tools (+ Anthropic-native tools from
   * extra_params), the provider-neutral tools -> the MCP connector (`mcp_servers`; a
   * [[ChatCompletionTool.MCPServerTool]] with custom `headers` is refused, the connector takes
   * a bearer token only) and the skills container (`container.skills`, with the code execution
   * tool added when missing - skills run there), tool choice with its forced-tool fallback,
   * and the converted messages.
   */
  private[impl] def toAnthropicToolRequest(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): (Seq[Message], AnthropicCreateMessageSettings) = {
    val functionTools = tools.collect { case ft: FunctionTool =>
      CustomTool(
        name = ft.name,
        inputSchema = ft.parameters,
        description = ft.description
      )
    }

    val mcpServers = tools.collect { case mcp: ChatCompletionTool.MCPServerTool =>
      if (mcp.headers.nonEmpty)
        throw new OpenAIScalaClientException(
          s"Anthropic's MCP connector sends a bearer token only - MCPServerTool '${mcp.name}' " +
            s"carries custom headers (${mcp.headers.keys.mkString(", ")}) it cannot deliver."
        )
      MCPServerURLDefinition(
        name = mcp.name,
        url = mcp.url,
        authorizationToken = mcp.authorizationToken,
        toolConfiguration =
          if (mcp.allowedTools.nonEmpty)
            Some(MCPToolConfiguration(allowedTools = mcp.allowedTools, enabled = Some(true)))
          else None
      )
    }

    val skills = tools.collect { case skill: ChatCompletionTool.SkillTool =>
      SkillParams(
        skillId = skill.skillId,
        `type` = skill.source match {
          case ChatCompletionTool.SkillSource.Provider => SkillSource.anthropic
          case ChatCompletionTool.SkillSource.Custom   => SkillSource.custom
        },
        version = skill.version
      )
    }
    val container =
      if (skills.nonEmpty) Some(Container(skills = skills)) else None

    val nativeTools = settings.anthropicTools
    // skills run in the code execution container
    val codeExecutionForSkills =
      if (skills.nonEmpty && !nativeTools.exists(_.isInstanceOf[CodeExecutionTool]))
        Seq(Tool.codeExecution())
      else Nil

    val allTools = functionTools ++ nativeTools ++ codeExecutionForSkills

    if (allTools.isEmpty && responseToolChoice.isDefined)
      logger.warn(
        s"Anthropic adapter: tool choice '${responseToolChoice.get}' requested but no tools were provided"
      )

    // an explicit forced choice is always sent (without tools the API rejects it loudly rather
    // than the choice being dropped silently)
    val (anthropicToolChoice, extraSystemMessages) =
      if (allTools.nonEmpty || responseToolChoice.isDefined) {
        val disableParallel = settings.parallel_tool_calls.map(!_)
        val (toolChoice, extra) =
          toAnthropicToolChoice(settings.model, responseToolChoice, disableParallel)
        (Some(toolChoice), extra)
      } else
        (None, Nil)

    val baseSettings = toAnthropicSettings(settings)
    val anthropicSettings = baseSettings.copy(
      tools = allTools,
      tool_choice = anthropicToolChoice,
      mcp_servers = baseSettings.mcp_servers ++ mcpServers,
      container = container.orElse(baseSettings.container)
    )

    val anthropicMessages =
      toAnthropicSystemMessages(
        messages.filter(_.isSystem) ++ extraSystemMessages,
        settings
      ) ++
        toAnthropicMessages(messages.filter(!_.isSystem), settings)

    (anthropicMessages, anthropicSettings)
  }

  // -- Batch processing (provider-agnostic) --

  override def createChatCompletionBatch(
    requests: Seq[ChatCompletionBatchRequest],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionBatchInfo] = {
    val anthropicSettings = toAnthropicSettings(settings)

    val anthropicRequests = requests.map { request =>
      MessageBatchRequest(
        customId = request.customId,
        messages = toAnthropicSystemMessages(request.messages.filter(_.isSystem), settings) ++
          toAnthropicMessages(request.messages.filter(!_.isSystem), settings),
        settings = anthropicSettings
      )
    }

    underlying
      .createMessageBatch(anthropicRequests)
      .map(toBatchInfo)
      .recoverWith(repackAsOpenAIException)
  }

  override def getChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    underlying.getMessageBatch(batchId).map(toBatchInfo).recoverWith(repackAsOpenAIException)

  override def retrieveChatCompletionBatchResults(
    batchId: String,
    model: String
  ): Future[Seq[ChatCompletionBatchResultItem]] =
    underlying
      .retrieveMessageBatchResults(batchId)
      .map(_.map { item =>
        val result = item.result match {
          case MessageBatchResult.Succeeded(message) =>
            Right(toOpenAI(message))

          case MessageBatchResult.Errored(error, _) =>
            Left(ChatCompletionBatchError(error.message, Some(error.`type`)))

          case MessageBatchResult.Canceled =>
            Left(
              ChatCompletionBatchError(
                "The request was canceled before it could be processed.",
                Some("canceled")
              )
            )

          case MessageBatchResult.Expired =>
            Left(
              ChatCompletionBatchError(
                "The batch expired before the request could be processed.",
                Some("expired")
              )
            )
        }

        ChatCompletionBatchResultItem(item.customId, result)
      })
      .recoverWith(repackAsOpenAIException)

  override def cancelChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[ChatCompletionBatchInfo] =
    underlying
      .cancelMessageBatch(batchId)
      .map(toBatchInfo)
      .recoverWith(repackAsOpenAIException)

  override def deleteChatCompletionBatch(
    batchId: String,
    model: String
  ): Future[Unit] =
    underlying.deleteMessageBatch(batchId).map(_ => ()).recoverWith(repackAsOpenAIException)

  private def toBatchInfo(batch: MessageBatch): ChatCompletionBatchInfo = {
    val status = batch.processingStatus match {
      case MessageBatchProcessingStatus.ended =>
        // canceled batches also end as `ended`; per-request outcomes ride on the results
        ChatCompletionBatchStatus.Completed
      case _ =>
        // in_progress, canceling
        ChatCompletionBatchStatus.InProgress
    }

    ChatCompletionBatchInfo(batch.id, status, batch.processingStatus.toString)
  }

  private def toOpenAIToolResponse(
    response: io.cequence.openaiscala.anthropic.domain.response.CreateMessageResponse
  ): ChatToolCompletionResponse = {
    val toolCalls = response.content.blocks.collect {
      case ContentBlockBase(ToolUseBlock(id, name, input), _) =>
        (
          id,
          FunctionCallSpec(name, input.toString): io.cequence.openaiscala.domain.ToolCallSpec
        )
    }

    val textContent = response.content.blocks.collect {
      case ContentBlockBase(TextBlock(text, _), _) => text
    }

    val message = AssistantToolMessage(
      content = if (textContent.nonEmpty) Some(textContent.mkString("\n")) else None,
      name = None,
      tool_calls = toolCalls
    )

    ChatToolCompletionResponse(
      id = response.id,
      created = new ju.Date(),
      model = response.model,
      system_fingerprint = response.stop_reason,
      choices = Seq(
        ChatToolCompletionChoiceInfo(
          message = message,
          index = 0,
          finish_reason = response.stop_reason
        )
      ),
      usage = Some(toOpenAI(response.usage)),
      // the raw Anthropic response (with a continued turn's rounds concatenated) - what the
      // provider ran, server-side tool blocks included
      originalResponse = Some(response)
    )
  }

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()
}

object OpenAIAnthropicChatCompletionService {

  /**
   * The user turn appended after the echoed assistant content when a `pause_turn` (or a
   * `tool_use` ending in an unrun server-side call) is continued.
   */
  val ContinuationPrompt = "Continue from where you left off."

  /**
   * Rounds a turn may be continued for, unless `setAnthropicMaxContinuations` says otherwise.
   */
  val DefaultMaxContinuations = 6

  def apply(
    underlying: AnthropicService
  )(
    implicit executionContext: ExecutionContext
  ): OpenAIChatCompletionService with OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIAnthropicChatCompletionService(underlying)
}
