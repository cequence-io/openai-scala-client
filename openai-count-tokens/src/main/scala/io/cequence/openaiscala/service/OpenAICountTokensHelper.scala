package io.cequence.openaiscala.service

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import io.cequence.openaiscala.JsonFormats
import io.cequence.openaiscala.domain.AssistantTool.FunctionTool
import io.cequence.openaiscala.domain._
import play.api.libs.json.Json

// based on: https://jtokkit.knuddels.de/docs/getting-started/recipes/chatml
trait OpenAICountTokensHelper {

  private lazy val registry = Encodings.newLazyEncodingRegistry()

  def countMessageTokens(
    model: String,
    messages: Seq[BaseMessage]
  ): Int = {
    val encoding = registry.getEncodingForModel(model).orElseThrow
    val (tokensPerMessage, tokensPerName) = tokensPerMessageAndName(model)

    val sum =
      messages.map(countMessageTokensAux(tokensPerMessage, tokensPerName, encoding)).sum

    sum + 3 // every reply is primed with <|start|>assistant<|message|>
  }

  def countMessageTokens(
    model: String,
    message: BaseMessage
  ): Int = {
    val encoding = registry.getEncodingForModel(model).orElseThrow
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
        // failover to (3, 1)
        (3, 1)
    }

  private def countContentAndExtra(
    encoding: Encoding,
    message: BaseMessage
  ): Int = {
    def count(s: String*) = s.map(encoding.countTokens).sum
    def countOpt(s: Option[String]) = s.map(count(_)).getOrElse(0)

    message match {
      case m: SystemMessage => count(m.content)
      case m: UserMessage   => count(m.content)
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
    val encoding = registry.getEncodingForModel(model).orElseThrow
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
}
