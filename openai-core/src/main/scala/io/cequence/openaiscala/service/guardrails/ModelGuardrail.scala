package io.cequence.openaiscala.service.guardrails

import io.cequence.openaiscala.OpenAIScalaJsonParseException
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.guardrails._
import io.cequence.openaiscala.domain.response.{ChatCompletionResponse, UsageInfo}
import io.cequence.openaiscala.domain.settings.{CreateChatCompletionSettings, JsonSchemaDef}
import io.cequence.openaiscala.service.{
  OpenAIChatCompletionExtra,
  OpenAIChatCompletionService,
  QuotedText
}
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsBoolean, JsLookupResult, JsObject, JsString}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * A guardrail answered by a chat model: the checks of a stage go out in ONE call, each as a
 * yes/no property of a JSON schema, so any `json_schema`-capable chat service can guard - an
 * LLM, or a decision model through `TypeSafeServiceFactory.asOpenAI` (each check a calibrated
 * yes/no question; Jev answers in about 250 ms). The guard only classifies: the checked text
 * goes to it inside `<USER_MESSAGE>` / `<ASSISTANT_REPLY>` tags with the instruction never to
 * follow it, and a tag inside the text is defused.
 *
 * Use one instance for both stages - the input stage asks `settings.inputChecks` about the
 * call's user messages (`settings.scope`), the output stage asks `settings.outputChecks` about
 * a reply, with the user's last message as context. System messages, tool calls and tool
 * results never go to the guard; images and files are marked as `[image]` / `[file]`.
 *
 * The guard service is not closed by this guardrail (nor by the guardrails adapter). Give it
 * the plain service, never one guarded by this very guardrail - each guard call would be
 * guarded again.
 */
