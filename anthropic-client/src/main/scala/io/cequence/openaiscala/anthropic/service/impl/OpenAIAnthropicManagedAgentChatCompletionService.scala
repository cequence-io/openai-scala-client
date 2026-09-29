package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, Materializer}
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.{
  OpenAIScalaClientException,
  OpenAIScalaEngineOverloadedException,
  OpenAIScalaRateLimitException,
  OpenAIScalaServerErrorException
}
import io.cequence.openaiscala.anthropic.domain.managedagents.{
  AgentModelConfig,
  AgentTool,
  EnvironmentConfig,
  SessionContentBlock,
  SessionDocumentSource,
  SessionEvent,
  SessionEventEnvelope,
  SessionImageSource
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateAgentSettings,
  AnthropicCreateEnvironmentSettings,
  AnthropicCreateSessionSettings
}
import io.cequence.openaiscala.anthropic.service.AnthropicService
import io.cequence.openaiscala.domain.response.ChatChunk.FinishReason
import io.cequence.openaiscala.domain.response.{
  ChatChunk,
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChunkMessageSpec,
  PromptTokensDetails,
  ToolApprovalDecision,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.domain.{
  BaseMessage,
  ChatCompletionTool,
  ChatRole,
  DeveloperMessage,
  FileContent,
  ImageURLContent,
  MessageSpec,
  SystemMessage,
  TextContent,
  UserMessage,
  UserSeqMessage,
  AssistantMessage => OpenAIAssistantMessage,
  Content => OpenAIContent
}
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsNull, JsObject, JsValue, Json}

import java.util.concurrent.atomic.AtomicBoolean
import java.{util => ju}
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/**
 * OpenAI-compatible chat-completion adapter backed by the Anthropic Managed Agents API.
 *
 * Each `createChatCompletion` call runs one managed-agent turn: resolve (or lazily create) an
 * agent and an environment, create a session, open the session event stream, send the
 * conversation as a `user.message` kickoff event, collect `agent.message` events until the
 * session goes idle, and (by default) delete the session afterwards.
 *
 * Semantics and limitations:
 *   - When [[fixedAgentId]] is empty, agents are created lazily — one per (model, system
 *     prompt) combination — and cached for the lifetime of this service, following the "create
 *     once, reference by id" guidance. `settings.model` selects the agent's model.
 *   - When [[fixedAgentId]] is set, `settings.model` is ignored (the agent's configured model
 *     applies) and system messages are folded into the kickoff transcript, since the agent's
 *     own system prompt cannot be replaced per request.
 *   - Assistant messages in the conversation history are folded into the kickoff transcript
 *     with role labels — managed-agent sessions accept only user-side events, so the history
 *     cannot be replayed turn-by-turn.
 *   - Most sampling settings (temperature, max_tokens, reasoning_effort, ...) have no
 *     managed-agents equivalent and are ignored.
 *   - Tool confirmations (an `always_ask` / `auto` permission policy) are supported by the
 *     TYPED stream (`createChatToolCompletionStreamed` / `createChatCompletionStreamedTyped`):
 *     the paused session is kept, its pending calls arrive as `ChatChunk.ToolApprovalRequest`s
 *     followed by `Finish(approval_required)`, and a call carrying the decisions
 *     (`setToolApprovalDecisions`) sends them as `user.tool_confirmation` events to the same
 *     session (the `messages` are ignored - the history is in the session) and streams the
 *     rest of the turn (the session applies the confirmations one at a time, idling with
 *     `requires_action` in between - such an interim idle is not a pause and is passed through
 *     as `Other`). The session is deleted once a turn finishes, fails or is cancelled; it is
 *     KEPT while paused (delete an abandoned run with `deleteSession(runId)`) and when a
 *     resume can be retried with the same decisions: its confirmations were not applied (the
 *     POST failed), or its next pause could not be looked up. `createChatCompletion(Streamed)`
 *     cannot answer confirmations and fail; custom tool results (`agent.custom_tool_use`) are
 *     not supported. A `session.error` the platform retries by itself does not end the turn;
 *     an `exhausted` / `terminal` one fails the call (classified), as does an event stream
 *     that closes before the turn ended.
 *
 * Requires the `managed-agents-2026-04-01` beta on the API key; not available on Bedrock.
 *
 * @param underlying
 *   Anthropic service used for the managed-agents calls.
 * @param fixedAgentId
 *   Use this pre-created agent for all sessions instead of creating agents on the fly.
 * @param environmentId
 *   Run sessions in this environment. When empty, a cloud environment named
 *   `openai-scala-client-chat-adapter` is looked up (or created) and reused.
 * @param agentTools
 *   Tools granted to lazily-created agents (ignored when [[fixedAgentId]] is set). Defaults to
 *   the full built-in toolset.
 * @param deleteSessionsAfterUse
 *   Whether to delete each session once its turn finishes (default true).
 */
