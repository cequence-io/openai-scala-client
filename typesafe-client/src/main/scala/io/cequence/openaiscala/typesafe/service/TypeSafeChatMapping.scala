package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.JsonFormats.eitherJsonSchemaWrites
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import io.cequence.openaiscala.typesafe.domain.Question
import io.cequence.openaiscala.typesafe.service.impl.SchemaQuestions
import play.api.libs.json._

import scala.util.Try

/**
 * How the OpenAI chat-completion adapter ([[TypeSafeServiceFactory.asOpenAI]]) maps a
 * conversation and a JSON schema onto a System One request - exposed so you can preview (or
 * unit-test) exactly what will be sent.
 *
 * '''Messages -> `state`.''' System One has no prompt, only the state it judges, so every
 * message ends up there - but in named fields, which read better and cost less than a chat log
 * (see `examples/typesafe/TypeSafeMessageMappingBenchmark`):
 *
 *   - system / developer messages -> `instructions` (joined in order, blank line between)
 *   - a user message whose text is a JSON object or array -> embedded as that JSON, not as a
 *     string, so the docs' `` `message.order_id` `` path references work; other text as is
 *   - one user message and no instructions -> the state IS that message (text or JSON)
 *   - one user message with instructions -> `{"instructions": ..., "message": ...}`
 *   - several turns -> `{"instructions": ..., "conversation": [{"role", "content"}, ...]}`
 *
 * Image content, tool messages and an empty conversation are refused.
 *
 * '''Schema -> `questions`.''' Only the schema produces questions - one per property, named by
 * its path, with the description as the instructions and the enum / range as the criteria
 * (booleans -> noul, string enums -> choice, numeric enums or small ranges -> score, arrays of
 * string enums -> one noul per option, objects recursively). Anything else is refused up
 * front, naming every offending path.
 */
object TypeSafeChatMapping {

  /** The `state` the adapter sends for these messages. */
  def toState(messages: Seq[BaseMessage]): JsValue = {
    val instructions = messages.collect {
      case SystemMessage(content, _)    => content
      case DeveloperMessage(content, _) => content
    }

    val turns: Seq[(String, JsValue)] = messages.collect {
      case UserMessage(content, _) => "user" -> userContent(content)
      case UserSeqMessage(content, _) =>
        "user" -> userContent(content.map {
          case TextContent(text) => text
          case other =>
            fail(
              s"Only text content is supported; got ${other.getClass.getSimpleName}."
            )
        }.mkString("\n"))
      case AssistantMessage(content, _, _) => "assistant" -> JsString(content)
      case other
          if !other.isInstanceOf[SystemMessage] && !other.isInstanceOf[DeveloperMessage] =>
        fail(
          s"${other.getClass.getSimpleName} is not supported; send system / user / assistant messages."
        )
    }

    if (turns.isEmpty)
      fail("At least one user (or assistant) message is required - it is what gets judged.")

    val instructionsField =
      if (instructions.isEmpty) Json.obj()
      else Json.obj("instructions" -> instructions.mkString("\n\n"))

    turns match {
      case Seq(("user", content)) if instructions.isEmpty => content
      case Seq(("user", content)) => instructionsField + ("message" -> content)
      case many =>
        instructionsField + ("conversation" -> JsArray(many.map { case (role, content) =>
          Json.obj("role" -> role, "content" -> content)
        }))
    }
  }

  /** The `questions` the adapter sends for this schema (throws if it cannot be answered). */
  def toQuestions(schema: JsonSchemaDef): Map[String, Question] =
    SchemaQuestions.plan(Json.toJson(schema.structure)).questions

  // a user message that IS a JSON object or array goes in as structured state; a number, a
  // quoted string or plain prose stays text
  private def userContent(text: String): JsValue = {
    val trimmed = text.trim
    if (trimmed.startsWith("{") || trimmed.startsWith("["))
      Try(Json.parse(trimmed)).toOption.collect {
        case obj: JsObject => obj
        case arr: JsArray  => arr
      }.getOrElse(JsString(text))
    else JsString(text)
  }

  private def fail(message: String): Nothing =
    throw new OpenAIScalaClientException(message)
}
