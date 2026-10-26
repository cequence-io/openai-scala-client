package io.cequence.openaiscala.service.adapter

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.domain.guardrails.{GuardrailAction, GuardrailVerdict}
import io.cequence.openaiscala.domain.response.{
  ChatChunk,
  ChatCompletionChoiceChunkInfo,
  ChatCompletionChunkResponse,
  ChatCompletionResponse,
  ChatToolCompletionResponse,
  ChunkMessageSpec,
  UsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.{BaseMessage, ChatCompletionTool, ChatRole}
import io.cequence.openaiscala.service.StreamedServiceTypes.OpenAIChatCompletionStreamedService
import io.cequence.openaiscala.service.guardrails.{InputGuardrail, OutputGuardrail}
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionService,
  OpenAIChatCompletionStreamedServiceExtra
}
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * See [[OpenAIServiceAdapters.guardrailsWithStreaming]]: the calls that are not streamed go
 * through [[GuardrailsAdapter]]; a stream starts only once its input passed, and when output
 * guardrails are set, its reply is held back until the stream finishes, checked once, then
 * released.
 */
private class GuardrailsStreamedAdapter(
  input: Seq[InputGuardrail],
  output: Seq[OutputGuardrail],
  onViolation: GuardrailAction,
  outputReprompts: Int,
  onVerdict: GuardrailVerdict => Unit
)(
  underlying: OpenAIChatCompletionStreamedService
)(
  implicit ec: ExecutionContext
) extends OpenAIChatCompletionService
    with OpenAIChatCompletionStreamedServiceExtra {

  import GuardrailsAdapter._
  import GuardrailsStreamedAdapter._

  private val sync =
    new GuardrailsAdapter(input, output, onViolation, outputReprompts, onVerdict)(underlying)

  private val runner = new GuardrailRunner(input, output, onViolation, onVerdict)

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    sync.createChatCompletion(messages, settings)

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    sync.createChatToolCompletion(messages, tools, responseToolChoice, settings)

  override def createChatCompletionStreamed(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Source[ChatCompletionChunkResponse, NotUsed] =
    guarded(
      messages,
      new OpenAIChunks(settings.model),
      repromptsFor(settings, outputReprompts)
    )(
      underlying.createChatCompletionStreamed(_, settings)
    )

  // the typed stream - its final variants (no tools, with approvals) go through it as well
  override def createChatToolCompletionStreamed(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Source[ChatChunk, NotUsed] =
    guarded(
      messages,
      new TypedChunks(settings.model),
      repromptsFor(settings, outputReprompts)
    )(
      underlying.createChatToolCompletionStreamed(_, tools, responseToolChoice, settings)
    )

  override def close(): Unit =
    underlying.close()

  private def guarded[C](
    messages: Seq[BaseMessage],
    chunks: StreamChunks[C],
    reprompts: Int
  )(
    stream: Seq[BaseMessage] => Source[C, NotUsed]
  ): Source[C, NotUsed] =
    Source
      .futureSource(
        runner.checkInput(messages).map {
          case Nil if !runner.hasOutput =>
            stream(messages)

          case Nil =>
            checkedWhenFinished(
              messages,
              chunks,
              stream,
              reprompts,
              messages,
              attempt = 0,
              usageSoFar = None
            )

          case verdicts =>
            blocked(verdicts)(chunks.blocked(_, verdicts, idAndModel = None, usage = None))
        }
      )
      .mapMaterializedValue(_ => NotUsed)

  // the reply held back until the stream finishes, then checked once by the output guardrails -
  // everything it releases, not only the answer text;
  // a flagged one asked for again (up to `reprompts` times, the usage of all attempts summed)
  // unless a guard was unavailable
  private def checkedWhenFinished[C](
    messages: Seq[BaseMessage],
    chunks: StreamChunks[C],
    stream: Seq[BaseMessage] => Source[C, NotUsed],
    reprompts: Int,
    callMessages: Seq[BaseMessage],
    attempt: Int,
    usageSoFar: Option[UsageInfo]
  ): Source[C, NotUsed] =
    stream(callMessages).fold(Vector.empty[C])(_ :+ _).flatMapConcat { all =>
      // a restart voids everything before it
      val current = all.drop(all.lastIndexWhere(chunks.isRestart).max(0))
      val usage = UsageInfo.sumOption(usageSoFar, current.flatMap(chunks.usage).lastOption)
      val decision =
        runner.checkOutput(
          chunks.checkedReplies(current).map { case (_, reply) => reply },
          messages,
          attempt < reprompts
        )

      Source.future(decision).flatMapConcat {
        // a reprompted stream reports the usage of every attempt
        case OutputDecision.Pass =>
          val released =
            if (attempt == 0) current
            else usage.fold[Seq[C]](current)(chunks.withTotalUsage(current, _))
          Source(released.toList)

        case again: OutputDecision.AskAgain =>
          checkedWhenFinished(
            messages,
            chunks,
            stream,
            reprompts,
            again.messages(messages),
            attempt + 1,
            usage
          )

        case block: OutputDecision.Block =>
          blocked(block.verdicts)(
            chunks.blockedReplies(_, block.verdicts, current, block.flagged, usage)
          )
      }
    }

  private def blocked[C](
    verdicts: Seq[GuardrailVerdict]
  )(
    answer: String => Seq[C]
  ): Source[C, NotUsed] =
    runner.block(verdicts)(answer).fold(Source.failed, chunks => Source(chunks.toList))
}

private object GuardrailsStreamedAdapter {

  /** The kind of the typed stream's `Other` chunk that carries the blocking verdicts. */
  val BlockKind = "guardrail_block"

  /** How a stream's chunks carry the reply, and how a block is written in them. */
  trait StreamChunks[C] {

    /** The answer text fragments of a chunk, by choice index. */
    def texts(chunk: C): Seq[(Int, String)]

    /**
     * The other text a chunk releases - reasoning, refusals, server-side tool output,
     * citations, a typed stream's further choices, raw extras - as (choice index, channel,
     * fragment). Tool-call arguments are not checked, as on the sync path.
     */
    def otherTexts(chunk: C): Seq[(Int, String, String)]

    /** The answer of each choice of a stream, by choice index, in choice order. */
    def repliesByChoice(chunks: Seq[C]): Seq[(Int, String)] =
      chunks
        .flatMap(texts)
        .groupBy { case (index, _) => index }
        .toSeq
        .sortBy { case (index, _) => index }
        .map { case (index, parts) => index -> parts.map { case (_, text) => text }.mkString }

    /**
     * Each choice's reply as the output guardrails check it, in choice order: its answer, then
     * each other channel - a channel's fragments joined as they came, so a text split across
     * deltas is checked whole, the channels a line apart.
     */
    def checkedReplies(chunks: Seq[C]): Seq[(Int, Reply)] = {
      val answers = repliesByChoice(chunks).toMap
      val others = chunks.flatMap(otherTexts)
      val indices = (answers.keys.toSeq ++ others.map { case (index, _, _) => index }).distinct

      indices.sorted.map { index =>
        val allChannels = others.collect { case (`index`, channel, _) => channel }.distinct
        val channels = allChannels.filterNot(repeated(allChannels))
        val texts = answers.get(index).toSeq ++ channels.map { channel =>
          others.collect { case (`index`, `channel`, text) => text }.mkString
        }
        index -> Reply(
          texts.filter(_.trim.nonEmpty).mkString("\n"),
          answers.getOrElse(index, "")
        )
      }
    }

    /**
     * The channels of a reply that repeat another of its channels, left out of its check (none
     * by default).
     */
    protected def repeated(channels: Seq[String]): Set[String] = Set.empty

    def isRestart(chunk: C): Boolean

    def idAndModel(chunk: C): Option[(String, String)]

    def usage(chunk: C): Option[UsageInfo]

    /**
     * The chunks with `usage` as their usage: the chunks that report one carry it instead,
     * else it is added at the end (before a terminator).
     */
    def withTotalUsage(
      chunks: Seq[C],
      usage: UsageInfo
    ): Seq[C]

    /**
     * The chunks answering a blocked stream with `message` (finish reason `content_filter`),
     * with the stream's own id and model when it had begun, and the usage it had reported.
     */
    def blocked(
      message: String,
      verdicts: Seq[GuardrailVerdict],
      idAndModel: Option[(String, String)],
      usage: Option[UsageInfo]
    ): Seq[C]

    /**
     * The chunks of a finished stream whose `flagged` replies are answered with `message` -
     * the other replies (several choices) released as they came.
     */
    def blockedReplies(
      message: String,
      verdicts: Seq[GuardrailVerdict],
      current: Seq[C],
      flagged: String => Boolean,
      usage: Option[UsageInfo]
    ): Seq[C]
  }

  final class TypedChunks(model: String) extends StreamChunks[ChatChunk] {

    override def texts(chunk: ChatChunk): Seq[(Int, String)] =
      chunk match {
        case ChatChunk.Text(text) => Seq(0 -> text)
        case _                    => Nil
      }

    override def otherTexts(chunk: ChatChunk): Seq[(Int, String, String)] =
      chunk match {
        case ChatChunk.Thinking(text) =>
          Seq((0, "thinking", text))

        case ChatChunk.Refusal(text) =>
          Seq((0, "refusal", text))

        // its textual view when it has one (the raw content carries the same and the payload's
        // metadata), else the text of the raw content
        case result: ChatChunk.ToolResult =>
          val text = result.text.map(Seq(_)).getOrElse(stringLeaves(result.content))
          Seq((0, s"tool_result:${result.callId}", text.mkString("\n")))

        case execution: ChatChunk.CodeExecution =>
          Seq((0, s"code:${execution.callId}", execution.code))

        case result: ChatChunk.CodeExecutionResult =>
          val text = result.output.map(Seq(_)).getOrElse(stringLeaves(result.raw))
          Seq((0, s"code_result:${result.callId}", text.mkString("\n")))

        case search: ChatChunk.WebSearch =>
          Seq((0, s"web_search:${search.callId}", search.queries.mkString("\n")))

        case result: ChatChunk.WebSearchResult =>
          val lines = result.results.map(item => (item.title.toSeq :+ item.url).mkString(" "))
          Seq((0, s"web_search_result:${result.callId}", lines.mkString("\n")))

        case citation: ChatChunk.Citation =>
          val text = (citation.citedText.toSeq ++ citation.title).mkString(" ")
          Seq((0, "citations", text + "\n"))

        // a further choice (OpenAI-compatible hosts): its delta, under its own index
        case ChatChunk.Other(kind, raw) if kind.startsWith("choice[") =>
          val index = choiceIndex(kind)
          Seq("content", "reasoning_content", "reasoning", "refusal").flatMap { field =>
            (raw \ "delta" \ field).asOpt[String].map(text => (index, field, text))
          }

        // a further candidate (Gemini, Vertex AI): its parts, under its own index
        case ChatChunk.Other(kind, raw) if kind.startsWith("candidate[") =>
          val index = choiceIndex(kind)
          (raw \ "content" \ "parts").asOpt[Seq[JsValue]].getOrElse(Nil).flatMap { part =>
            val channel =
              if ((part \ "thought").asOpt[Boolean].contains(true))
                "thought"
              else
                "text"
            (part \ "text").asOpt[String].map(text => (index, channel, text))
          }

        case ChatChunk.Other(BlockKind, _) =>
          Nil

        // the events repeating text checked from its deltas (the answer, a reasoning summary)
        case ChatChunk.Other(kind, _) if RepeatedTextEvents.contains(kind) =>
          Nil

        // anything else released raw (content.*, subagent messages, unknown events): its text
        case ChatChunk.Other(kind, raw) =>
          stringLeaves(raw).map(text => (0, s"other:$kind", text + "\n"))

        case _ =>
          Nil
      }

    // the semantic layer repeats the tool layer: a call's code execution or web search result
    // is its ToolResult again (Anthropic, Gemini, Vertex AI emit both)
    override protected def repeated(channels: Seq[String]): Set[String] =
      channels.filter { channel =>
        SemanticResultChannels.exists { prefix =>
          channel.startsWith(prefix) &&
          channels.contains("tool_result:" + channel.stripPrefix(prefix))
        }
      }.toSet

    private val SemanticResultChannels = Seq("code_result:", "web_search_result:")

    // Responses API events whose text the stream's deltas already carried
    private val RepeatedTextEvents = Set(
      "response.content_part.added",
      "response.content_part.done",
      "response.reasoning_summary_part.added",
      "response.reasoning_summary_part.done"
    )

    // `choice[2]` / `candidate[2]` -> 2 (an unparsable index is checked as its own choice)
    private def choiceIndex(kind: String): Int =
      Try(kind.drop(kind.indexOf('[') + 1).takeWhile(_.isDigit).toInt).getOrElse(-1)

    override def isRestart(chunk: ChatChunk): Boolean = chunk.isInstanceOf[ChatChunk.Retry]

    override def idAndModel(chunk: ChatChunk): Option[(String, String)] =
      chunk match {
        case ChatChunk.Start(id, startModel) => Some(id -> startModel)
        case _                               => None
      }

    override def usage(chunk: ChatChunk): Option[UsageInfo] =
      chunk match {
        case ChatChunk.Usage(usage) => Some(usage)
        case _                      => None
      }

    override def withTotalUsage(
      chunks: Seq[ChatChunk],
      usage: UsageInfo
    ): Seq[ChatChunk] =
      if (chunks.exists(_.isInstanceOf[ChatChunk.Usage]))
        chunks.map {
          case _: ChatChunk.Usage => ChatChunk.Usage(usage)
          case other              => other
        }
      else
        chunks.lastOption match {
          case Some(ChatChunk.Done) => chunks.init :+ ChatChunk.Usage(usage) :+ ChatChunk.Done
          case _                    => chunks :+ ChatChunk.Usage(usage)
        }

    // a typed stream is blocked as a whole, whichever of its choices was flagged
    override def blockedReplies(
      message: String,
      verdicts: Seq[GuardrailVerdict],
      current: Seq[ChatChunk],
      flagged: String => Boolean,
      usage: Option[UsageInfo]
    ): Seq[ChatChunk] =
      blocked(message, verdicts, current.flatMap(idAndModel).headOption, usage)

    override def blocked(
      message: String,
      verdicts: Seq[GuardrailVerdict],
      idAndModel: Option[(String, String)],
      usage: Option[UsageInfo]
    ): Seq[ChatChunk] = {
      val (id, startModel) = idAndModel.getOrElse(GuardrailsAdapter.blockedId() -> model)

      Seq(
        ChatChunk.Start(id, startModel),
        ChatChunk.Text(message),
        ChatChunk.Other(BlockKind, verdictsJson(verdicts)),
        ChatChunk.Finish(
          ChatChunk.FinishReason.content_filter,
          Some(GuardrailsAdapter.ContentFilter)
        )
      ) ++ usage.map(ChatChunk.Usage(_)).toSeq
    }
  }

  final class OpenAIChunks(model: String) extends StreamChunks[ChatCompletionChunkResponse] {

    override def texts(chunk: ChatCompletionChunkResponse): Seq[(Int, String)] =
      chunk.choices.flatMap { choice =>
        choice.delta.content.filter(_.nonEmpty).map(choice.index -> _)
      }

    override def otherTexts(chunk: ChatCompletionChunkResponse): Seq[(Int, String, String)] =
      chunk.choices.flatMap { choice =>
        val delta = choice.delta
        delta.reasoning_content.map(text => (choice.index, "reasoning_content", text)).toSeq ++
          delta.reasoning.map(text => (choice.index, "reasoning", text)) ++
          delta.content_chunks.getOrElse(Nil).flatMap { raw =>
            stringLeaves(raw).map(text => (choice.index, "content_chunks", text + "\n"))
          }
      }

    override def isRestart(chunk: ChatCompletionChunkResponse): Boolean = false

    override def idAndModel(chunk: ChatCompletionChunkResponse): Option[(String, String)] =
      Option(chunk.id).filter(_.nonEmpty).map(_ -> chunk.model)

    override def usage(chunk: ChatCompletionChunkResponse): Option[UsageInfo] = chunk.usage

    override def withTotalUsage(
      chunks: Seq[ChatCompletionChunkResponse],
      usage: UsageInfo
    ): Seq[ChatCompletionChunkResponse] =
      if (chunks.exists(_.usage.isDefined))
        chunks.map(chunk =>
          if (chunk.usage.isDefined) chunk.copy(usage = Some(usage)) else chunk
        )
      else {
        val (id, chunkModel) =
          chunks
            .flatMap(idAndModel)
            .headOption
            .getOrElse(GuardrailsAdapter.blockedId() -> model)
        chunks :+ ChatCompletionChunkResponse(
          id,
          new Date(),
          chunkModel,
          system_fingerprint = None,
          choices = Nil,
          usage = Some(usage)
        )
      }

    override def blockedReplies(
      message: String,
      verdicts: Seq[GuardrailVerdict],
      current: Seq[ChatCompletionChunkResponse],
      flagged: String => Boolean,
      usage: Option[UsageInfo]
    ): Seq[ChatCompletionChunkResponse] = {
      val replies = checkedReplies(current)
      val flaggedIndices = replies.collect {
        case (index, reply) if flagged(reply.checked) => index
      }.toSet
      val idAndModelOfStream = current.flatMap(idAndModel).headOption

      if (flaggedIndices.size == replies.size)
        blocked(message, verdicts, idAndModelOfStream, usage)
      else {
        // the other choices as they came (a chunk left with no choice and no usage dropped),
        // each flagged one answered with the message, before the trailing usage-only chunks
        val kept = current.flatMap { chunk =>
          val choices = chunk.choices.filterNot(choice => flaggedIndices(choice.index))
          if (choices.isEmpty && chunk.choices.nonEmpty && chunk.usage.isEmpty) None
          else Some(chunk.copy(choices = choices))
        }
        val (body, trailer) =
          kept.splitAt(kept.lastIndexWhere(_.choices.nonEmpty) + 1)

        val (id, chunkModel) =
          idAndModelOfStream.getOrElse(GuardrailsAdapter.blockedId() -> model)
        val answers = flaggedIndices.toSeq.sorted.flatMap { index =>
          blockChunks(id, chunkModel, index, message, usage = None)
        }

        val released = body ++ answers ++ trailer
        usage.fold(released)(withTotalUsage(released, _))
      }
    }

    override def blocked(
      message: String,
      verdicts: Seq[GuardrailVerdict],
      idAndModel: Option[(String, String)],
      usage: Option[UsageInfo]
    ): Seq[ChatCompletionChunkResponse] = {
      val (id, chunkModel) = idAndModel.getOrElse(GuardrailsAdapter.blockedId() -> model)
      blockChunks(id, chunkModel, index = 0, message, usage)
    }

    // the message as a choice's reply, then its finish (content_filter) with the usage
    private def blockChunks(
      id: String,
      chunkModel: String,
      index: Int,
      message: String,
      usage: Option[UsageInfo]
    ): Seq[ChatCompletionChunkResponse] = {
      val created = new Date()

      def chunk(
        delta: ChunkMessageSpec,
        finishReason: Option[String],
        chunkUsage: Option[UsageInfo]
      ) =
        ChatCompletionChunkResponse(
          id,
          created,
          chunkModel,
          system_fingerprint = None,
          choices = Seq(ChatCompletionChoiceChunkInfo(delta, index, finishReason)),
          usage = chunkUsage
        )

      Seq(
        chunk(
          ChunkMessageSpec(role = Some(ChatRole.Assistant), content = Some(message)),
          finishReason = None,
          chunkUsage = None
        ),
        chunk(
          ChunkMessageSpec(role = None, content = None),
          Some(GuardrailsAdapter.ContentFilter),
          usage
        )
      )
    }
  }

  /**
   * The text of a raw JSON value - its string leaves, in order - for the output check. Left
   * out: a long string without whitespace (base64 data, a signature), which carries no text,
   * and the identifier and status fields (`type`, `object`, `status`, `role`, `id`, `*_id`),
   * which name the payload rather than release text.
   */
  def stringLeaves(json: JsValue): Seq[String] =
    json match {
      case JsString(text) =>
        if (text.length > MaxOpaqueStringLength && !text.exists(_.isWhitespace))
          Nil
        else
          Seq(text)
      case JsArray(items) => items.toSeq.flatMap(stringLeaves)
      case obj: JsObject =>
        obj.fields.toSeq.flatMap { case (key, value) =>
          if (MetadataFields.contains(key) || key.endsWith("_id"))
            Nil
          else
            stringLeaves(value)
        }
      case _ => Nil
    }

  private val MaxOpaqueStringLength = 512

  private val MetadataFields = Set("type", "object", "status", "role", "id")

  def verdictsJson(verdicts: Seq[GuardrailVerdict]): JsObject = {
    val stage: String = verdicts.headOption.fold("")(_.stage.toString)
    Json.obj(
      "stage" -> stage,
      "verdicts" -> verdicts.map { verdict =>
        Json.obj(
          "guardrail" -> verdict.guardrail,
          "flagged" -> verdict.flagged.map(_.name),
          "unavailable" -> verdict.unavailable
        )
      }
    )
  }
}
