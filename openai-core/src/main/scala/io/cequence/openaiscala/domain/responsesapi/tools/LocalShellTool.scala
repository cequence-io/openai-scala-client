package io.cequence.openaiscala.domain.responsesapi.tools

/**
 * A tool that allows the model to execute shell commands in a local environment.
 */
case object LocalShellTool extends Tool {
  val `type`: String = "local_shell"

  override def typeString: String = `type`
}
