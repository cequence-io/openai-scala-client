package io.cequence.openaiscala.service.adapter

import io.cequence.openaiscala.OpenAIScalaGuardrailException
import io.cequence.openaiscala.domain.guardrails.{
  GuardrailAction,
  GuardrailBlock,
  GuardrailVerdict
}
import io.cequence.openaiscala.domain.response.{
  ChatCompletionChoiceInfo,
  ChatCompletionResponse,
  ChatToolCompletionChoiceInfo,
  ChatToolCompletionResponse,
  UsageInfo
}
import io.cequence.openaiscala.domain.settings.CreateChatCompletionSettings
import io.cequence.openaiscala.domain.settings.ToolApprovalSettingsOps._
import io.cequence.openaiscala.domain.{
  AssistantMessage,
  AssistantToolMessage,
  BaseMessage,
  ChatCompletionTool,
  UserMessage
}
import io.cequence.openaiscala.service.OpenAIChatCompletionService
import io.cequence.openaiscala.service.guardrails.{InputGuardrail, OutputGuardrail}
import io.cequence.wsclient.service.CloseableService
import io.cequence.wsclient.service.adapter.ServiceWrapper
import org.slf4j.LoggerFactory

import java.util.{Date, UUID}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
 * See [[OpenAIServiceAdapters.guardrails]]: the input guardrails run (concurrently) before the
 * call, the output guardrails on every non-empty reply after it.
 */
private class GuardrailsAdapter[S <: OpenAIChatCompletionService](
  input: Seq[InputGuardrail],
  output: Seq[OutputGuardrail],
  onViolation: GuardrailAction,
  outputReprompts: Int,
  onVerdict: GuardrailVerdict => Unit
)(
  underlying: S
)(
  implicit ec: ExecutionContext
) extends ServiceWrapper[S]
    with CloseableService
    with OpenAIChatCompletionService {

  import GuardrailsAdapter._

  require(outputReprompts >= 0, s"outputReprompts must not be negative, got $outputReprompts.")

  private val runner = new GuardrailRunner(input, output, onViolation, onVerdict)

  override def wrap[T](
    fun: S => Future[T]
  ): Future[T] = fun(underlying)

  override def createChatCompletion(
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  ): Future[ChatCompletionResponse] =
    guarded(messages, settings)(underlying.createChatCompletion(_, settings))

  override def createChatToolCompletion(
    messages: Seq[BaseMessage],
    tools: Seq[ChatCompletionTool],
    responseToolChoice: Option[String],
    settings: CreateChatCompletionSettings
  ): Future[ChatToolCompletionResponse] =
    guarded(messages, settings)(
      underlying.createChatToolCompletion(_, tools, responseToolChoice, settings)
    )

  override def close(): Unit =
    underlying.close()

  // the input checked, the call made, its replies checked - a flagged reply asked for again
  // (the usage of all attempts summed) or blocked
  private def guarded[R](
    messages: Seq[BaseMessage],
    settings: CreateChatCompletionSettings
  )(
    call: Seq[BaseMessage] => Future[R]
  )(
    implicit replies: GuardedReplies[R]
  ): Future[R] = {
    val reprompts = repromptsFor(settings, outputReprompts)

    def blocked(verdicts: Seq[GuardrailVerdict])(respond: String => R): Future[R] =
      Future.fromTry(runner.block(verdicts)(respond).toTry)

    def callAndCheck(
      callMessages: Seq[BaseMessage],
      attempt: Int,
      usageSoFar: Option[UsageInfo]
    ): Future[R] =
      call(callMessages).flatMap { response =>
        val responseFinal =
          if (attempt == 0) response
          else
            replies.withUsage(
              response,
              UsageInfo.sumOption(usageSoFar, replies.usage(response))
            )

        runner.checkOutput(replies.texts(response), messages, attempt < reprompts).flatMap {
          case OutputDecision.Pass =>
            Future.successful(responseFinal)

          case again: OutputDecision.AskAgain =>
            callAndCheck(again.messages(messages), attempt + 1, replies.usage(responseFinal))

          case block: OutputDecision.Block =>
            blocked(block.verdicts)(
              replies.blockedReplies(responseFinal, _, block.flagged, block.verdicts)
            )
        }
      }

    runner.checkInput(messages).flatMap {
      case Nil      => callAndCheck(messages, attempt = 0, usageSoFar = None)
      case verdicts => blocked(verdicts)(replies.blocked(_, settings.model, verdicts))
    }
  }
}

/**
 * How a chat response carries its replies, and how a block is written in it - the counterpart
 * of the streamed adapter's `StreamChunks`.
 */
private trait GuardedReplies[R] {

