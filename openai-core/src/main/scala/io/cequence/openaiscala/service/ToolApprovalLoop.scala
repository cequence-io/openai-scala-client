package io.cequence.openaiscala.service

import akka.NotUsed
import akka.stream.scaladsl.Source
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain.response.{ChatChunk, ToolApprovalDecision, UsageInfo}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

/**
 * Human approval by callback: runs a typed chat stream that may pause for approval
 * (`ToolApprovalRequest`s + `Finish(approval_required)`, see [[ChatChunk]]) and, whenever a
 * round pauses, asks `decide` about each pending request (one at a time, in order) and resumes
 * the run with the decisions - joining all rounds into ONE stream that ends with the final
 * answer. The joined stream is shaped like a single run:
 *   - one `Start` (the first round's - for OpenAI the id of the first response);
 *   - no intermediate `Finish(approval_required)`: the answered `ToolApprovalRequest`s are
 *     consumed by the loop, and the `Finish` / `Usage` / `Done` of every round are held back -
 *     the joined stream ends with the last round's `Finish`, the usage summed over all rounds
 *     and `Done` (if the last round sent one);
 *   - tool-call ordinals (`ToolCallStart` / `ToolCallDelta` / `ToolCall` `index`) continue
 *     across rounds instead of restarting at 0.
 *
 * A round ends the joined stream paused - its requests and `Finish(approval_required)` passed
 * through, exactly like the plain typed stream - when `maxRounds` resumes were already made,
 * or when it also carries client-side function calls (the loop does not run local tools: run
 * them and resume with their tool messages and the decisions yourself). A stream restart
 * (`ChatChunk.Retry`) is passed through in the first round but fails the joined stream in a
 * resumed one (a retried resume may re-send answers that were already applied).
 *
 * Each materialization runs its own loop; nothing is sent before it is materialized. `decide`
 * runs on `ec` and may take as long as a human needs (return a Future, do not block); a failed
 * Future, or a decision answering another request, fails the stream - the run stays paused (an
 * Anthropic managed-agent session is kept: resume it or `deleteSession(runId)`).
 */
object ToolApprovalLoop {

