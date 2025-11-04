package io.cequence.openaiscala.anthropic.domain.tools

import io.cequence.wsclient.domain.EnumValue

/**
 * Bash tool for executing bash commands. Name is always "bash".
 *
 * @param `type`
 *   Version of the bash tool.
 */
case class BashTool(
  override val `type`: BashToolType = BashToolType.bash_20250124
) extends Tool {
  override val name: String = "bash"
}

sealed trait BashToolType extends EnumValue

object BashToolType {
  case object bash_20250124 extends BashToolType
  case object bash_20241022 extends BashToolType

  def values: Seq[BashToolType] = Seq(bash_20250124, bash_20241022)
}