  /** The text of each choice, in choice order (a tool-call-only reply has none). */
  def texts(response: R): Seq[String]

  def usage(response: R): Option[UsageInfo]

  def withUsage(
    response: R,
    usage: Option[UsageInfo]
  ): R

  /**
   * The response with each choice whose text is `flagged` answered with `message` (finish
   * reason `content_filter`), the other choices kept, the block attached.
   */
  def blockedReplies(
    response: R,
    message: String,
    flagged: String => Boolean,
    verdicts: Seq[GuardrailVerdict]
  ): R

  /** A response answering a blocked input with `message`. */
  def blocked(
    message: String,
    model: String,
    verdicts: Seq[GuardrailVerdict]
  ): R
}

private object GuardedReplies {

  import GuardrailsAdapter.{blockedId, ContentFilter}

  implicit val chatReplies: GuardedReplies[ChatCompletionResponse] =
    new GuardedReplies[ChatCompletionResponse] {

      override def texts(response: ChatCompletionResponse): Seq[String] =
        response.choices.map(_.message.content)

      override def usage(response: ChatCompletionResponse): Option[UsageInfo] = response.usage

      override def withUsage(
        response: ChatCompletionResponse,
        usage: Option[UsageInfo]
      ): ChatCompletionResponse = response.copy(usage = usage)

      override def blockedReplies(
        response: ChatCompletionResponse,
        message: String,
        flagged: String => Boolean,
        verdicts: Seq[GuardrailVerdict]
      ): ChatCompletionResponse =
        response.copy(
          choices = response.choices.map { choice =>
            if (flagged(choice.message.content)) answered(choice.index, message) else choice
          },
          originalResponse = Some(GuardrailBlock(verdicts, response.originalResponse))
        )

      override def blocked(
        message: String,
        model: String,
        verdicts: Seq[GuardrailVerdict]
      ): ChatCompletionResponse =
        ChatCompletionResponse(
          id = blockedId(),
          created = new Date(),
          model = model,
          system_fingerprint = None,
          choices = Seq(answered(index = 0, message)),
          usage = None,
          originalResponse = Some(GuardrailBlock(verdicts, None))
        )

      private def answered(
        index: Int,
        message: String
      ) =
        ChatCompletionChoiceInfo(AssistantMessage(message), index, Some(ContentFilter), None)
    }

  implicit val toolReplies: GuardedReplies[ChatToolCompletionResponse] =
    new GuardedReplies[ChatToolCompletionResponse] {

      override def texts(response: ChatToolCompletionResponse): Seq[String] =
        response.choices.flatMap(_.message.content)

      override def usage(response: ChatToolCompletionResponse): Option[UsageInfo] =
        response.usage

      override def withUsage(
        response: ChatToolCompletionResponse,
        usage: Option[UsageInfo]
      ): ChatToolCompletionResponse = response.copy(usage = usage)

      // a flagged choice's tool calls go with its text
      override def blockedReplies(
        response: ChatToolCompletionResponse,
        message: String,
        flagged: String => Boolean,
        verdicts: Seq[GuardrailVerdict]
      ): ChatToolCompletionResponse =
        response.copy(
          choices = response.choices.map { choice =>
            if (choice.message.content.exists(flagged)) answered(choice.index, message)
            else choice
          },
          originalResponse = Some(GuardrailBlock(verdicts, response.originalResponse))
        )

      override def blocked(
        message: String,
        model: String,
        verdicts: Seq[GuardrailVerdict]
      ): ChatToolCompletionResponse =
        ChatToolCompletionResponse(
          id = blockedId(),
          created = new Date(),
          model = model,
          system_fingerprint = None,
          choices = Seq(answered(index = 0, message)),
          usage = None,
          originalResponse = Some(GuardrailBlock(verdicts, None))
        )

      private def answered(
        index: Int,
        message: String
      ) =
        ChatToolCompletionChoiceInfo(
          AssistantToolMessage(content = Some(message)),
          index,
          Some(ContentFilter)
        )
    }
}

/** A reply flagged by the output guardrails, with their blocking verdicts. */
private final case class Violation(
  reply: String,
  verdicts: Seq[GuardrailVerdict]
) {
  def unavailable: Boolean = verdicts.exists(_.unavailable)
}

/** What the output check of an attempt's replies decides - for both guardrails adapters. */
private sealed trait OutputDecision

private object OutputDecision {

  case object Pass extends OutputDecision

  /** Ask for another reply: the flagged one and a note follow the call's own messages. */
  final case class AskAgain(violation: Violation) extends OutputDecision {
    def messages(original: Seq[BaseMessage]): Seq[BaseMessage] =
      original ++ Seq(
        AssistantMessage(violation.reply),
        UserMessage(GuardrailsAdapter.repromptMessage(violation.verdicts))
      )
  }