  val DefaultMaxRounds = 10

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * @param settings
   *   the first round's settings, used as they are (they may carry decisions that resume a
   *   paused run); the resumed rounds send them with the decisions of the round before
   * @param decide
   *   answers one pending request - `request.approve` / `request.deny(reason)`
   * @param maxRounds
   *   how many times the run is resumed at most (0 = never, the plain stream)
   * @param stream
   *   one round: the typed stream for the given settings (the same messages and tools every
   *   round - e.g. `createChatToolCompletionStreamed(messages, tools, None, _)`)
   */
  def apply(
    settings: CreateChatCompletionSettings,
    decide: ChatChunk.ToolApprovalRequest => Future[ToolApprovalDecision],
    maxRounds: Int = DefaultMaxRounds
  )(
    stream: CreateChatCompletionSettings => Source[ChatChunk, NotUsed]
  )(
    implicit ec: ExecutionContext
  ): Source[ChatChunk, NotUsed] = {
    require(maxRounds >= 0, s"maxRounds must not be negative, got $maxRounds.")

    // one round, then (after its last chunk, the decisions and only if not cancelled) the next
    def round(
      number: Int,
      roundSettings: CreateChatCompletionSettings,
      indexBase: Int,
      priorUsage: Option[UsageInfo]
    ): Source[ChatChunk, NotUsed] = {
      val state = new RoundState(number, indexBase)
      val resume = Promise[Option[Resume]]()

      // `None` marks the end of the round - completing `resume` there (not on termination)
      // keeps a failed or cancelled round from asking for decisions
      val chunks = (stream(roundSettings).map(Some(_)) ++ Source.single(None)).mapConcat {
        case Some(chunk) =>
          state.add(chunk)

        case None =>
          val usage = (priorUsage.toList ++ state.usage).reduceOption(UsageInfo.sum)
          val runId = state.requests.headOption.fold("")(_.runId)

          if (state.requests.isEmpty) {
            resume.success(None)
            state.trailer(usage)
          } else if (state.clientToolCalls) {
            logger.warn(
              s"Run '$runId' paused for approval together with client-side function calls - ending the stream paused (run the calls, then resume with their tool messages and the decisions)."
            )
            resume.success(None)
            state.trailer(usage)
          } else if (number >= maxRounds) {
            if (maxRounds > 0)
              logger.warn(
                s"Run '$runId' paused for approval again after $maxRounds resumed round(s) - ending the stream paused (resume it yourself, or raise the round limit)."
              )
            resume.success(None)
            state.trailer(usage)
          } else {
            logger.info(
              s"Run '$runId' paused for approval of ${state.requests.size} call(s) - asking for decisions (round ${number + 1}/$maxRounds)."
            )
            resume.success(Some(Resume(state.requests, state.nextIndex, usage)))
            Nil
          }
      }

      val next = resume.future.flatMap {
        case Some(Resume(requests, nextIndexBase, usage)) =>
          decideAll(requests, decide).map(decisions =>
            round(
              number + 1,
              settings.setToolApprovalDecisions(decisions),
              nextIndexBase,
              usage
            )
          )

        case None =>
          Future.successful(Source.empty[ChatChunk])
      }

      chunks.concat(Source.futureSource(next).mapMaterializedValue(_ => NotUsed))
    }

    Source.lazySource(() => round(0, settings, 0, None)).mapMaterializedValue(_ => NotUsed)
  }

  // the requests one after another (a human answers one prompt at a time), each decision
  // checked to answer its request and bound to it as the provider sent it
  private def decideAll(
    requests: Seq[ChatChunk.ToolApprovalRequest],
    decide: ChatChunk.ToolApprovalRequest => Future[ToolApprovalDecision]
  )(
    implicit ec: ExecutionContext
  ): Future[Seq[ToolApprovalDecision]] =
    requests.foldLeft(Future.successful(Vector.empty[ToolApprovalDecision])) {
      (
        decided,
        request
      ) =>
        decided.flatMap { decisions =>
          Future.fromTry(Try(decide(request))).flatten.flatMap { decision =>
            if (
              decision.request.requestId == request.requestId &&
              decision.request.runId == request.runId
            )
              Future.successful(decisions :+ decision.copy(request = request))
            else
              Future.failed(
                new OpenAIScalaClientException(
                  s"The tool approval callback answered request '${request.requestId}' with a decision for '${decision.request.requestId}' - answer it with request.approve / request.deny(...)."
                )
              )
          }
        }
    }

  private final case class Resume(
    requests: Seq[ChatChunk.ToolApprovalRequest],
    nextIndexBase: Int,
    usage: Option[UsageInfo]
  )

  // what one round has seen so far (one instance per round and materialization)
  private final class RoundState(
    number: Int,
    indexBase: Int
  ) {
    var requests = Vector.empty[ChatChunk.ToolApprovalRequest]
    var clientToolCalls = false
    var usage: Option[UsageInfo] = None
    var nextIndex: Int = indexBase
    private var finish: Option[ChatChunk.Finish] = None
    private var done = false

    def add(chunk: ChatChunk): List[ChatChunk] =
      chunk match {
        case _: ChatChunk.Start if number > 0 => Nil
        case request: ChatChunk.ToolApprovalRequest =>
          requests :+= request
          Nil
        case f: ChatChunk.Finish =>
          finish = Some(f)
          Nil
        case ChatChunk.Usage(u) =>
          usage = Some(u)
          Nil
        case ChatChunk.Done =>
          done = true
          Nil
        case retry: ChatChunk.Retry =>
          if (number > 0)
            throw new OpenAIScalaClientException(
              s"A resumed tool-approval round was restarted (attempt ${retry.attempt}) - a retried resume may re-send answers that were already applied, so it cannot be joined; do not retry streams that carry approval decisions."
            )
          // the restarted attempt voids what the round has seen
          requests = Vector.empty
          clientToolCalls = false
          usage = None
          nextIndex = indexBase
          finish = None
          done = false
          List(retry)
        case c: ChatChunk.ToolCallStart => List(c.copy(index = shift(c.index)))
        case c: ChatChunk.ToolCallDelta => List(c.copy(index = shift(c.index)))
        case c: ChatChunk.ToolCall =>
          if (!c.serverSide) clientToolCalls = true
          List(c.copy(index = shift(c.index)))
        case other => List(other)
      }

    // the end of a stream that is not resumed: the pending requests (a paused run), the
    // Finish, the usage of all rounds, Done
    def trailer(totalUsage: Option[UsageInfo]): List[ChatChunk] =
      requests.toList ++ finish.toList ++ totalUsage.map(ChatChunk.Usage(_)).toList ++
        (if (done) List(ChatChunk.Done) else Nil)

    private def shift(index: Int): Int = {
      val shifted = indexBase + index
      nextIndex = math.max(nextIndex, shifted + 1)
      shifted
    }
  }
}
