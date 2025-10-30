package io.cequence.openaiscala.domain.responsesapi.tools

/**
 * A tool that runs Python code to help generate a response to a prompt.
 *
 * @param container
 *   The code interpreter container. Can be a container ID or an object that specifies uploaded
 *   file IDs to make available to your code.
 */
case class CodeInterpreterTool(
  container: CodeInterpreterContainer
) extends Tool {
  val `type`: String = "code_interpreter"

  override def typeString: String = `type`
}

/**
 * Code interpreter container specification.
 */
sealed trait CodeInterpreterContainer

object CodeInterpreterContainer {

  /**
   * A specific container ID.
   *
   * @param id
   *   The container ID.
   */
  case class ContainerId(id: String) extends CodeInterpreterContainer

  /**
   * Auto configuration for a code interpreter container.
   *
   * @param fileIds
   *   Optional list of uploaded files to make available to your code.
   */
  case class Auto(
    fileIds: Seq[String] = Nil
  ) extends CodeInterpreterContainer {
    val `type`: String = "auto"
  }
}