final class ModelGuardrail(
  guard: OpenAIChatCompletionService,
  val settings: ModelGuardrailSettings
)(
  implicit ec: ExecutionContext
) extends InputGuardrail
    with OutputGuardrail {

  import ModelGuardrail._

  override val name: String = settings.name

  override def checkInput(
    messages: Seq[BaseMessage]
  ): Future[GuardrailVerdict] =
    inputText(messages) match {
      case Some(text) if settings.inputChecks.nonEmpty =>
        classify(GuardrailStage.Input, settings.inputChecks, quoted(UserTag, text))

      case _ =>
        Future.successful(GuardrailVerdict(name, GuardrailStage.Input))
    }

  override def checkOutput(
    messages: Seq[BaseMessage],
    reply: String
  ): Future[GuardrailVerdict] =
    if (reply.trim.isEmpty || settings.outputChecks.isEmpty)
      Future.successful(GuardrailVerdict(name, GuardrailStage.Output))
    else {
      val context = lastUserText(messages).map(quoted(UserTag, _) + "\n\n").getOrElse("")
      classify(GuardrailStage.Output, settings.outputChecks, context + quoted(ReplyTag, reply))
    }

  private def classify(
    stage: GuardrailStage,
    checks: Seq[GuardrailCheck],
    prompt: String
  ): Future[GuardrailVerdict] =
    Future.unit.flatMap { _ =>
      val chatSettings = settings.adjustSettings(
        CreateChatCompletionSettings(
          model = settings.model,
          jsonSchema = Some(schema(stage, checks))
        )
      )

      val (messagesFinal, settingsFinal) = OpenAIChatCompletionExtra.handleOutputJsonSchema(
        Seq(SystemMessage(instructions(stage, checks)), UserMessage(prompt)),
        chatSettings,
        taskNameForLogging = s"guardrail '$name'",
        enforceJsonSchemaMode = settings.enforceJsonSchemaMode
      )

      ask(messagesFinal, settingsFinal, checks, settings.parseRetries, None).map {
        case (results, response, usage) =>
          val verdict = GuardrailVerdict(
            name,
            stage,
            results,
            model = Some(response.model).filter(_.nonEmpty).orElse(Some(settingsFinal.model)),
            usage = usage
          )
          logger.debug(
            s"Guardrail '$name' (${verdict.model.getOrElse("")}) on the $stage: " +
              (if (verdict.violation) s"flagged ${verdict.flagged.map(_.name).mkString(", ")}"
               else "passed") + "."
          )
          verdict
      }
    }.recover { case NonFatal(e) =>
      val model = Some(settings.model)
      if (settings.failOpen) {
        logger.warn(
          s"Guardrail '$name' could not check the $stage - passing it (fail open): ${e.getMessage}"
        )
        GuardrailVerdict.failedOpen(name, stage, e, model)
      } else {
        logger.warn(
          s"Guardrail '$name' could not check the $stage - blocking it (fail closed): ${e.getMessage}"
        )
        GuardrailVerdict.failedClosed(name, stage, e, model)
      }
    }

  // asks again (up to retriesLeft times) while the answer is not a verdict; the usage of every
  // attempt is summed - each one was billed
  private def ask(
    messages: Seq[BaseMessage],
    chatSettings: CreateChatCompletionSettings,
    checks: Seq[GuardrailCheck],
    retriesLeft: Int,
    usageSoFar: Option[UsageInfo]
  ): Future[(Seq[GuardrailCheckResult], ChatCompletionResponse, Option[UsageInfo])] =
    guard.createChatCompletion(messages, chatSettings).flatMap { response =>
      val usage = UsageInfo.sumOption(usageSoFar, response.usage)

      parseResults(response, checks) match {
        case Right(results) =>
          Future.successful((results, response, usage))

        case Left(problem) if retriesLeft > 0 =>
          logger.warn(s"Guardrail '$name' got no usable answer ($problem) - asking again.")
          ask(messages, chatSettings, checks, retriesLeft - 1, usage)

        case Left(problem) =>
          Future.failed(
            new OpenAIScalaJsonParseException(
              s"Guardrail '$name' got no usable answer from '${chatSettings.model}': $problem",
              response
            )
          )
      }
    }

  // the stage's prompt template with its placeholders filled
  private def instructions(
    stage: GuardrailStage,
    checks: Seq[GuardrailCheck]
  ): String = {
    val template = stage match {
      case GuardrailStage.Input  => settings.prompts.input
      case GuardrailStage.Output => settings.prompts.output
    }

    template
      .replace(
        GuardrailPrompts.PolicyPlaceholder,
        settings.policy.map(_.trim).filter(_.nonEmpty).getOrElse("N/A")
      )
      .replace(
        GuardrailPrompts.ChecksPlaceholder,
        checks.map(check => s"- ${check.name}: ${question(check, stage)}").mkString("\n")
      )
  }

  private def inputText(
    messages: Seq[BaseMessage]
  ): Option[String] = {
    val userMessages = settings.scope match {
      // since the model last spoke - a system message after the user's must not hide it
      case GuardrailScope.NewUserMessages =>
        messages.reverse
          .takeWhile(message => !modelRoles.contains(message.role))
          .reverse
          .filter(_.role == ChatRole.User)

      case GuardrailScope.AllUserMessages =>
        messages.filter(_.role == ChatRole.User)
    }

    Some(userMessages.map(textOf).filter(_.trim.nonEmpty).mkString("\n\n")).filter(_.nonEmpty)
  }
}

object ModelGuardrail {

  private val logger = LoggerFactory.getLogger("ModelGuardrail")

  // the roles of the model's turns (a tool / function result answers a model's call)
  private val modelRoles: Set[ChatRole] =
    Set(ChatRole.Assistant, ChatRole.Tool, ChatRole.Function)

  private val UserTag = "USER_MESSAGE"
  private val ReplyTag = "ASSISTANT_REPLY"

  def apply(
    guard: OpenAIChatCompletionService,
    settings: ModelGuardrailSettings
  )(
    implicit ec: ExecutionContext
  ): ModelGuardrail =
    new ModelGuardrail(guard, settings)

  /** A guardrail with the default checks, failing closed. */
  def apply(
    guard: OpenAIChatCompletionService,
    model: String
  )(
    implicit ec: ExecutionContext
  ): ModelGuardrail =
    new ModelGuardrail(guard, ModelGuardrailSettings(model))

