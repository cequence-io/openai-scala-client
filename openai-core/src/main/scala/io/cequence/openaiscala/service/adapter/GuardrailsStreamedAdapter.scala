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
import play.api.libs.json.{JsObject, Json}

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}

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

  // the reply held back until the stream finishes, then checked once by the output guardrails;
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
      val decision = runner.checkOutput(chunks.replies(current), messages, attempt < reprompts)

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

    /** The text fragments of a chunk, by choice index. */
    def texts(chunk: C): Seq[(Int, String)]

    /** The reply of each choice of a stream, by choice index, in choice order. */
    def repliesByChoice(chunks: Seq[C]): Seq[(Int, String)] =
      chunks
        .flatMap(texts)
        .groupBy { case (index, _) => index }
        .toSeq
        .sortBy { case (index, _) => index }
        .map { case (index, parts) => index -> parts.map { case (_, text) => text }.mkString }

    /** The reply of each choice of a stream, in choice order. */
    def replies(chunks: Seq[C]): Seq[String] =
      repliesByChoice(chunks).map { case (_, reply) => reply }

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

    // a typed stream has one reply - blocked as a whole
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
      val replies = repliesByChoice(current)
      val flaggedIndices = replies.collect {
        case (index, reply) if flagged(reply) => index
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
