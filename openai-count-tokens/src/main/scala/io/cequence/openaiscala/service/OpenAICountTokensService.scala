package io.cequence.openaiscala.service

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import io.cequence.openaiscala.domain.{ChatRole, FunMessageSpec, FunctionSpec, MessageSpec}

class OpenAICountTokensService {
  private lazy val registry = Encodings.newLazyEncodingRegistry()
  private val encoding: Encoding = registry.getEncoding("cl100k_base").get()
  def countMessageTokens(
    messages: Seq[MessageSpec]
  ): Int = {
    val (tokensPerMessage, tokensPerName) = (3, 0)
    messages.foldLeft(3) { case (acc, messageSpec) =>
      acc +
        tokensPerMessage +
        encoding.countTokens(messageSpec.role.toString) +
        encoding.countTokens(messageSpec.content) +
        messageSpec.name.map(name => encoding.countTokens(name) + tokensPerName).getOrElse(0)
    }
  }

  def countFunMessageTokens(
    messages: Seq[FunMessageSpec],
    functions: Seq[FunctionSpec],
    responseFunctionName: Option[String]
  ): Int =
    messages
      .foldLeft((false, 0)) { case ((paddedSystem, count), message) =>
        if (message.role == ChatRole.System && !paddedSystem) {
          (
            true,
            count + messageTokensEstimate(
              message.copy(content = message.content.map(s => s + "\n"))
            )
          )
        } else {
          (paddedSystem, count + messageTokensEstimate(message))
        }
      }
      ._2 +
      3 +
      functionsTokensEstimate(functions.toList) +
      (if (messages.exists(m => m.role == ChatRole.System)) {
         -4
       } else 0) +
      responseFunctionName.map(name => stringTokens(name) + 4).getOrElse(0)

  private def stringTokens(s: String): Int =
    encoding.encode(s).size()

  private def messageTokensEstimate(message: FunMessageSpec): Int = {
    val components = List(
      Some(message.role.toString),
      message.content,
      message.name,
      message.function_call.map(_.name),
      message.function_call.map(_.arguments)
    ).flatten
    components.map(stringTokens).sum +
      3 +
      message.name.map(_ => -1).getOrElse(0) +
      message.function_call.map(_ => 3).getOrElse(0)
  }

  private def functionsTokensEstimate(functions: List[FunctionSpec]): Int = {
    val promptDefinitions = OpenAIFunctionsImpl.formatFunctionDefinitions(functions)
    stringTokens(promptDefinitions) + 9
  }
}
