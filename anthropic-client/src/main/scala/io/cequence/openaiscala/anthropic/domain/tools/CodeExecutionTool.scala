package io.cequence.openaiscala.anthropic.domain.tools

import io.cequence.wsclient.domain.EnumValue

/**
 * Code execution tool for running code. Name is always "code_execution".
 *
 * @param `type`
 *   Version of the code execution tool.
 */
case class CodeExecutionTool(
  override val `type`: CodeExecutionToolType = CodeExecutionToolType.code_execution_20250825
) extends Tool {
  override val name: String = "code_execution"
}

sealed trait CodeExecutionToolType extends EnumValue

object CodeExecutionToolType {
  case object code_execution_20250522 extends CodeExecutionToolType
  case object code_execution_20250825 extends CodeExecutionToolType

  def values: Seq[CodeExecutionToolType] =
    Seq(code_execution_20250522, code_execution_20250825)
}