  /** The yes/no question of a check, as the guard gets it. */
  def question(
    check: GuardrailCheck,
    stage: GuardrailStage
  ): String = {
    val description = check.description.trim
    if (description.endsWith("?"))
      description
    else {
      val subject = stage match {
        case GuardrailStage.Input  => "the user's message"
        case GuardrailStage.Output => "the assistant's reply"
      }
      s"Does $subject ${description.stripSuffix(".")}?"
    }
  }

  /**
   * The JSON schema the guard answers: a yes/no property per check, named by it, plus a
   * `<name>_confidence` number for a check with a threshold - a decision model's adapter fills
   * that from the answer's probability (it is not asked), an LLM states it.
   */
  def schema(
    stage: GuardrailStage,
    checks: Seq[GuardrailCheck]
  ): JsonSchemaDef = {
    val properties = checks.flatMap { check =>
      val answer = check.name -> JsonSchema.Boolean(Some(question(check, stage)))
      val confidence = check.threshold.map { _ =>
        confidenceName(check) -> JsonSchema.Number(
          Some(
            s"How confident are you in your answer to ${check.name}, from 0 (a guess) to 1 (certain)?"
          )
        )
      }
      answer +: confidence.toSeq
    }

    JsonSchemaDef(
      name = "guardrail_verdict",
      strict = true,
      structure = JsonSchema.Object(properties, required = properties.map(_._1))
    )
  }

  private def confidenceName(check: GuardrailCheck) = s"${check.name}_confidence"

  private[guardrails] def parseResults(
    response: ChatCompletionResponse,
    checks: Seq[GuardrailCheck]
  ): Either[String, Seq[GuardrailCheckResult]] =
    Try(
      OpenAIChatCompletionExtra.defaultParseJsonOrRepair(
        OpenAIChatCompletionExtra.cleanupJsonContent(response.contentHead)
      )
    ) match {
      case Success(json: JsObject) =>
        val answers = checks.map(check => check -> flagOf(json \ check.name))
        val missing = answers.collect { case (check, None) => check.name }

        if (missing.nonEmpty)
          Left(s"no yes/no answer for ${missing.mkString(", ")}")
        else
          Right(answers.collect { case (check, Some(flag)) => result(check, flag, json) })

      case Success(other) =>
        Left(s"not a JSON object: ${other.toString.take(200)}")

      case Failure(e) =>
        Left(s"not JSON: ${e.getMessage}")
    }

  private def flagOf(value: JsLookupResult): Option[Boolean] =
    value.toOption.flatMap {
      case JsBoolean(flag) => Some(flag)
      case JsString(text) =>
        text.trim.toLowerCase match {
          case "true" | "yes" => Some(true)
          case "false" | "no" => Some(false)
          case _              => None
        }
      case _ => None
    }

  // the probability of a violation from the confidence in the given answer; a threshold
  // decides by it, else (no threshold, or no usable confidence) the answer itself does
  private def result(
    check: GuardrailCheck,
    flag: Boolean,
    json: JsObject
  ): GuardrailCheckResult = {
    val probability = check.threshold.flatMap { _ =>
      (json \ confidenceName(check))
        .asOpt[Double]
        .filter(confidence => confidence >= 0 && confidence <= 1)
        .map(confidence => round(if (flag) confidence else 1 - confidence))
    }

    val flagged = (check.threshold, probability) match {
      case (Some(threshold), Some(p)) => p >= threshold
      case _                          => flag
    }

    GuardrailCheckResult(check.name, flagged, probability)
  }

  private def round(value: Double): Double =
    BigDecimal(value).setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble

  // a message must not close its own tag (or open the other) and append "instructions"
  private val quoted = QuotedText(UserTag, ReplyTag)

  private def lastUserText(messages: Seq[BaseMessage]): Option[String] =
    messages.reverse.find(_.role == ChatRole.User).map(textOf).filter(_.trim.nonEmpty)

  private def textOf(message: BaseMessage): String =
    message match {
      case UserSeqMessage(parts, _) =>
        parts.map {
          case TextContent(text)  => text
          case _: ImageURLContent => "[image]"
          case file: FileContent  => s"[file${file.filename.map(" " + _).getOrElse("")}]"
        }.mkString("\n")

      case other =>
        BaseMessage.getTextContent(other).getOrElse("")
    }
}
