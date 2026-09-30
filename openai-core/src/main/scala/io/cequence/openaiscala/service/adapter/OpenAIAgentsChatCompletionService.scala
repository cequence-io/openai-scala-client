package io.cequence.openaiscala.service.adapter

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import io.cequence.openaiscala.JsonFormats.jsonSchemaFormat
import io.cequence.openaiscala.domain.agents.AgentSessionEvent.{
  ItemAdded,
  ItemDone,
  OutputTextDelta,
  OutputTextDone,
  ReasoningSummaryTextDelta,
  ReasoningSummaryTextDone,
  SessionUpdated,
  TurnCancelled,
  TurnCompleted,
  TurnFailed,
  TurnUpdated
}
import io.cequence.openaiscala.domain.agents._
import io.cequence.openaiscala.domain.response.ChatChunk._
import io.cequence.openaiscala.domain.response._
import io.cequence.openaiscala.domain.responsesapi.MultiAgentConfig
import io.cequence.openaiscala.domain.settings.ResponsesChatCompletionSettingsOps._
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.domain.settings.{
  ChatCompletionResponseFormatType,
  CreateChatCompletionSettings
}
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  AssistantTool,
  JsonSchema,
  AssistantToolMessage,
  BaseMessage,
  ChatCompletionTool,
  DeveloperMessage,
  FunctionCallSpec,
  ImageURLContent,
  MessageSpec,
  SystemMessage,
  TextContent,
  ToolMessage,
  UserMessage,
  UserSeqMessage
}
import io.cequence.openaiscala.service.{
  ChatChunks,
  OpenAIAgentsService,
  OpenAIChatCompletionExtra,
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra,
  OpenAIStreamedServiceExtra
}
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException,
  OpenAIScalaServerErrorException,
  OpenAIScalaTokenCountExceededException,
  OpenAIScalaUnauthorizedException
}
import io.cequence.wsclient.JsonUtil
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, JsValue, Json}

