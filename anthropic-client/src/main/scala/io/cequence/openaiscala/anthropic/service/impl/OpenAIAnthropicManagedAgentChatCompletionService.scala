package io.cequence.openaiscala.anthropic.service.impl

import akka.NotUsed
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{KillSwitches, Materializer}
import com.typesafe.scalalogging.Logger
import io.cequence.openaiscala.OpenAIScalaClientException
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
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChoiceInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChunkMessageSpec,
  PromptTokensDetails,
  UsageInfo => OpenAIUsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{
  BaseMessage,
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
import play.api.libs.json.{JsObject, JsValue}

import java.{util => ju}
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

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
 *   - Agents that request a client-side action (tool confirmation or a custom tool result)
 *     fail the call — this adapter cannot answer them. Use the built-in toolset or MCP tools,
 *     which the platform executes server-side.
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
  ): Future[ChatCompletionResponse] = {
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
  ): Source[ChatCompletionChunkResponse, NotUsed] = {
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
   * kickoff events are sent once it materializes, and the stream completes at the first
   * terminal event (idle / terminated / error). A kickoff-send failure aborts the stream.
   */
  private def turnEventSource(
    sessionId: String,
    kickoffEvents: Seq[SessionEvent]
  ): Source[SessionEventEnvelope, NotUsed] =
    underlying
      .streamSessionEvents(sessionId)
      .viaMat(KillSwitches.single)(Keep.right)
      .takeWhile(envelope => !isTerminal(envelope), inclusive = true)
      .mapMaterializedValue { killSwitch =>
        underlying.sendSessionEvents(sessionId, kickoffEvents).onComplete {
          case Failure(e) => killSwitch.abort(e)
          case _          => ()
        }
        NotUsed
      }

  private def isTerminal(envelope: SessionEventEnvelope): Boolean =
    envelope.`type` match {
      case "session.status_idle" | "session.status_terminated" | "session.error" => true
      case _                                                                     => false
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
    error: Option[String] = None
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

      case "session.error" =>
        accum.copy(error = Some(errorMessage(envelope.raw)))

      case _ => accum
    }

  private def toChatCompletionResponse(
    sessionId: String,
    model: String,
    accum: SessionTurnAccum
  ): ChatCompletionResponse = {
    accum.error.foreach { message =>
      throw new OpenAIScalaClientException(
        s"Managed-agent session '$sessionId' reported an error: $message"
      )
    }

    if (accum.stopReason.contains("requires_action"))
      throw new OpenAIScalaClientException(
        s"Managed-agent session '$sessionId' requested a client-side action (tool confirmation or custom tool result), which the chat-completion adapter cannot provide. Use server-executed tools (built-in toolset, MCP) instead."
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
              s"Managed-agent session '$sessionId' requested a client-side action (tool confirmation or custom tool result), which the chat-completion adapter cannot provide. Use server-executed tools (built-in toolset, MCP) instead."
            )
          case stopReason =>
            List(chunkResponse(sessionId, model, None, stopReason.orElse(Some("end_turn"))))
        }

      case "session.status_terminated" =>
        List(chunkResponse(sessionId, model, None, Some("terminated")))

      case "session.error" =>
        throw new OpenAIScalaClientException(
          s"Managed-agent session '$sessionId' reported an error: ${errorMessage(envelope.raw)}"
        )

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