private[service] class OpenAIAnthropicManagedAgentChatCompletionService(
  underlying: AnthropicService,
  fixedAgentId: Option[String] = None,
  environmentId: Option[String] = None,
  agentTools: Seq[AgentTool] = Seq(AgentTool.Toolset()),
  deleteSessionsAfterUse: Boolean = true
)(
  implicit executionContext: ExecutionContext,
  materializer: Materializer
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra {

  private val logger: Logger = Logger(LoggerFactory.getLogger(this.getClass))

  private val adapterResourceName = "openai-scala-client-chat-adapter"
  private val adapterMetadata = Map("created_by" -> "openai-scala-client")

  // lazily-resolved environment id and agent ids, cached for the lifetime of this service
  private val environmentIdCache = TrieMap.empty[Unit, Future[String]]
  private val agentIdCache = TrieMap.empty[(String, Option[String]), Future[String]]

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    if (settings.toolApprovalDecisions.nonEmpty)
      Future.failed(decisionsNeedTypedStream)
    else {
      val kickoffEvents = toKickoffEvents(messages)

      (
        for {
          sessionId <- createTurnSession(messages, settings)
          accum <- turnEventSource(sessionId, kickoffEvents)
            .runWith(Sink.fold(SessionTurnAccum())(accumulate))
            .transformWith(result => cleanupSession(sessionId).transform(_ => result))
        } yield toChatCompletionResponse(sessionId, settings.model, accum)
      ).recoverWith(repackAsOpenAIException)
    }

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    if (settings.toolApprovalDecisions.nonEmpty)
      Source.failed(decisionsNeedTypedStream)
    else {
      val kickoffEvents = toKickoffEvents(messages)

      val futureSource = createTurnSession(messages, settings).map { sessionId =>
        turnEventSource(sessionId, kickoffEvents)
          .mapConcat(toChunks(sessionId, settings.model, _))
          .watchTermination() {
            (
              mat,
              doneFuture
            ) =>
              doneFuture.onComplete(_ => cleanupSession(sessionId))
              mat
          }
      }

      // keep it like this because of the compatibility with older versions of Akka stream
      Source.fromFutureSource(futureSource).mapMaterializedValue(_ => NotUsed)
    }

  private def decisionsNeedTypedStream =
    new OpenAIScalaClientException(
      "Tool approval decisions resume a managed-agent session via the typed stream only - use createChatToolCompletionStreamed / createChatCompletionStreamedTyped."
    )

  /**
   * The typed stream of one managed-agent turn (see the class scaladoc): `Start` (the session
   * id), the agent's messages as `Text`, its server-executed tool calls / results on the tool
   * layer, then `Finish` + `Usage` at the session's idle. A run paused for tool confirmation
   * ends with its pending calls as `ToolApprovalRequest`s and `Finish(approval_required)`; the
   * session is kept (not deleted) until a call carrying the decisions finishes it. Tools are
   * configured on the agent (`agentTools` / a fixed agent), not per call.
   */
  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] =
    if (tools.nonEmpty)
      Source.failed(
        new OpenAIScalaClientException(
          "Managed-agent tools are configured on the agent (agentTools / a fixed agent), not per call - pass no tools."
        )
      )
    else {
      val decisions = settings.toolApprovalDecisions

      // (session, events to send, approved/denied calls to report first)
      val turn: Future[(String, Seq[SessionEvent], Seq[ChatChunk.ToolApprovalRequest])] =
        if (decisions.nonEmpty)
          Future.fromTry(Try(resumeTurn(decisions)))
        else
          Future
            .fromTry(Try(toKickoffEvents(messages)))
            .flatMap(kickoff =>
              createTurnSession(messages, settings).map(id => (id, kickoff, Nil))
            )

      val futureSource = turn.map { case (sessionId, events, answered) =>
        // paused: the turn ended waiting for confirmations (set once they are resolved);
        // pauseUnresolved: it ended waiting, but looking the pending calls up failed (I/O);
        // sent: the outcome of the POST carrying this turn's events (kickoff / confirmations)
        val paused = new AtomicBoolean(false)
        val pauseUnresolved = new AtomicBoolean(false)
        val sent = Promise[Unit]()
        val resuming = decisions.nonEmpty

        val answeredCalls = answered.zipWithIndex.flatMap { case (request, index) =>
          List(
            ChatChunk
              .ToolCallStart(index, request.requestId, request.toolName, serverSide = true),
            ChatChunk.ToolCall(
              index,
              request.requestId,
              request.toolName,
              request.arguments,
              serverSide = true
            )
          )
        }

        // A paused session is the run the caller resumes - keep it. So is one a resume can
        // retry with the same decisions: its confirmations were not applied (the POST failed),
        // or it paused again on calls that could not be looked up (the caller may also delete
        // it: deleteSession(runId)). Anything else - a finished, failed or cancelled turn, a
        // resume included once its confirmations were applied - is deleted once, BEFORE the
        // stream completes (a caller closing the service right after the last chunk must not
        // race the deletion), or on failure / cancellation - after the POST's outcome is known
        val cleaned = new AtomicBoolean(false)
        def cleanupOnce(): Future[Unit] =
          if (!cleaned.compareAndSet(false, true)) Future.successful(())
          else
            sent.future.transformWith { post =>
              if (paused.get) Future.successful(())
              else if (resuming && (post.isFailure || pauseUnresolved.get)) {
                logger.warn(
                  s"Resuming managed-agent session '$sessionId' did not finish - the session is kept (retry the decisions, or delete it with deleteSession)."
                )
                Future.successful(())
              } else cleanupSession(sessionId)
            }

        // `None` marks the end of the session events: the cleanup runs in the ordered mapAsync
        // after every event was mapped (so a pause is known), before the stream completes
        val chunks = (turnEventSource(
          sessionId,
          events,
          confirmed = answered.map(_.requestId).toSet,
          sent = sent
        ).statefulMapConcat { () =>
          val mapper = new TypedTurnMapper(sessionId, answered)
          mapper.map _
        }.map(Some(_)) ++ Source.single(None))
          .mapAsync(1) {
            case Some(Right(chunk)) => Future.successful(List(chunk))
            // paused only once the pause can be answered - an unanswerable one (a custom tool)
            // fails the stream and its session is cleaned up; a failed lookup keeps a resumed
            // one (it can be retried)
            case Some(Left(pause)) =>
              resolvePause(sessionId, pause).transform(
                chunks => { paused.set(true); chunks },
                {
                  case e: UnanswerablePauseException => e
                  case e =>
                    pauseUnresolved.set(true)
                    e
                }
              )
            case None => cleanupOnce().map(_ => Nil)
          }
          .mapConcat(identity)

        (Source.single(ChatChunk.Start(sessionId, settings.model)) ++
          Source(answeredCalls.toList) ++ chunks).watchTermination() {
          (
            mat,
            done
          ) =>
            done.onComplete(_ => cleanupOnce())
            mat
        }
      }

      Source
        .fromFutureSource(futureSource)
        .mapMaterializedValue(_ => NotUsed)
        .mapError(toOpenAIException)
    }

  // the session a set of decisions resumes, their user.tool_confirmation events and the calls
  private def resumeTurn(
    decisions: Seq[ToolApprovalDecision]
  ): (String, Seq[SessionEvent], Seq[ChatChunk.ToolApprovalRequest]) = {
    val requests = decisions.map(_.request)

    requests
      .find(r => !ToolUseEventTypes.contains((r.raw \ "type").asOpt[String].getOrElse("")))
      .foreach(r =>
        throw new OpenAIScalaClientException(
          s"Tool approval request '${r.requestId}' is not a managed-agent tool confirmation request - it cannot resume a managed-agent session."
        )
      )

    val sessionId = requests.map(_.runId).distinct match {
      case Seq(single) => single
      case several =>
        throw new OpenAIScalaClientException(
          s"Tool approval decisions must answer one paused session, got: ${several.mkString(", ")}."
        )
    }

    val confirmations = decisions.map(decision =>
      SessionEvent.UserToolConfirmation(
        toolUseId = decision.request.requestId,
        allow = decision.approve,
        denyMessage = decision.reason.filterNot(_ => decision.approve)
      )
    )

    (sessionId, confirmations, requests)
  }

  private val ToolUseEventTypes = Set("agent.tool_use", "agent.mcp_tool_use")

  /**
   * A `requires_action` idle: the pending event ids (in order) with the requests already seen
   * in this stream, and the chunks to emit after them (Finish, Usage).
   */
  private case class Pause(
    eventIds: Seq[String],
    seen: Map[String, ChatChunk.ToolApprovalRequest],
    tail: List[ChatChunk]
  )

  /**
   * Maps the session events of one turn to typed chunks (one instance per materialization).
   * `answered` are the calls a resume confirmed - reported up front, so their ordinals and
   * names are known when their results arrive.
   */
  private class TypedTurnMapper(
    sessionId: String,
    answered: Seq[ChatChunk.ToolApprovalRequest]
  ) {
    private var toolCount = answered.size
    private val confirmed = answered.map(_.requestId).toSet
    private val toolNames = mutable.Map(answered.map(r => r.requestId -> r.toolName): _*)
    private val pending = mutable.LinkedHashMap.empty[String, ChatChunk.ToolApprovalRequest]
    private var usage = SessionTurnAccum()

    def map(envelope: SessionEventEnvelope): List[Either[Pause, ChatChunk]] = {
      val raw = envelope.raw
      def id = envelope.id.getOrElse("")

      envelope.`type` match {
        case "agent.message" =>
          val text = agentMessageTexts(raw).mkString("\n")
          if (text.isEmpty) Nil else List(Right(ChatChunk.Text(text)))

        case eventType if ToolUseEventTypes.contains(eventType) =>
          val name = (raw \ "name").asOpt[String].getOrElse(eventType)
          val arguments = (raw \ "input").toOption.filterNot(_ == JsNull).getOrElse(Json.obj())
          toolNames.put(id, name)

          if ((raw \ "evaluated_permission").asOpt[String].contains("ask")) {
            // reported (as a ToolCall) once confirmed, in the resumed stream
            pending.put(id, toApprovalRequest(sessionId, envelope))
            Nil
          } else {
            val index = toolCount
            toolCount += 1
            List(
              Right(ChatChunk.ToolCallStart(index, id, name, serverSide = true)),
              Right(ChatChunk.ToolCall(index, id, name, arguments.toString, serverSide = true))
            )
          }

        case "agent.tool_result" | "agent.mcp_tool_result" =>
          val callId = (raw \ "tool_use_id")
            .asOpt[String]
            .orElse((raw \ "mcp_tool_use_id").asOpt[String])
            .getOrElse(id)
          val content = (raw \ "content").toOption.getOrElse(JsNull)
          val text = agentMessageTexts(raw).mkString("\n")
          List(
            Right(
              ChatChunk.ToolResult(
                callId,
                toolNames.getOrElse(callId, "tool"),
                content,
                if (text.isEmpty) None else Some(text),
                isError = (raw \ "is_error").asOpt[Boolean].getOrElse(false)
              )
            )
          )

        case "span.model_request_end" =>
          usage = accumulate(usage, envelope)
          Nil

        // the session still applying the confirmations of this resume - the turn goes on
        case "session.status_idle" if isInterimIdle(raw, confirmed) =>
          List(Right(ChatChunk.Other("session.status_idle", raw)))

        case "session.status_idle" =>
          val usageChunk = ChatChunk.Usage(toUsageInfo(usage))
          stopReasonType(raw) match {
            case Some("requires_action") =>
              // a confirmation of this resume still queued is not asked again
              val eventIds = (raw \ "stop_reason" \ "event_ids")
                .asOpt[Seq[String]]
                .getOrElse(Nil)
                .filterNot(confirmed.contains)
              List(
                Left(
                  Pause(
                    eventIds,
                    pending.toMap,
                    List(
                      ChatChunk
                        .Finish(FinishReason.approval_required, Some("requires_action")),
                      usageChunk
                    )
                  )
                )
              )

            case stopReason =>
              val reason = stopReason match {
                case None | Some("end_turn") => FinishReason.stop
                case Some("budget_reached")  => FinishReason.length
                case Some(_)                 => FinishReason.unknown
              }
              List(
                Right(ChatChunk.Finish(reason, stopReason.orElse(Some("end_turn")))),
                Right(usageChunk)
              )
          }

        case "session.status_terminated" =>
          List(
            Right(ChatChunk.Finish(FinishReason.unknown, Some("terminated"))),
            Right(ChatChunk.Usage(toUsageInfo(usage)))
          )

        // the platform is retrying by itself - the turn goes on
        case "session.error" if !isTerminalError(raw) =>
          List(Right(ChatChunk.Other("session.error", raw)))

        case "session.error" =>
          throw sessionErrorException(sessionId, raw)

        case other => List(Right(ChatChunk.Other(other, raw)))
      }
    }
  }

  private def toApprovalRequest(
    sessionId: String,
    envelope: SessionEventEnvelope
  ): ChatChunk.ToolApprovalRequest = {
    val raw = envelope.raw
    ChatChunk.ToolApprovalRequest(
      requestId = envelope.id.getOrElse(""),
      toolName = (raw \ "name").asOpt[String].getOrElse(envelope.`type`),
      arguments =
        (raw \ "input").toOption.filterNot(_ == JsNull).getOrElse(Json.obj()).toString,
      serverName = (raw \ "mcp_server_name").asOpt[String],
      runId = sessionId,
      raw = raw
    )
  }

  /**
   * The approval requests of a pause, in `event_ids` order. An id not seen in this stream (a
   * call left pending by an earlier stream, e.g. after a partial resume) is looked up in the
   * session's event history; a custom-tool use cannot be answered here and fails the stream.
   */
  private def resolvePause(
    sessionId: String,
    pause: Pause
  ): Future[List[ChatChunk]] = {
    val missing = pause.eventIds.filterNot(pause.seen.contains)

    val lookedUp: Future[Map[String, SessionEventEnvelope]] =
      if (missing.isEmpty) Future.successful(Map.empty)
      else findSessionEvents(sessionId, missing.toSet)

    lookedUp.map { found =>
      val requests = pause.eventIds.map { eventId =>
        pause.seen.get(eventId).getOrElse {
          found.get(eventId) match {
            case Some(envelope) if ToolUseEventTypes.contains(envelope.`type`) =>
              toApprovalRequest(sessionId, envelope)
            case Some(envelope) =>
              throw new UnanswerablePauseException(
                s"Managed-agent session '$sessionId' requested a client-side '${envelope.`type`}' action (e.g. a custom tool result), which this adapter cannot provide - only tool confirmations are supported."
              )
            case None =>
              throw new UnanswerablePauseException(
                s"Managed-agent session '$sessionId' requires action on event '$eventId', which is not in the session history."
              )
          }
        }
      }
      requests.toList ++ pause.tail
    }
  }

  // pages through the session's event history until every id is found (or it runs out)
  private def findSessionEvents(
    sessionId: String,
    ids: Set[String],
    page: Option[String] = None,
    found: Map[String, SessionEventEnvelope] = Map.empty
  ): Future[Map[String, SessionEventEnvelope]] =
    underlying.listSessionEvents(sessionId, limit = Some(100), page = page).flatMap {
      response =>
        val all = found ++ response.data.collect {
          case e if e.id.exists(ids.contains) => e.id.get -> e
        }
        if (ids.forall(all.contains) || response.nextPage.isEmpty) Future.successful(all)
        else findSessionEvents(sessionId, ids, response.nextPage, all)
    }

  /**
   * Closes the underlying ws client, and releases all its resources.
   */
  override def close(): Unit = underlying.close()

  // ---------------------------------------------------------------------------
  // Session setup
  // ---------------------------------------------------------------------------

  private def createTurnSession(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[String] =
    for {
      envId <- resolveEnvironmentId()
      agentId <- resolveAgentId(settings.model, systemPromptOf(messages))
      session <- underlying.createSession(
        AnthropicCreateSessionSettings(
          agentId = agentId,
          environmentId = envId,
          title = Some("openai-scala-client chat completion"),
          metadata = adapterMetadata
        )
      )
    } yield session.id

  private def resolveEnvironmentId(): Future[String] =
    environmentId
      .map(Future.successful)
      .getOrElse(cachedFuture(environmentIdCache, ())(findOrCreateEnvironment()))

  private def findOrCreateEnvironment(): Future[String] =
    underlying.listEnvironments(limit = Some(100)).flatMap { page =>
      page.data.find(_.name == adapterResourceName) match {
        case Some(env) => Future.successful(env.id)

        case None =>
          underlying
            .createEnvironment(
              AnthropicCreateEnvironmentSettings(
                name = adapterResourceName,
                config = Some(EnvironmentConfig.Cloud()),
                description =
                  Some("Auto-created by the openai-scala-client managed-agent chat adapter"),
                metadata = adapterMetadata
              )
            )
            .map(_.id)
            .recoverWith { case createError =>
              // environment names are unique - a concurrent creator may have won the race
              underlying.listEnvironments(limit = Some(100)).flatMap {
                _.data
                  .find(_.name == adapterResourceName)
                  .map(env => Future.successful(env.id))
                  .getOrElse(Future.failed(createError))
              }
            }
      }
    }

  private def resolveAgentId(
    model: String,
    systemPrompt: Option[String]
  ): Future[String] =
    fixedAgentId
      .map(Future.successful)
      .getOrElse(
        cachedFuture(agentIdCache, (model, systemPrompt))(
          underlying
            .createAgent(
              AnthropicCreateAgentSettings(
                name = s"$adapterResourceName-$model",
                model = AgentModelConfig(model),
                system = systemPrompt,
                tools = agentTools,
                metadata = adapterMetadata
              )
            )
            .map(_.id)
        )
      )

  private def cachedFuture[K, V](
    cache: TrieMap[K, Future[V]],
    key: K
  )(
    create: => Future[V]
  ): Future[V] = {
    val future = cache.getOrElseUpdate(key, create)
    // don't cache failures - allow the next call to retry
    future.recoverWith { case e =>
      cache.remove(key, future)
      Future.failed(e)
    }
  }

  // ---------------------------------------------------------------------------
  // Message conversion
  // ---------------------------------------------------------------------------

  private def isSystemLike(message: BaseMessage): Boolean =
    message.isSystem || message.role == ChatRole.Developer

  /**
   * System prompt for lazily-created agents. When a fixed agent is used the system messages
   * are folded into the transcript instead (see [[toKickoffEvents]]).
   */
  private def systemPromptOf(messages: Seq[BaseMessage]): Option[String] = {
    val texts = messages.collect {
      case SystemMessage(content, _)    => content
      case DeveloperMessage(content, _) => content
    }
    if (texts.isEmpty) None else Some(texts.mkString("\n"))
  }

  private def toKickoffEvents(messages: Seq[BaseMessage]): Seq[SessionEvent] = {
    val (systemMessages, conversationMessages) = messages.partition(isSystemLike)

    // system messages ride on the agent config unless the agent is fixed
    val inlineSystemTexts =
      if (fixedAgentId.isDefined)
        systemMessages.collect {
          case SystemMessage(content, _)    => content
          case DeveloperMessage(content, _) => content
        }
      else
        Nil

    val blocks = toUserContentBlocks(conversationMessages, inlineSystemTexts)

    if (blocks.isEmpty)
      throw new OpenAIScalaClientException("At least one user message expected.")

    Seq(SessionEvent.UserMessage(blocks))
  }

  private def toUserContentBlocks(
    messages: Seq[BaseMessage],
    inlineSystemTexts: Seq[String]
  ): Seq[SessionContentBlock] = {
    // label turns only when the history cannot be sent as a single verbatim user message
    val multiTurn = messages.size > 1 || inlineSystemTexts.nonEmpty

    val systemBlocks = inlineSystemTexts.map(text =>
      SessionContentBlock.Text(s"System: $text"): SessionContentBlock
    )

    val messageBlocks = messages.flatMap {
      case UserMessage(content, _) =>
        Seq(SessionContentBlock.Text(if (multiTurn) s"User: $content" else content))

      case UserSeqMessage(contents, _) =>
        val label = if (multiTurn) Seq(SessionContentBlock.Text("User:")) else Nil
        label ++ contents.map(toSessionContentBlock)

      case OpenAIAssistantMessage(content, _, _) =>
        Seq(SessionContentBlock.Text(s"Assistant: $content"))

      // legacy message type
      case MessageSpec(role, content, _) if role == ChatRole.User =>
        Seq(SessionContentBlock.Text(if (multiTurn) s"User: $content" else content))

      case other =>
        throw new OpenAIScalaClientException(
          s"Message type '${other.getClass.getSimpleName}' is not supported by the Anthropic managed-agent chat-completion adapter."
        )
    }

    systemBlocks ++ messageBlocks
  }

  private def toSessionContentBlock(content: OpenAIContent): SessionContentBlock =
    content match {
      case TextContent(text) =>
        SessionContentBlock.Text(text)

      case ImageURLContent(url) =>
        if (url.startsWith("data:")) {
          val (mediaType, data) = parseDataUrl(url)
          SessionContentBlock.Image(SessionImageSource.Base64(data, mediaType))
        } else
          SessionContentBlock.Image(SessionImageSource.Url(url))

      case FileContent(fileIdOpt, fileDataOpt, filenameOpt) =>
        (fileIdOpt, fileDataOpt) match {
          case (Some(fileId), _) =>
            SessionContentBlock.Document(
              SessionDocumentSource.FileRef(fileId),
              title = filenameOpt
            )

          case (_, Some(fileData)) if fileData.startsWith("data:") =>
            val (mediaType, data) = parseDataUrl(fileData)
            SessionContentBlock.Document(
              SessionDocumentSource.Base64(data, mediaType),
              title = filenameOpt
            )

          case _ =>
            throw new OpenAIScalaClientException(
              "FileContent for the managed-agent adapter requires either fileId or fileData as a data URL (e.g. data:application/pdf;base64,...)."
            )
        }
    }

  private def parseDataUrl(url: String): (String, String) = {
    val mediaTypeEncodingAndData = url.drop(5)
    val mediaType = mediaTypeEncodingAndData.takeWhile(_ != ';')
    val encodingAndData = mediaTypeEncodingAndData.drop(mediaType.length + 1)
    val encoding = encodingAndData.takeWhile(_ != ',')
    (mediaType, encodingAndData.drop(encoding.length + 1))
  }

  // ---------------------------------------------------------------------------
  // Turn execution - session event stream
  // ---------------------------------------------------------------------------

  /**
   * Events of one turn: the SSE stream is opened first (so no early events are missed), the
   * kickoff events are sent once it materializes (`sent` gets the POST's outcome), and the
   * stream completes at the first terminal event (idle / terminated / error). A kickoff-send
   * failure aborts the stream; so does an event stream that closes before the turn ended.
   */
  private def turnEventSource(
    sessionId: String,
    kickoffEvents: Seq[SessionEvent],
    confirmed: Set[String] = Set.empty,
    sent: Promise[Unit] = Promise[Unit]()
  ): Source[SessionEventEnvelope, NotUsed] =
    (underlying
      .streamSessionEvents(sessionId)
      .viaMat(KillSwitches.single)(Keep.right)
      .takeWhile(envelope => !isTerminal(envelope, confirmed), inclusive = true)
      .mapMaterializedValue { killSwitch =>
        underlying.sendSessionEvents(sessionId, kickoffEvents).onComplete { result =>
          sent.tryComplete(result.map(_ => ()))
          result.failed.foreach(killSwitch.abort)
        }
        NotUsed
      }
      .map(Some(_)) ++ Source.single(None)).statefulMapConcat { () =>
      var ended = false

      {
        case Some(envelope) =>
          if (isTerminal(envelope, confirmed)) ended = true
          List(envelope)
        case None =>
          if (!ended)
            throw new OpenAIScalaServerErrorException(
              s"The event stream of managed-agent session '$sessionId' closed before the turn ended."
            )
          Nil
      }
    }

  private def isTerminal(
    envelope: SessionEventEnvelope,
    confirmed: Set[String]
  ): Boolean =
    envelope.`type` match {
      case "session.status_idle"       => !isInterimIdle(envelope.raw, confirmed)
      case "session.status_terminated" => true
      case "session.error"             => isTerminalError(envelope.raw)
      case _                           => false
    }

  /**
   * The session applies the confirmations of one POST one at a time and goes idle
   * (`requires_action`) in between, listing the calls whose confirmations are still queued
   * (live 2026-09-29) - not a pause: the rest of the turn follows in the same stream.
   */
  private def isInterimIdle(
    raw: JsObject,
    confirmed: Set[String]
  ): Boolean =
    stopReasonType(raw).contains("requires_action") && {
      val eventIds = (raw \ "stop_reason" \ "event_ids").asOpt[Seq[String]].getOrElse(Nil)
      eventIds.nonEmpty && eventIds.forall(confirmed.contains)
    }

  private def cleanupSession(sessionId: String): Future[Unit] =
    if (deleteSessionsAfterUse)
      underlying.deleteSession(sessionId).map(_ => ()).recover { case e =>
        logger.warn(s"Failed to delete managed-agent session '$sessionId': ${e.getMessage}")
      }
    else
      Future.successful(())

  // -- raw event payload helpers --

  private def agentMessageTexts(raw: JsObject): Seq[String] =
    (raw \ "content").asOpt[Seq[JsValue]].getOrElse(Nil).flatMap { block =>
      if ((block \ "type").asOpt[String].contains("text"))
        (block \ "text").asOpt[String]
      else
        None
    }

  private def stopReasonType(raw: JsObject): Option[String] =
    (raw \ "stop_reason" \ "type").asOpt[String]

  // a session.error that ends the turn: all but `retrying` (the platform retries by itself) -
  // `exhausted` means the turn is dead (the session returns to idle), `terminal` that the
  // session terminates
  private def isTerminalError(raw: JsObject): Boolean =
    !(raw \ "error" \ "retry_status" \ "type").asOpt[String].contains("retrying")

  private def sessionErrorException(
    sessionId: String,
    raw: JsObject
  ): OpenAIScalaClientException = {
    val message = s"Managed-agent session '$sessionId' reported an error: ${errorMessage(raw)}"
    (raw \ "error" \ "type").asOpt[String] match {
      case Some("model_overloaded_error") => new OpenAIScalaEngineOverloadedException(message)
      case Some("model_rate_limited_error") => new OpenAIScalaRateLimitException(message)
      case _                                => new OpenAIScalaClientException(message)
    }
  }

  private def errorMessage(raw: JsObject): String =
    (raw \ "error" \ "message").asOpt[String].getOrElse(raw.toString())

  // ---------------------------------------------------------------------------
  // Non-streamed response assembly
  // ---------------------------------------------------------------------------

  private case class SessionTurnAccum(
    texts: Vector[String] = Vector.empty,
    inputTokens: Int = 0,
    outputTokens: Int = 0,
    cacheCreationTokens: Int = 0,
    cacheReadTokens: Int = 0,
    stopReason: Option[String] = None,
    terminated: Boolean = false,
    error: Option[JsObject] = None
  )

  private def accumulate(
    accum: SessionTurnAccum,
    envelope: SessionEventEnvelope
  ): SessionTurnAccum =
    envelope.`type` match {
      case "agent.message" =>
        accum.copy(texts = accum.texts ++ agentMessageTexts(envelope.raw))

      case "span.model_request_end" =>
        val usage = envelope.raw \ "model_usage"
        accum.copy(
          inputTokens = accum.inputTokens + (usage \ "input_tokens").asOpt[Int].getOrElse(0),
          outputTokens =
            accum.outputTokens + (usage \ "output_tokens").asOpt[Int].getOrElse(0),
          cacheCreationTokens = accum.cacheCreationTokens +
            (usage \ "cache_creation_input_tokens").asOpt[Int].getOrElse(0),
          cacheReadTokens = accum.cacheReadTokens +
            (usage \ "cache_read_input_tokens").asOpt[Int].getOrElse(0)
        )

      case "session.status_idle" =>
        accum.copy(stopReason = stopReasonType(envelope.raw).orElse(Some("end_turn")))

      case "session.status_terminated" =>
        accum.copy(
          terminated = true,
          stopReason = accum.stopReason.orElse(Some("terminated"))
        )

      case "session.error" if isTerminalError(envelope.raw) =>
        accum.copy(error = Some(envelope.raw))

      case _ => accum
    }

  private def toChatCompletionResponse(
    sessionId: String,
    model: String,
    accum: SessionTurnAccum
  ): ChatCompletionResponse = {
    accum.error.foreach(raw => throw sessionErrorException(sessionId, raw))

    if (accum.stopReason.contains("requires_action"))
      throw new OpenAIScalaClientException(
        s"Managed-agent session '$sessionId' requested a client-side action (tool confirmation or custom tool result). Tool confirmations can be answered via the typed stream (createChatToolCompletionStreamed / createChatCompletionStreamedTyped + setToolApprovalDecisions); custom tool results are not supported."
      )

    if (accum.texts.isEmpty)
      throw new OpenAIScalaClientException(
        s"Managed-agent session '$sessionId' finished (${accum.stopReason.getOrElse("unknown stop reason")}) without producing an agent message."
      )

    ChatCompletionResponse(
      id = sessionId,
      created = new ju.Date(),
      model = model,
      system_fingerprint = accum.stopReason,
      choices = Seq(
        ChatCompletionChoiceInfo(
          message = OpenAIAssistantMessage(accum.texts.mkString("\n"), name = None),
          index = 0,
          finish_reason = accum.stopReason,
          logprobs = None
        )
      ),
      usage = Some(toUsageInfo(accum)),
      originalResponse = None
    )
  }

  private def toUsageInfo(accum: SessionTurnAccum): OpenAIUsageInfo = {
    val promptTokens = accum.inputTokens + accum.cacheCreationTokens + accum.cacheReadTokens

    OpenAIUsageInfo(
      prompt_tokens = promptTokens,
      completion_tokens = Some(accum.outputTokens),
      total_tokens = promptTokens + accum.outputTokens,
      prompt_tokens_details = Some(
        PromptTokensDetails(
          cached_tokens = accum.cacheReadTokens,
          audio_tokens = None
        )
      ),
      completion_tokens_details = None
    )
  }

  // ---------------------------------------------------------------------------
  // Streamed response assembly
  // ---------------------------------------------------------------------------

  private def toChunks(
    sessionId: String,
    model: String,
    envelope: SessionEventEnvelope
  ): List[ChatCompletionChunkResponse] =
    envelope.`type` match {
      case "agent.message" =>
        val text = agentMessageTexts(envelope.raw).mkString("\n")
        if (text.isEmpty) Nil else List(chunkResponse(sessionId, model, Some(text), None))

      case "session.status_idle" =>
        stopReasonType(envelope.raw) match {
          case Some("requires_action") =>
            throw new OpenAIScalaClientException(
              s"Managed-agent session '$sessionId' requested a client-side action (tool confirmation or custom tool result). Tool confirmations can be answered via the typed stream (createChatToolCompletionStreamed / createChatCompletionStreamedTyped + setToolApprovalDecisions); custom tool results are not supported."
            )
          case stopReason =>
            List(chunkResponse(sessionId, model, None, stopReason.orElse(Some("end_turn"))))
        }

      case "session.status_terminated" =>
        List(chunkResponse(sessionId, model, None, Some("terminated")))

      case "session.error" if isTerminalError(envelope.raw) =>
        throw sessionErrorException(sessionId, envelope.raw)

      case _ => Nil
    }

  private def chunkResponse(
    sessionId: String,
    model: String,
    content: Option[String],
    finishReason: Option[String]
  ): ChatCompletionChunkResponse =
    ChatCompletionChunkResponse(
      id = sessionId,
      created = new ju.Date,
      model = model,
      system_fingerprint = None,
      choices = Seq(
        ChatCompletionChoiceChunkInfo(
          delta = ChunkMessageSpec(role = None, content = content),
          index = 0,
          finish_reason = finishReason
        )
      ),
      usage = None
    )
}

// a pause this adapter cannot answer (a custom-tool action, an event missing from the history)
private final class UnanswerablePauseException(message: String)
    extends OpenAIScalaClientException(message)

object OpenAIAnthropicManagedAgentChatCompletionService {

  def apply(
    underlying: AnthropicService,
    fixedAgentId: Option[String] = None,
    environmentId: Option[String] = None,
    agentTools: Seq[AgentTool] = Seq(AgentTool.Toolset()),
    deleteSessionsAfterUse: Boolean = true
  )(
    implicit executionContext: ExecutionContext,
    materializer: Materializer
  ): OpenAIChatCompletionService with OpenAIChatCompletionStreamedServiceExtra =
    new OpenAIAnthropicManagedAgentChatCompletionService(
      underlying,
      fixedAgentId,
      environmentId,
      agentTools,
      deleteSessionsAfterUse
    )
}
