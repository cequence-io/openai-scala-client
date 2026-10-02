package io.cequence.openaiscala.typesafe.service

import io.cequence.openaiscala.JsonFormats.eitherJsonSchemaWrites
import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.JsonSchemaDef
import io.cequence.openaiscala.typesafe.domain.{DecisionImage, Question}
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
 * Tool messages and an empty conversation are refused, and so is image content - unless the
 * host reads images (`toState(messages, images = true)`, Perplexity's Decisions API): a user
 * message with images then becomes an array of its parts, text as above and each image an
 * OpenAI-style `{"type": "image_url", "image_url": {"url": ...}}` part, which may be a base64
 * PNG, JPEG or WebP data URL of at most 2,048 tiles of 32 x 32 pixels. Everything this object
 * cannot map fails with an `OpenAIScalaClientException` - it is the OpenAI adapter's mapping,
 * so its errors wear the OpenAI adapter's type.
 *
 * '''Schema -> `questions`.''' Only the schema produces questions - one per property, named by
 * its path, with the description as the instructions and the enum / range as the criteria
 * (booleans -> noul, string enums -> choice, numeric enums or small ranges -> score, arrays of
 * string enums -> one noul per option, objects recursively). Anything else is refused up
 * front, naming every offending path.
 */
object TypeSafeChatMapping {

  /** The `state` the adapter sends for these messages (image content refused). */
  def toState(messages: Seq[BaseMessage]): JsValue = toState(messages, images = false)

  /**
   * The `state` the adapter sends for these messages.
   *
   * @param images
   *   whether the host reads images in the state (Perplexity's Decisions API does; TypeSafe's
   *   Jev and Liquid's d1 read an image part as text, so for them image content is refused)
   */
  def toState(
    messages: Seq[BaseMessage],
    images: Boolean
  ): JsValue = {
    val instructions = messages.collect {
      case SystemMessage(content, _)    => content
      case DeveloperMessage(content, _) => content
    }

    val turns: Seq[(String, JsValue)] = messages.collect {
      case UserMessage(content, _) => "user" -> userContent(content)
      case UserSeqMessage(content, _) =>
        "user" -> userSeqContent(content, images)
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

  /**
   * The `questions` the adapter sends for this schema, named by their paths:
   * `customer.is_angry` is `is_angry` inside `customer`, `topics.[payments]` the option
   * `payments` of the multi-select `topics`, and a `.` or `\` inside a property name or an
   * option is escaped with a backslash (the property `a.b` is `a\.b`). The answers in
   * `originalResponse` carry the same names.
   */
  def toQuestions(schema: JsonSchemaDef): Map[String, Question] = plan(schema).questions

  // the adapter's own plan (it also needs the slots to assemble the answers); a schema System
  // One cannot answer fails the way every other misuse here does
  private[typesafe] def plan(schema: JsonSchemaDef): SchemaQuestions.Plan =
    try SchemaQuestions.plan(Json.toJson(schema.structure))
    catch { case e: IllegalArgumentException => fail(e.getMessage) }

  // text parts are joined into one text; with images (allowed) the message is an array of its
  // parts in order - consecutive text parts joined, each image an `image_url` part
  private def userSeqContent(
    content: Seq[Content],
    images: Boolean
  ): JsValue = {
    val parts: Seq[Either[String, String]] = content.map {
      case TextContent(text)    => Left(text)
      case ImageURLContent(url) => Right(url)
      case other =>
        fail(
          s"Only text and image content are supported; got ${other.getClass.getSimpleName}."
        )
    }

    val imageUrls = parts.collect { case Right(url) => url }

    if (imageUrls.isEmpty)
      userContent(parts.collect { case Left(text) => text }.mkString("\n"))
    else if (!images)
      fail(
        "Image content is read only by Perplexity's Decisions API " +
          "(TypeSafeServiceFactory.perplexityAsOpenAI, or asOpenAI(service, imageInput = true)) - " +
          "TypeSafe's Jev and Liquid's d1 would read it as text."
      )
    else {
      val problems = imageUrls.flatMap(DecisionImage.problem)
      if (problems.nonEmpty)
        fail(s"The message carries an image the API cannot take: ${problems.mkString("; ")}.")

      val grouped = parts.foldLeft(Vector.empty[Either[String, String]]) {
        case (acc :+ Left(previous), Left(text)) => acc :+ Left(previous + "\n" + text)
        case (acc, part)                         => acc :+ part
      }
      JsArray(grouped.map {
        case Left(text) => userContent(text)
        case Right(url) => DecisionImage.part(url)
      })
    }
  }

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