import java.util.concurrent.atomic.AtomicBoolean
import java.{util => ju}
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
 * An OpenAI chat-completion view of the OpenAI Agents API (beta): each call runs one session
 * turn - an inline agent (`settings.model`, the system / developer messages as its
 * instructions, `reasoning_effort`, `verbosity`, `service_tier`, json_schema output, the
 * call's tools plus [[agentTools]]) or the reusable [[agentId]] - in [[environment]], and
 * streams it as typed [[ChatChunk]]s:
 *   - the `final_answer` messages as `Text`, the agent's `commentary` (its progress notes) and
 *     reasoning summaries as `Thinking`
 *   - commands run in the environment, MCP calls, web searches and subagent calls as
 *     server-side tool calls / results (plus `CodeExecution*` / `WebSearch` chunks); the
 *     environment / subagent events as `Other`
 *   - `Finish(stop)` when the session idles
 *
 * The Agents API takes user messages only, so a multi-turn history is folded into one message
 * with role labels (a lone user message is sent as is, images included). Sampling settings
 * (temperature, top_p, max_tokens, penalties, stop, logprobs, seed, n) have no Agents API
 * equivalent and are ignored with a warning.
 *
 * '''Client function tools''' (the call's `FunctionTool`s): a call pauses the session
 * (`requires_action`) - the stream ends with the pending calls as `ToolCall`s and
 * `Finish(tool_calls)`, and the session is KEPT; the next call ending with the `ToolMessage`s
 * answering them resumes it (its earlier messages are ignored - the history is in the
 * session): the adapter subscribes to the session's events, posts the results once the
 * subscription is live, and streams the rest of the turn (the replayed items it already
 * reported are skipped). Answer promptly - the platform moves on after a while. The paused
 * sessions live in this adapter instance (a resume must reach it); [[close]] cancels and
 * deletes the ones never resumed. Tool results for a session this adapter does not know start
 * a new session instead (the history folded as usual).
 *
 * Sessions are deleted once their turn ends (unless [[deleteSessionsAfterUse]] is off);
 * approval decisions, Responses-native tools, skills and MCP tools requiring approval are
 * refused. The session id is the response id / `Start` id.
 *
 * @param agentId
 *   a reusable agent to run (its model, instructions, tools and output settings apply - the
 *   call's model, reasoning effort, verbosity, service tier and json_schema are ignored, its
 *   system messages are sent as part of the input; per-call tools are refused, and so are
 *   [[agentTools]] / [[multiAgent]])
 * @param agentTools
 *   server-side tools for the inline agent (web search, MCP servers, computer use, ...)
 * @param multiAgent
 *   server-hosted multi-agent execution for the inline agent
 */
final class OpenAIAgentsChatCompletionService(
  underlying: OpenAIAgentsService with OpenAIStreamedServiceExtra,
  agentId: Option[String] = None,
  environment: AgentEnvironment = AgentEnvironment.NoEnvironment,
  agentTools: Seq[AgentTool] = Nil,
  multiAgent: Option[MultiAgentConfig] = None,
  deleteSessionsAfterUse: Boolean = true
)(
  implicit ec: ExecutionContext,
  materializer: Materializer
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra {

  import OpenAIAgentsChatCompletionService._

  require(
    agentId.isEmpty || (agentTools.isEmpty && multiAgent.isEmpty),
    "agentTools / multiAgent configure the inline agent - set them on the reusable agent (agentId) instead."
  )

  private val logger = LoggerFactory.getLogger(getClass)

  // the sessions paused on client function calls, by session id
  private val pausedSessions = TrieMap.empty[String, PausedSession]

  // session cleanups (DELETE, cancel + DELETE) still running - close() waits for them
  private val cleanupsInFlight = TrieMap.empty[Future[Unit], Unit]

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    createChatToolCompletionStreamed(messages, Nil, None, settings).assembled.map { a =>
      ChatCompletionResponse(
        id = a.id.getOrElse(""),
        created = new ju.Date(),
        model = a.model.getOrElse(settings.model),
        system_fingerprint = None,
        choices = Seq(
          ChatCompletionChoiceInfo(
            message = AssistantMessage(a.text),
            index = 0,
            finish_reason = a.finishReason.map(_.toString),
            logprobs = None
          )
        ),
        usage = a.usage,
        originalResponse = None
      )
    }

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    createChatToolCompletionStreamed(
      messages,
      tools,
      responseToolChoice,
      settings
    ).assembled.map { a =>
      ChatToolCompletionResponse(
        id = a.id.getOrElse(""),
        created = new ju.Date(),
        model = a.model.getOrElse(settings.model),
        system_fingerprint = None,
        choices = Seq(
          ChatToolCompletionChoiceInfo(
            message = AssistantToolMessage(
              content = if (a.text.nonEmpty) Some(a.text) else None,
              tool_calls = a.toolCalls
                .filterNot(_.serverSide)
                .map(call => (call.callId, FunctionCallSpec(call.toolName, call.arguments)))
            ),
            index = 0,
            finish_reason = a.finishReason.map(_.toString)
          )
        ),
        usage = a.usage
      )
    }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    createChatToolCompletionStreamed(messages, Nil, None, settings).via(
      ChatChunks.toOpenAIChunks
    )

  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] =
    Try(plan(messages, tools, responseToolChoice, settings)) match {
      case Failure(e)             => Source.failed(e)
      case Success(Left(resume))  => resumeTurn(resume)
      case Success(Right(create)) => startTurn(create)
    }

  /**
   * Cancels and deletes the sessions paused on client function calls, waits (up to 30 s) for
   * every session cleanup still running - e.g. the DELETE of a turn whose consumer stopped
   * reading at its `Finish` - then closes the service.
   */
  override def close(): Unit = {
    pausedSessions.keys.toSeq.foreach(cancelAndDelete)
    Try(Await.ready(Future.sequence(cleanupsInFlight.keys.toSeq), 30.seconds))
    pausedSessions.clear()
    underlying.close()
  }

  // ---- planning ----

  private def plan(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Either[Resume, Create] = {
    if (settings.toolApprovalDecisions.nonEmpty)
      throw new OpenAIScalaClientException(
        "The Agents API chat adapter cannot resume a run paused for tool approval."
      )
    if (settings.responsesTools.nonEmpty)
      throw new OpenAIScalaClientException(
        "Responses-native tools (setResponsesTools) are not Agents API tools - pass AgentTools to the adapter (agentTools) instead."
      )

    val toolOutputs = messages.reverse.takeWhile(_.isInstanceOf[ToolMessage]).reverse.collect {
      case tool: ToolMessage => tool
    }
    val paused = pausedSessions.values.find(session =>
      toolOutputs.nonEmpty && toolOutputs.forall(t =>
        session.pendingCalls.contains(t.tool_call_id)
      )
    )

    paused match {
      case Some(session) => Left(Resume(session, toolOutputs))
      case None =>
        if (toolOutputs.nonEmpty)
          logger.warn(
            "Agents API chat adapter: the tool results answer no session paused in this adapter - starting a new session with the history."
          )
        Right(toCreate(messages, tools, responseToolChoice, settings))
    }
  }

  private def toCreate(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Create = {
    warnIgnored(settings, responseToolChoice)

    val (systemMessages, conversation) = messages.partition {
      case _: SystemMessage | _: DeveloperMessage => true
      case MessageSpec(role, _, _) => Set("system", "developer").contains(role.toString)
      case _                       => false
    }
    val systemTexts = systemMessages.collect {
      case SystemMessage(content, _)    => content
      case DeveloperMessage(content, _) => content
      case MessageSpec(_, content, _)   => content
    }

    val callTools = tools.map {
      case ft: AssistantTool.FunctionTool =>
        AgentTool.Function(
          ft.name,
          ft.description.getOrElse(""),
          Json.toJson(ft.parameters).as[JsObject]
        ): AgentTool
      case mcp: ChatCompletionTool.MCPServerTool =>
        if (mcp.requireApproval)
          throw new OpenAIScalaClientException(
            s"MCPServerTool(${mcp.name}) requires approval, which the Agents API chat adapter cannot ask for."
          )
        AgentTool.Mcp(
          mcp.name,
          McpTransport.Http(
            mcp.url,
            authorization = mcp.authorizationToken,
            headers = mcp.headers
          ),
          allowedTools = if (mcp.allowedTools.nonEmpty) Some(mcp.allowedTools) else None
        ): AgentTool
      case other =>
        throw new OpenAIScalaClientException(
          s"${other.getClass.getSimpleName} is not supported by the Agents API chat adapter (skills belong to the environment)."
        )
    }

    val (config, input) = agentId match {
      case Some(id) =>
        if (callTools.nonEmpty)
          throw new OpenAIScalaClientException(
            "Per-call tools cannot be added to a reusable agent (agentId) - configure them on the agent."
          )
        val agentSettings = Seq(
          "reasoning_effort" -> settings.reasoning_effort.isDefined,
          "verbosity" -> settings.verbosity.isDefined,
          "service_tier" -> settings.service_tier.isDefined,
          "json_schema" -> settings.response_format_type.contains(
            ChatCompletionResponseFormatType.json_schema
          )
        ).collect { case (name, true) => name }
        if (agentSettings.nonEmpty)
          logger.warn(
            s"Agents API chat adapter: ignoring ${agentSettings.mkString(", ")} - the reusable agent $id carries its own."
          )
        (None, toInput(conversation, systemTexts))
      case None =>
        val schemaFormat: Option[JsObject] =
          if (
            settings.response_format_type.contains(
              ChatCompletionResponseFormatType.json_schema
            )
          )
            settings.jsonSchema.map { schemaDef =>
              // the Agents API validates every schema as strict (closed objects - live
              // 2026-09-30), whatever `strict` says: every object is closed, the map form too
              val schema = schemaDef.structure match {
                case Left(jsonSchema) =>
                  Json.toJson(
                    JsonSchema
                      .setAdditionalPropertiesToFalse(jsonSchema, overrideExisting = true)
                  )
                case Right(map) =>
                  JsonUtil.StringAnyMapFormat.writes(
                    OpenAIChatCompletionExtra.toStrictSchema(Right(map))
                  )
              }
              Json.obj("type" -> "json_schema", "schema" -> schema)
            }
          else None
        val text = JsObject(
          schemaFormat.map("format" -> _).toSeq ++
            settings.verbosity.map(v => "verbosity" -> Json.toJson(v.toString)).toSeq
        )
        val config = AgentConfig(
          model = Some(settings.model),
          instructions =
            if (systemTexts.nonEmpty) Some(systemTexts.mkString("\n\n")) else None,
          reasoning = settings.reasoning_effort.map(effort =>
            AgentReasoning(
              effort = Some(
                ChatCompletionSettingsConversions
                  .responsesReasoningEffort(settings.model, effort)
              )
            )
          ),
          text = if (text.fields.nonEmpty) Some(text) else None,
          serviceTier = settings.service_tier,
          tools =
            if (agentTools.nonEmpty || callTools.nonEmpty) Some(agentTools ++ callTools)
            else None,
          multiAgent = multiAgent
        )
        (Some(config), toInput(conversation, Nil))
    }

    Create(
      CreateAgentSessionSettings(
        agent = config,
        agentId = agentId,
        environment = environment,
        metadata = settings.metadata ++ Map("created_by" -> "openai-scala-client")
      ),
      input,
      settings.model
    )
  }

  private val ignoredSettings: Seq[(String, CreateChatCompletionSettings => Boolean)] = Seq(
    "temperature" -> (_.temperature.isDefined),
    "top_p" -> (_.top_p.isDefined),
    "max_tokens" -> (_.max_tokens.isDefined),
    "n" -> (_.n.isDefined),
    "stop" -> (_.stop.nonEmpty),
    "presence_penalty" -> (_.presence_penalty.isDefined),
    "frequency_penalty" -> (_.frequency_penalty.isDefined),
    "logit_bias" -> (_.logit_bias.nonEmpty),
    "logprobs" -> (_.logprobs.isDefined),
    "top_logprobs" -> (_.top_logprobs.isDefined),
    "seed" -> (_.seed.isDefined),
    "parallel_tool_calls" -> (_.parallel_tool_calls.isDefined)
  )

  private def warnIgnored(
    settings: CreateChatCompletionSettings,
    responseToolChoice: Option[String]
  ): Unit = {
    val ignored = ignoredSettings.collect { case (name, set) if set(settings) => name } ++
      responseToolChoice.map(_ => "a forced tool choice") ++
      (if (
         settings.response_format_type.contains(ChatCompletionResponseFormatType.json_object)
       )
         Seq("json_object (use json_schema)")
       else Nil)
    if (ignored.nonEmpty)
      logger.warn(
        s"Agents API chat adapter: ignoring ${ignored.mkString(", ")} - no Agents API equivalent."
      )
  }

  // a lone user message is sent as is; a history is folded into one labeled message
  private def toInput(
    conversation: Seq[BaseMessage],
    systemTexts: Seq[String]
  ): AgentInput = {
    val lone = systemTexts.isEmpty && conversation.size == 1

    def label(role: String) = if (lone) "" else s"$role: "

    val content: Seq[AgentInputContent] =
      systemTexts.map(t => AgentInputContent.Text(s"System: $t"): AgentInputContent) ++
        conversation.flatMap {
          case UserMessage(content, _) =>
            Seq(AgentInputContent.Text(label("User") + content))
          case UserSeqMessage(contents, _) =>
            (if (lone) Nil else Seq(AgentInputContent.Text("User:"))) ++ contents.map {
              case TextContent(text)    => AgentInputContent.Text(text): AgentInputContent
              case ImageURLContent(url) => AgentInputContent.Image(url): AgentInputContent
              case other =>
                throw new OpenAIScalaClientException(
                  s"${other.getClass.getSimpleName} is not supported by the Agents API chat adapter - a message carries text and images only."
                )
            }
          // the deprecated generic message, labeled by its role (system / developer ones are
          // the instructions)
          case MessageSpec(role, content, _) =>
            val text = role.toString match {
              case "assistant"         => s"Assistant: $content"
              case "tool" | "function" => s"Tool result: $content"
              case _                   => label("User") + content
            }
            Seq(AgentInputContent.Text(text))
          case AssistantMessage(content, _, _) =>
            Seq(AgentInputContent.Text(s"Assistant: $content"))
          case AssistantToolMessage(content, _, toolCalls) =>
            content.map(c => AgentInputContent.Text(s"Assistant: $c")).toSeq ++
              toolCalls.collect { case (id, call: FunctionCallSpec) =>
                AgentInputContent.Text(
                  s"Assistant called ${call.name}(${call.arguments}) [$id]"
                )
              }
          case ToolMessage(content, toolCallId, _) =>
            Seq(AgentInputContent.Text(s"Tool result [$toolCallId]: ${content.getOrElse("")}"))
          case other =>
            throw new OpenAIScalaClientException(
              s"Message type '${other.getClass.getSimpleName}' is not supported by the Agents API chat adapter."
            )
        }

    if (content.isEmpty)
      throw new OpenAIScalaClientException("At least one user message expected.")
    AgentInput.Messages(Seq(AgentUserMessage(content)))
  }

  // ---- the turns ----

  private def startTurn(create: Create): Source[ChatChunk, NotUsed] =
    Source.lazySource { () =>
      val state = new TurnState(create.model, None, Set.empty, Set.empty, 0)
      underlying
        .createAgentSessionStreamed(create.settings, create.input)
        .via(turnFlow(state, resumedFrom = None, posted = noPost))
        .watchTermination() {
          (
            _,
            done
          ) =>
            done.onComplete(_ => settleOnTermination(state, resumedFrom = None, noPost))
            NotUsed
        }
    }.mapMaterializedValue(_ => NotUsed)

  private def resumeTurn(resume: Resume): Source[ChatChunk, NotUsed] =
    Source.lazySource { () =>
      val paused = resume.paused

      val results = resume.toolOutputs.map { output =>
        AgentSessionInput.ToolResult(
          turnId = paused.pendingCalls(output.tool_call_id),
          callId = output.tool_call_id,
          output = Some(output.content.getOrElse(""))
        ): AgentSessionInput
      }
      val state = new TurnState(
        paused.model,
        Some(paused.sessionId),
        paused.reportedItems,
        results.collect { case r: AgentSessionInput.ToolResult => r.callId }.toSet,
        paused.nextToolIndex
      )

      // subscribe first: post the results once the subscription is live (its replay of the
      // paused turn arrives), or after a grace period - a turn that finished before the
      // subscription connected would never be delivered; never once the stream is gone. The
      // session stays registered as paused until the results are posted.
      val live = Promise[Boolean]()
      val posted = live.future.flatMap {
        case true =>
          underlying.sendAgentSessionEvents(paused.sessionId, results).map { _ =>
            state.posted = true
            pausedSessions.remove(paused.sessionId, paused)
            ()
          }
        case false => Future.successful(())
      }
      akka.pattern.after(3.seconds, materializer.system.scheduler)(
        Future.successful(live.trySuccess(true))
      )

      Source
        .single(Start(paused.sessionId, paused.model): ChatChunk)
        .concat(
          underlying
            .streamAgentSessionEvents(paused.sessionId)
            .map { event => live.trySuccess(true); event }
            .via(turnFlow(state, resumedFrom = Some(paused), posted))
            .merge(Source.future(posted).flatMapConcat(_ => Source.empty[ChatChunk]))
        )
        .watchTermination() {
          (
            _,
            done
          ) =>
            done.onComplete { _ =>
              live.trySuccess(false)
              // an in-flight POST decides whether the session is still paused
              posted.onComplete(_ =>
                settleOnTermination(state, resumedFrom = Some(paused), posted)
              )
            }
            NotUsed
        }
    }.mapMaterializedValue(_ => NotUsed)

  private val noPost = Future.successful(())

  // at the turn's end marker - synchronously, BEFORE the turn's final chunks go out, so neither
  // a resume nor close() issued by a caller who has seen them can overtake it: a session paused
  // on client calls is registered at once; a finished one is deleted (a turn that ended
  // cancelled is cancelled first - it may still be winding down), after a resume's POST outcome
  // is known (the DELETE must not overtake the results) and tracked for close(). The stream
  // completes only once this settlement has.
  private def finishTurn(
    state: TurnState,
    resumedFrom: Option[PausedSession],
    posted: Future[Unit]
  ): Future[Unit] = {
    if (state.settled.compareAndSet(false, true))
      state.settlement.completeWith(
        state.sessionId match {
          case Some(sessionId) if state.pausedOn.nonEmpty =>
            pausedSessions.put(
              sessionId,
              PausedSession(
                sessionId,
                state.model,
                state.pausedOn,
                state.reported.toSet,
                state.nextIndex
              )
            )
            Future.successful(())

          case Some(sessionId) =>
            tracked(posted.transformWith { _ =>
              resumedFrom.foreach(paused => pausedSessions.remove(sessionId, paused))
              if (!deleteSessionsAfterUse) Future.successful(())
              else if (state.idle) deleteSession(sessionId)
              else cancelAndDelete(sessionId)
            })

          case None => Future.successful(())
        }
      )
    state.settlement.future
  }

  // once the stream has terminated: a turn that reached its end was settled at its end marker
  // (a no-op here); one that did not (failed, or the consumer cancelled) is cancelled and
  // deleted - except a resume whose results were never posted: that session is still paused
  // (still registered - resumable with the same results)
  private def settleOnTermination(
    state: TurnState,
    resumedFrom: Option[PausedSession],
    posted: Future[Unit]
  ): Unit =
    if (state.ended) finishTurn(state, resumedFrom, posted)
    else
      state.sessionId.foreach { sessionId =>
        if (
          (resumedFrom.isEmpty || state.posted) && state.settled.compareAndSet(false, true)
        ) {
          resumedFrom.foreach(paused => pausedSessions.remove(sessionId, paused))
          if (deleteSessionsAfterUse) cancelAndDelete(sessionId)
        }
      }

  private def deleteSession(sessionId: String): Future[Unit] =
    tracked(
      underlying.deleteAgentSession(sessionId).map(_ => ()).recover { case e =>
        logger.warn(s"Agents API chat adapter: could not delete session $sessionId: $e")
      }
    )

  private def cancelAndDelete(sessionId: String): Future[Unit] =
    tracked(
      underlying
        .sendAgentSessionEvents(sessionId, Seq(AgentSessionInput.Cancel))
        .recover { case _ => () }
        .flatMap(_ => underlying.deleteAgentSession(sessionId))
        .map(_ => ())
        .recover { case e =>
          logger.warn(s"Agents API chat adapter: could not delete session $sessionId: $e")
        }
    )

  private def tracked(cleanup: Future[Unit]): Future[Unit] = {
    cleanupsInFlight.put(cleanup, ())
    cleanup.onComplete(_ => cleanupsInFlight.remove(cleanup))
    cleanup
  }

  // ends the turn's chunks at its end marker, where the session is settled (see finishTurn) -
  // the stream completes once that has; an event stream that completes before the end fails
  // (a sentinel after the upstream - `concat` demands its second source eagerly, so a lazy
  // check there would run up front)
  private def turnFlow(
    state: TurnState,
    resumedFrom: Option[PausedSession],
    posted: Future[Unit]
  ): Flow[AgentSessionEvent, ChatChunk, NotUsed] =
    Flow[AgentSessionEvent]
      .map(Option(_))
      .concat(Source.single(None))
      .mapConcat {
        case Some(event) =>
          val chunks = state.onEvent(event)
          if (state.ended) finishTurn(state, resumedFrom, posted)
          chunks
        case None =>
          if (state.ended) Nil
          else
            throw new OpenAIScalaServerErrorException(
              "The Agents API session event stream closed before the turn ended."
            )
      }
      .takeWhile(_.isLeft, inclusive = true)
      .mapAsync(1) {
        case Left(chunk) => Future.successful(Option(chunk))
        case Right(_)    => state.settlement.future.map(_ => Option.empty[ChatChunk])
      }
      .collect { case Some(chunk) => chunk }
}

object OpenAIAgentsChatCompletionService {

  def apply(
    underlying: OpenAIAgentsService with OpenAIStreamedServiceExtra,
    agentId: Option[String] = None,
    environment: AgentEnvironment = AgentEnvironment.NoEnvironment,
    agentTools: Seq[AgentTool] = Nil,
    multiAgent: Option[MultiAgentConfig] = None,
    deleteSessionsAfterUse: Boolean = true
  )(
    implicit ec: ExecutionContext,
    materializer: Materializer
  ): OpenAIAgentsChatCompletionService =
    new OpenAIAgentsChatCompletionService(
      underlying,
      agentId,
      environment,
      agentTools,
      multiAgent,
      deleteSessionsAfterUse
    )

  private final case class Create(
    settings: CreateAgentSessionSettings,
    input: AgentInput,
    model: String
  )

  private final case class Resume(
    paused: PausedSession,
    toolOutputs: Seq[ToolMessage]
  )

  private final case class PausedSession(
    sessionId: String,
    model: String,
    pendingCalls: Map[String, String], // call id -> turn id
    reportedItems: Set[String],
    nextToolIndex: Int
  )

  private case object End

  private val subagentCallTypes = Set(
    "create_subagent_call",
    "send_subagent_input_call",
    "resume_subagent_call",
    "wait_for_subagents_call",
    "interrupt_subagent_call",
    "close_subagent_call"
  )

  // the per-turn mapping of session events to chunks; `reported` holds the item ids already
  // streamed (a resumed turn's subscription replays them)
  private final class TurnState(
    var model: String,
    var sessionId: Option[String],
    alreadyReported: Set[String],
    answered: Set[String],
    var nextIndex: Int
  ) {
    // read by the settling callback once the stream has terminated
    @volatile var ended = false
    @volatile var idle = false // ended at the session idling after the turn
    @volatile var posted = false
    val settled = new AtomicBoolean(false)
    val settlement: Promise[Unit] = Promise[Unit]()
    @volatile var pausedOn: Map[String, String] = Map.empty
    val reported: mutable.Set[String] = mutable.Set.empty ++ alreadyReported
    private var started = sessionId.isDefined // a resume emits its own Start
    private var turnFinished = false
    private var usage: Option[UsageInfo] = None
    private val phases = mutable.Map.empty[String, Option[String]]
    private val streamedText = mutable.Set.empty[String]
    private val streamedSummaries = mutable.Set.empty[(String, Int)]
    private val toolIndex = mutable.Map.empty[String, Int]
    private val calledClient = mutable.Set.empty[String]

    private def fresh(itemId: Option[String]): Boolean = !itemId.exists(reported.contains)

    private def index(key: String): Int =
      toolIndex.getOrElseUpdate(key, { val i = nextIndex; nextIndex += 1; i })

    private def textOf(
      itemId: String,
      text: String
    ): Option[ChatChunk] =
      if (text.isEmpty) None
      else if (phases.get(itemId).flatten.contains(AgentMessagePhase.Commentary))
        Some(Thinking(text))
      else Some(Text(text))

    private def end(chunks: ChatChunk*): List[Either[ChatChunk, End.type]] = {
      ended = true
      chunks.map(Left(_)).toList :+ Right(End)
    }

    private def clientCall(
      callId: String,
      name: String,
      arguments: JsValue
    ): List[ChatChunk] =
      if (calledClient.contains(callId) || answered.contains(callId)) Nil
      else {
        calledClient += callId
        List(ToolCall(index(callId), callId, name, arguments.toString, serverSide = false))
      }

    def onEvent(event: AgentSessionEvent): List[Either[ChatChunk, End.type]] = {
      event match {
        case session: SessionUpdated if sessionId.isEmpty =>
          sessionId = Some(session.session.id)
          // the model actually run (a reusable agent's own)
          session.session.agent.foreach(agent => model = agent.model)
        case _ =>
      }
      val startChunk =
        if (!started && sessionId.isDefined) {
          started = true; List(Start(sessionId.get, model))
        } else Nil

      val chunks: List[Either[ChatChunk, End.type]] = event match {
        // ---- items ----
        case ItemAdded(_, _, _, _, item) if fresh(item.id) => added(item).map(Left(_))
        case ItemDone(_, _, _, _, item) if fresh(item.id) =>
          val out = done(item)
          item.id.foreach(reported += _)
          out.map(Left(_))
        case _: ItemAdded | _: ItemDone => Nil

        case OutputTextDelta(_, _, _, itemId, _, _, delta) if fresh(Some(itemId)) =>
          streamedText += itemId
          textOf(itemId, delta).map(Left(_)).toList
        // a replayed part (no deltas) carries its whole text
        case OutputTextDone(_, _, _, itemId, _, _, text)
            if fresh(Some(itemId)) && !streamedText.contains(itemId) =>
          streamedText += itemId
          textOf(itemId, text).map(Left(_)).toList
        case ReasoningSummaryTextDelta(_, _, _, itemId, _, summaryIndex, delta)
            if fresh(Some(itemId)) =>
          streamedSummaries += ((itemId, summaryIndex))
          List(Left(Thinking(delta)))
        // a replayed summary part (no deltas) carries its whole text
        case ReasoningSummaryTextDone(_, _, _, itemId, _, summaryIndex, text)
            if fresh(Some(itemId)) && text.nonEmpty &&
              !streamedSummaries.contains((itemId, summaryIndex)) =>
          streamedSummaries += ((itemId, summaryIndex))
          List(Left(Thinking(text)))

        // ---- the turn / session ----
        case turn: TurnUpdated if turn.eventType == TurnCompleted =>
          turnFinished = true
          usage = turn.usage
            .orElse(turn.turn.usage)
            .map(u =>
              UsageInfo(
                prompt_tokens = u.inputTokens,
                total_tokens = u.totalTokens,
                completion_tokens = Some(u.outputTokens)
              )
            )
          Nil
        case turn: TurnUpdated if turn.eventType == TurnFailed =>
          throw turnError(turn.turn.error)
        case turn: TurnUpdated if turn.eventType == TurnCancelled =>
          end(Finish(FinishReason.unknown, Some("cancelled")))

        case session: SessionUpdated if session.isIdle && turnFinished =>
          idle = true
          end(Finish(FinishReason.stop, Some("completed")) +: usage.map(Usage(_)).toSeq: _*)

        case session: SessionUpdated if session.requiresAction =>
          val pending = session.session.pendingFunctionCalls.filterNot(c => answered(c.callId))
          val other = session.session.requiredActions.filter {
            case _: AgentRequiredAction.FunctionCall => false
            case _                                   => true
          }
          if (other.nonEmpty)
            throw new OpenAIScalaClientException(
              s"The agent waits for ${other.map(_.getClass.getSimpleName).mkString(", ")}, which the Agents API chat adapter cannot answer - use the Agents API directly (sendAgentSessionEvents)."
            )
          if (pending.isEmpty) Nil // the pause this resume answers, replayed
          else {
            pausedOn = pending.map(call => call.callId -> call.turnId).toMap
            val calls =
              pending.flatMap(call => clientCall(call.callId, call.name, call.arguments))
            end(calls :+ Finish(FinishReason.tool_calls, Some("requires_action")): _*)
          }

        case session: SessionUpdated if session.isFailed =>
          throw new OpenAIScalaServerErrorException(
            s"The agent session failed: ${session.session.error.getOrElse("(no detail)")}"
          )

        case error: AgentSessionEvent.Error =>
          val code =
            (error.error \ "code").asOpt[String].orElse((error.error \ "type").asOpt[String])
          throw classifiedError(code, s"Agents API session error: ${error.message}")

        case AgentSessionEvent.Other(eventType, raw)
            if eventType.startsWith("agent.session.environment.") ||
              eventType.startsWith("agent.session.subagent.") =>
          List(Left(ChatChunk.Other(eventType, raw)))

        case _ => Nil
      }

      startChunk.map(Left(_)) ++ chunks
    }

    private def added(item: AgentSessionItem): List[ChatChunk] =
      item match {
        case message: AgentSessionItem.Message =>
          message.id.foreach(id => phases.put(id, message.phase))
          Nil
        case call: AgentSessionItem.FunctionCall =>
          if (answered(call.callId) || calledClient(call.callId)) Nil
          else
            List(ToolCallStart(index(call.callId), call.callId, call.name, serverSide = false))
        case cmd: AgentSessionItem.CommandExecution =>
          val id = cmd.id.getOrElse("")
          List(ToolCallStart(index(id), id, "command_execution", serverSide = true))
        case mcp: AgentSessionItem.McpCall =>
          val id = mcp.id.getOrElse("")
          List(ToolCallStart(index(id), id, mcp.name, serverSide = true))
        case search: AgentSessionItem.WebSearchCall =>
          val id = search.id.getOrElse("")
          List(ToolCallStart(index(id), id, "web_search", serverSide = true))
        case other: AgentSessionItem.Other if subagentCallTypes(other.itemType) =>
          val id = other.id.getOrElse("")
          List(ToolCallStart(index(id), id, other.itemType, serverSide = true))
        case _ => Nil
      }

    private def done(item: AgentSessionItem): List[ChatChunk] =
      item match {
        case message: AgentSessionItem.Message if message.role == "assistant" =>
          // a message whose text neither streamed nor was replayed as a part
          message.id match {
            case Some(id) if !streamedText.contains(id) =>
              phases.getOrElseUpdate(id, message.phase)
              streamedText += id
              textOf(id, message.text).toList
            case _ => Nil
          }

        case call: AgentSessionItem.FunctionCall =>
          clientCall(call.callId, call.name, call.arguments)

        case cmd: AgentSessionItem.CommandExecution =>
          val id = cmd.id.getOrElse("")
          val failed = cmd.exitCode.exists(_ != 0) || cmd.status.contains("failed")
          val result = Json.obj(
            "command" -> cmd.command,
            "output" -> cmd.output,
            "exit_code" -> cmd.exitCode,
            "duration_ms" -> cmd.durationMs
          )
          List(
            ToolCall(
              index(id),
              id,
              "command_execution",
              Json.obj("command" -> cmd.command).toString,
              serverSide = true
            ),
            CodeExecution(id, Some("bash"), cmd.command),
            ToolResult(id, "command_execution", result, cmd.output, isError = failed),
            CodeExecutionResult(id, cmd.output, isError = failed, result)
          )

        case mcp: AgentSessionItem.McpCall =>
          val id = mcp.id.getOrElse("")
          val arguments = mcp.arguments.map {
            case play.api.libs.json.JsString(s) => s
            case other                          => other.toString
          }.getOrElse("{}")
          List(
            ToolCall(index(id), id, mcp.name, arguments, serverSide = true),
            ToolResult(
              id,
              mcp.name,
              mcp.error.orElse(mcp.output).getOrElse(Json.obj()),
              mcp.output.map {
                case play.api.libs.json.JsString(s) => s
                case other                          => other.toString
              },
              isError = mcp.error.isDefined
            )
          )

        case search: AgentSessionItem.WebSearchCall =>
          val id = search.id.getOrElse("")
          val action = search.action.getOrElse(Json.obj())
          val queries = (action \ "queries")
            .asOpt[Seq[String]]
            .getOrElse((action \ "query").asOpt[String].toSeq)
          List(
            ToolCall(index(id), id, "web_search", action.toString, serverSide = true),
            WebSearch(id, queries)
          )

        case other: AgentSessionItem.Other if subagentCallTypes(other.itemType) =>
          val id = other.id.getOrElse("")
          List(ToolCall(index(id), id, other.itemType, other.raw.toString, serverSide = true))

        case other: AgentSessionItem.Other =>
          List(ChatChunk.Other(s"item.${other.itemType}", other.raw))

        case _ => Nil
      }
  }

  private def turnError(error: Option[AgentTurnError]): Throwable =
    classifiedError(
      error.map(_.code),
      s"The agent turn failed: ${error.map(e => s"${e.code}: ${e.message}").getOrElse("(no detail)")}"
    )

  // a failed turn or an error event, by the Agents API error code - so the retry adapters
  // retry the transient ones
  private def classifiedError(
    code: Option[String],
    message: String
  ): Throwable =
    code match {
      case Some("rate_limit_exceeded" | "usage_limit_exceeded" | "credit_balance_exhausted") =>
        new OpenAIScalaRateLimitException(message)
      case Some("server_overloaded" | "flex_unavailable") =>
        new OpenAIScalaEngineOverloadedException(message)
      case Some("context_length_exceeded") =>
        new OpenAIScalaTokenCountExceededException(message)
      case Some("authentication_error") =>
        new OpenAIScalaUnauthorizedException(message)
      case Some(
            "server_error" | "internal_error" | "sandbox_error" | "connection_failed" |
            "request_timeout"
          ) =>
        new OpenAIScalaServerErrorException(message)
      case _ => new OpenAIScalaClientException(message)
    }
}
