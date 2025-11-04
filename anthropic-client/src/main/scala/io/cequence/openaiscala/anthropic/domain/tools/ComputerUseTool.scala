package io.cequence.openaiscala.anthropic.domain.tools

import io.cequence.wsclient.domain.EnumValue

/**
 * Computer use tool for controlling a computer. Name is always "computer".
 *
 * @param displayHeightPx
 *   The height of the display in pixels (>= 1).
 * @param displayWidthPx
 *   The width of the display in pixels (>= 1).
 * @param `type`
 *   Version of the computer use tool.
 * @param displayNumber
 *   The X11 display number (e.g. 0, 1) for the display (>= 0).
 */
case class ComputerUseTool(
  displayHeightPx: Int,
  displayWidthPx: Int,
  override val `type`: ComputerUseToolType,
  displayNumber: Option[Int] = None
) extends Tool {
  override val name: String = "computer"
}

sealed trait ComputerUseToolType extends EnumValue

object ComputerUseToolType {
  case object computer_20250124 extends ComputerUseToolType
  case object computer_20241022 extends ComputerUseToolType

  def values: Seq[ComputerUseToolType] = Seq(computer_20250124, computer_20241022)
}
