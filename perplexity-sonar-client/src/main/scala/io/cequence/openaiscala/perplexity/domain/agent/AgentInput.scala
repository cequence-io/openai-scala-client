package io.cequence.openaiscala.perplexity.domain.agent

import io.cequence.wsclient.domain.EnumValue

/**
 * The `input` of an Agent API request (`POST /v1/agent`): a plain string, or a list of items
 * that replays a conversation (messages, function calls and their outputs).
 */
sealed trait AgentInput

object AgentInput {

  final case class Text(text: String) extends AgentInput

  final case class Items(items: Seq[AgentInputItem]) extends AgentInput

  def apply(text: String): AgentInput = Text(text)

  def apply(items: AgentInputItem*): AgentInput = Items(items)
}

sealed trait AgentInputItem

object AgentInputItem {

  /**
   * A conversation message (`type: message`); `content` is a string or a list of text / image
   * parts.
   */
  final case class Message(
    role: AgentRole,
    content: Either[String, Seq[AgentContentPart]]
  ) extends AgentInputItem

  object Message {
    def apply(
      role: AgentRole,
      text: String
    ): Message = Message(role, Left(text))

    def user(text: String): Message = Message(AgentRole.user, Left(text))
    def assistant(text: String): Message = Message(AgentRole.assistant, Left(text))
    def system(text: String): Message = Message(AgentRole.system, Left(text))
    def developer(text: String): Message = Message(AgentRole.developer, Left(text))
  }

  /**
   * A function call the model made earlier (`type: function_call`), replayed so that the
   * matching [[FunctionCallOutput]] has its call.
   */
  final case class FunctionCall(
    callId: String,
    name: String,
    arguments: String,
    thoughtSignature: Option[String] = None
  ) extends AgentInputItem

  /**
   * The result of a custom function call (`type: function_call_output`): a JSON string or a
   * list of text / image parts.
   */
  final case class FunctionCallOutput(
    callId: String,
    output: Either[String, Seq[AgentContentPart]],
    name: Option[String] = None,
    thoughtSignature: Option[String] = None
  ) extends AgentInputItem

  object FunctionCallOutput {
    def apply(
      callId: String,
      output: String
    ): FunctionCallOutput = FunctionCallOutput(callId, Left(output))
  }
}

sealed trait AgentRole extends EnumValue

object AgentRole {
  case object user extends AgentRole
  case object assistant extends AgentRole
  case object system extends AgentRole
  case object developer extends AgentRole

  def values: Seq[AgentRole] = Seq(user, assistant, system, developer)
}

sealed trait AgentContentPart

object AgentContentPart {

  final case class InputText(text: String) extends AgentContentPart

  /**
   * An image as an HTTPS URL or a base64 data URL (`data:image/png;base64,...`), at most 2048
   * characters for a URL.
   */
  final case class InputImage(imageUrl: String) extends AgentContentPart
}