  /** Block the flagged replies. */
  final case class Block(violations: Seq[Violation]) extends OutputDecision {
    def verdicts: Seq[GuardrailVerdict] = violations.flatMap(_.verdicts)

    def flagged(reply: String): Boolean = violations.exists(_.reply == reply)
  }
}

/**
 * Runs the guardrails for both guardrails adapters (sync and streamed): every check of a stage
 * at once, every verdict reported to `onVerdict`, the blocking ones acted on.
 */
private class GuardrailRunner(
  input: Seq[InputGuardrail],
  output: Seq[OutputGuardrail],
  onViolation: GuardrailAction,
  onVerdict: GuardrailVerdict => Unit
)(
  implicit ec: ExecutionContext
) {

  import GuardrailsAdapter.logger

  def hasOutput: Boolean = output.nonEmpty

  /** The blocking verdicts of the input guardrails. */
  def checkInput(messages: Seq[BaseMessage]): Future[Seq[GuardrailVerdict]] =
    checkAll(input.map(guardrail => () => guardrail.checkInput(messages)))

  /**
   * The output guardrails' decision on an attempt's replies, every reply checked by every
   * guardrail at once: a flagged reply is asked for again while `canAskAgain` - unless a guard
   * was unavailable - else blocked.
   */
  def checkOutput(
    replies: Seq[String],
    messages: Seq[BaseMessage],
    canAskAgain: Boolean
  ): Future[OutputDecision] = {
    val texts = replies.filter(_.trim.nonEmpty).distinct

    if (output.isEmpty || texts.isEmpty) Future.successful(OutputDecision.Pass)
    else
      Future
        .traverse(texts) { text =>
          checkAll(output.map(guardrail => () => guardrail.checkOutput(messages, text)))
            .map(Violation(text, _))
        }
        .map(_.filter(_.verdicts.nonEmpty))
        .map {
          case Nil =>
            OutputDecision.Pass

          case violations @ (first +: _) if canAskAgain && !violations.exists(_.unavailable) =>
            logger.info(
              s"${GuardrailVerdict.describe(first.verdicts)} Asking for another reply."
            )
            OutputDecision.AskAgain(first)

          case violations =>
            OutputDecision.Block(violations)
        }
  }

  /**
   * A block as the call's answer: `Reject`'s exception, or `Respond`'s message as `respond`
   * writes it.
   */
  def block[T](
    verdicts: Seq[GuardrailVerdict]
  )(
    respond: String => T
  ): Either[OpenAIScalaGuardrailException, T] = {
    logger.info(GuardrailVerdict.describe(verdicts))
    onViolation match {
      case GuardrailAction.Reject => Left(new OpenAIScalaGuardrailException(verdicts))
      case GuardrailAction.Respond(message) => Right(respond(message(verdicts.head)))
    }
  }

  // runs the checks concurrently, reports every verdict and returns the blocking ones; a check
  // that fails (or throws) fails the call
  private def checkAll(
    checks: Seq[() => Future[GuardrailVerdict]]
  ): Future[Seq[GuardrailVerdict]] =
    Future.traverse(checks)(check => Future.unit.flatMap(_ => check())).map { verdicts =>
      verdicts.foreach(report)
      verdicts.filter(_.violation)
    }

  private def report(verdict: GuardrailVerdict): Unit =
    try {
      onVerdict(verdict)
    } catch {
      case NonFatal(e) =>
        logger.warn(s"The guardrail verdict callback failed: ${e.getMessage}")
    }
}

private object GuardrailsAdapter {

  private[adapter] val logger = LoggerFactory.getLogger("GuardrailsAdapter")

  // OpenAI's finish reason for a reply its content filter withheld
  private[adapter] val ContentFilter = "content_filter"

  private[adapter] def blockedId(): String = s"guardrail-${UUID.randomUUID()}"

  private[adapter] def repromptMessage(verdicts: Seq[GuardrailVerdict]): String = {
    val problems =
      verdicts.flatMap(_.flagged.map(_.name.replace('_', ' '))).distinct.mkString(", ")
    s"Your previous reply was blocked by a content check ($problems). " +
      "Answer my previous message again, without that problem."
  }

  // a call resuming a paused run is never asked again: its approval decisions are one-shot
  // (sending them twice re-runs or forks the run), and a resume ignores the appended messages
  private[adapter] def repromptsFor(
    settings: CreateChatCompletionSettings,
    outputReprompts: Int
  ): Int =
    if (settings.toolApprovalDecisions.nonEmpty) 0 else outputReprompts
}
