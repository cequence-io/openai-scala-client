package io.cequence.openaiscala.domain.responsesapi

import io.cequence.openaiscala.domain.ChatRole

sealed trait Message {
  val `type`: String = "message"
}

object Message {

  /**
   * A message input to the model with a role indicating instruction following hierarchy.
   * Instructions given with the developer or system role take precedence over instructions
   * given with the user role. Messages with the assistant role are presumed to have been
   * generated by the model in previous interactions.
   *
   * @param content
   *   Text
   * @param role
   *   The role of the message input
   */
  final case class InputText(
    content: String,
    role: ChatRole, // user, assistant, system, or developer
    status: Option[ModelStatus] = None
  ) extends Message
      with Input

  /**
   * Shortcut for creating a System input text message
   *
   * @param content
   *   Text
   * @return
   */
  def System(content: String) = InputText(content, ChatRole.System)

  /**
   * Shortcut for creating a Developer input text message
   *
   * @param content
   *   Text
   * @return
   */
  def Developer(content: String) = InputText(content, ChatRole.Developer)

  /**
   * Shortcut for creating a User input text message
   *
   * @param content
   *   Text
   * @return
   */
  def User(content: String) = InputText(content, ChatRole.User)

  /**
   * Shortcut for creating an Assistant input text message
   *
   * @param content
   *   Text
   * @return
   */
  def Assistant(content: String) = InputText(content, ChatRole.Assistant)

  /**
   * A message input to the model with a role indicating instruction following hierarchy.
   * Instructions given with the developer or system role take precedence over instructions
   * given with the user role. Messages with the assistant role are presumed to have been
   * generated by the model in previous interactions.
   *
   * @param content
   *   Text, image, or audio input to the model, used to generate a response
   * @param role
   *   The role of the message input
   */
  final case class InputContent(
    content: Seq[InputMessageContent],
    role: ChatRole, // user, assistant, system, or developer
    status: Option[ModelStatus] = None,
    id: Option[String] = None
  ) extends Message
      with Input

  /**
   * An output message from the model.
   *
   * @param content
   *   The content of the output message - list of texts or refusals
   * @param id
   *   The unique ID of the output message
   * @param role
   *   The role of the output message. Always assistant
   * @param status
   *   The status of the message input
   * @param `type`
   *   The type of the output message. Always message
   */
  final case class OutputContent(
    content: Seq[OutputMessageContent] = Nil,
    id: String,
    status: ModelStatus // in_progress, completed, or incomplete
  ) extends Message
      with Input
      with Output {

    val role: ChatRole = ChatRole.Assistant
  }
}
