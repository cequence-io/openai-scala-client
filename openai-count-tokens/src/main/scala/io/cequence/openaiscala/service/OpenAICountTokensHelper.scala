package io.cequence.openaiscala.service

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.{Encoding, EncodingType}
import io.cequence.openaiscala.JsonFormats
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import play.api.libs.json.Json
import com.knuddels.jtokkit.api.ModelType
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Helper trait for counting tokens in messages and texts using the jtokkit library.
 *
 * Based on: [[https://jtokkit.knuddels.de/docs/getting-started/recipes/chatml]]
 *
 * Supported chat models (encoding used):
 *   - gpt-4.1, gpt-4.5, gpt-5.x, gpt-6.x, o1/o3/o4-series, chatgpt-4o*, chat-latest, gpt-oss*
 *     (o200k_base)
 *   - gpt-4o, gpt-4o-mini (o200k_base)
 *   - gpt-4, gpt-4-32k, gpt-4-turbo (cl100k_base)
 *   - gpt-3.5-turbo, gpt-3.5-turbo-16k (cl100k_base)
 *
 * Supported embedding models:
 *   - text-embedding-ada-002 (cl100k_base)
 *   - text-embedding-3-small (cl100k_base)
 *   - text-embedding-3-large (cl100k_base)
 *
 * Legacy models such as text-davinci are also supported.
 *
 * For any other/unrecognized model - including newer OpenAI chat models not explicitly listed
 * above and non-OpenAI ids (e.g. claude-*, gemini-*) - the o200k_base encoding is used as a
 * best-effort estimate, and a one-time warning is logged per distinct model name.
 */
trait OpenAICountTokensHelper {

  // Shared JVM-wide registry (see the companion object): an encoding is large (o200k_base holds
  // ~200K byte-array keys plus their lookup maps, i.e. tens of MB) and thread-safe, so a
  // per-instance registry would needlessly multiply that footprint per mixing class instance.
  private def registry = OpenAICountTokensHelper.sharedEncodingRegistry

  /**
   * Resolves the jtokkit [[Encoding]] to use for a given (possibly unknown/non-OpenAI) model
   * id.
   *
   * Order of resolution:
   *   1. Explicit o200k_base families are matched FIRST, before delegating to jtokkit's own
   *      `getEncodingForModel`. This is needed because jtokkit 1.1.0's `ModelType` list stops
   *      at gpt-4o/gpt-4o-mini and its own prefix fallback would otherwise silently mis-route
   *      e.g. "gpt-4.1" or "gpt-4.5-preview" to cl100k_base (they start with "gpt-4"). 2.
   *      jtokkit's own model registry/prefix lookup (covers gpt-4, gpt-4-32k, gpt-4-turbo,
   *      gpt-4o(-mini), gpt-3.5-turbo(-16k), legacy models, etc). 3. Fallback to o200k_base as
   *      a best-effort estimate for anything else (newer OpenAI models not yet in jtokkit, or
   *      non-OpenAI model ids), with a one-time warning.
   */
  private[service] def encodingFor(model: String): Encoding = {
    val lowerCased = model.toLowerCase

    val o200kPrefixes =
      Seq(
        "gpt-4.1",
        "gpt-4.5",
        "gpt-5",
        "gpt-6",
        "chatgpt-4o",
        "chat-latest",
        "o1",
        "o3",
        "o4"
      )

    if (
      o200kPrefixes.exists(prefix => lowerCased.startsWith(prefix)) ||
      lowerCased.contains("gpt-oss")
    ) {
      registry.getEncoding(EncodingType.O200K_BASE)
    } else {
      val fromRegistry = registry.getEncodingForModel(model)

      if (fromRegistry.isPresent) {
        fromRegistry.get()
      } else {
        OpenAICountTokensHelper.warnUnknownModelOnce(model)
        registry.getEncoding(EncodingType.O200K_BASE)
      }
    }
  }

  def countMessageTokens(
    model: String,
    messages: Seq[BaseMessage]
  ): Int = {
    val encoding = encodingFor(model)
    val (tokensPerMessage, tokensPerName) = tokensPerMessageAndName(model)

    val sum =
      messages.map(countMessageTokensAux(tokensPerMessage, tokensPerName, encoding)).sum

    sum + 3 // every reply is primed with <|start|>assistant<|message|>
  }

  def countMessageTokens(
    model: String,
    message: BaseMessage
  ): Int = {
    val encoding = encodingFor(model)
    val (tokensPerMessage, tokensPerName) = tokensPerMessageAndName(model)

    countMessageTokensAux(tokensPerMessage, tokensPerName, encoding)(message)
  }

  private def countMessageTokensAux(
    tokensPerMessage: Int,
    tokensPerName: Int,
    encoding: Encoding
  )(
    message: BaseMessage
  ) = {
    tokensPerMessage +
      countContentAndExtra(encoding, message) +
      encoding.countTokens(message.role.toString) +
      message.nameOpt.map { name => encoding.countTokens(name) + tokensPerName }.getOrElse(0)
  }

  private def tokensPerMessageAndName(model: String): (Int, Int) =
    model match {
      case ModelId.gpt_3_5_turbo_0301 =>
        // every message follows <|start|>{role/name}\n{content}<|end|>\n
        // if there's a name, the role is omitted
        (4, -1)
      case ModelId.gpt_3_5_turbo_0613 | ModelId.gpt_3_5_turbo_16k_0613 | ModelId.gpt_4_0613 |
          ModelId.gpt_4_32k_0613 | ModelId.gpt_4_turbo_2024_04_09 =>
        (3, 1)
      case ModelId.gpt_3_5_turbo => tokensPerMessageAndName(ModelId.gpt_3_5_turbo_0613)
      case ModelId.gpt_4         => tokensPerMessageAndName(ModelId.gpt_4_0613)
      case _                     =>
        // failover to (3, 1) - this also covers all newer/unlisted models (gpt-4.1+, gpt-5.x,
        // gpt-6.x, o-series, non-OpenAI ids, ...), which is correct since (3, 1) has been the
        // per-message/per-name overhead for every model since gpt-3.5-turbo-0613/gpt-4-0613
        (3, 1)
    }

  private def countContentAndExtra(
    encoding: Encoding,
    message: BaseMessage
  ): Int = {
    def count(s: String*) = s.map(encoding.countTokens).sum
    def countOpt(s: Option[String]) = s.map(count(_)).getOrElse(0)

    message match {
      case m: SystemMessage    => count(m.content)
      case m: DeveloperMessage => count(m.content)
      case m: UserMessage      => count(m.content)
      case m: UserSeqMessage =>
        val contents = m.content.map(Json.toJson(_)(JsonFormats.contentWrites).toString())
        count(contents: _*)

      case m: AssistantMessage => count(m.content)

      case m: AssistantToolMessage =>
        val toolCallTokens = m.tool_calls.map { case (id, toolSpec) =>
          toolSpec match {
            case toolSpec: FunctionCallSpec =>
              count(
                id,
                toolSpec.name,
                toolSpec.arguments
              ) + 3 // plus extra three tokens per function/tool call
          }
        }

        toolCallTokens.sum + countOpt(m.content)

      case m: AssistantFunMessage =>
        val funCallTokens = m.function_call
          .map(c => count(c.name, c.arguments) + 3 // plus extra three tokens per function call
          )
          .getOrElse(0)

        funCallTokens + countOpt(m.content)

      case m: ToolMessage => count(m.tool_call_id) + countOpt(m.content)
      case m: FunMessage  => count(m.content)
      case m: MessageSpec => count(m.content)
    }
  }

  def countFunMessageTokens(
    model: String,
    messages: Seq[BaseMessage],
    functions: Seq[FunctionTool],
    responseFunctionName: Option[String]
  ): Int = {
    val encoding = encodingFor(model)
    val (tokensPerMessage, tokensPerName) = tokensPerMessageAndName(model)

    def countMessageTokens(message: BaseMessage) =
      countMessageTokensAux(tokensPerMessage, tokensPerName, encoding)(message)

    val messagesTokensCount = messages
      .foldLeft((false, 0)) { case ((paddedSystem, count), message) =>
        val (newPaddedFlag, paddedMessage) =
          if (message.role == ChatRole.System && !paddedSystem) {
            message match {
              case m: SystemMessage =>
                (true, m.copy(content = m.content + "\n"))
              case m: MessageSpec if m.role == ChatRole.System =>
                (true, m.copy(content = m.content + "\n"))
              case _ =>
                throw new IllegalArgumentException(s"Unexpected message: $message")
            }
          } else {
            (paddedSystem, message)
          }

        (newPaddedFlag, count + countMessageTokens(paddedMessage))
      }
      ._2

    val functionsTokensCount = functionsTokensEstimate(encoding, functions)
    val systemRoleAdjustment = if (messages.exists(m => m.role == ChatRole.System)) -4 else 0
    val responseFunctionNameCount =
      responseFunctionName.map(name => encoding.countTokens(name) + 4).getOrElse(0)

    messagesTokensCount + functionsTokensCount + systemRoleAdjustment + responseFunctionNameCount + 3
  }

  private def functionsTokensEstimate(
    encoding: Encoding,
    functions: Seq[FunctionTool]
  ): Int = {
    val promptDefinitions = FunctionCallOpenAISerializer.formatFunctionDefinitions(functions)
    encoding.countTokens(promptDefinitions) + 9
  }

  /**
   * Counts the tokens of a text using the encoding for the given model type. Default model
   * type is GPT_4O, which uses the O200K_BASE encoding.
   *
   * @param text
   * @param modelType
   * @return
   */
  def countTokens(
    text: String,
    modelType: Option[ModelType] = None
  ): Int = {
    val encoding = registry.getEncodingForModel(modelType.getOrElse(ModelType.GPT_4O))
    encoding.countTokens(text)
  }

  /**
   * Counts the tokens of a text using the encoding resolved for the given (possibly
   * unknown/non-OpenAI) model id - see [[encodingFor]] for the resolution rules.
   *
   * @param text
   * @param model
   * @return
   */
  def countTokens(
    text: String,
    model: String
  ): Int = {
    val encoding = encodingFor(model)
    encoding.countTokens(text)
  }
}

object OpenAICountTokensHelper {

  /**
   * One lazy encoding registry per JVM. Encodings and the registry are thread-safe (see the
   * jtokkit docs), and each encoding retains a substantial byte-array/token map, so all
   * instances mixing in [[OpenAICountTokensHelper]] share this single registry.
   */
  private lazy val sharedEncodingRegistry = Encodings.newLazyEncodingRegistry()

  private lazy val logger = Logger(LoggerFactory.getLogger(classOf[OpenAICountTokensHelper]))

  // Small JVM-wide set of model names we've already warned about, so a hot loop that keeps
  // calling countMessageTokens/countTokens with an unknown model doesn't spam the log.
  private val warnedAboutModels: java.util.Set[String] =
    Collections.newSetFromMap(new ConcurrentHashMap[String, java.lang.Boolean]())

  private[service] def warnUnknownModelOnce(model: String): Unit =
    if (warnedAboutModels.add(model)) {
      logger.warn(
        s"No jtokkit encoding found for model '$model'. Falling back to the o200k_base " +
          "encoding as an estimate."
      )
    }
}
