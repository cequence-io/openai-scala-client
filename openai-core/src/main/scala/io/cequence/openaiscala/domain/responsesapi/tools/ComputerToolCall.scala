package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.openaiscala.domain.responsesapi.ModelStatus
import io.cequence.openaiscala.domain.responsesapi.Input
import io.cequence.openaiscala.domain.responsesapi.Output
import io.cequence.wsclient.domain.EnumValue

/**
 * A tool call to a computer use tool.
 *
 * @param action
 *   The action to perform
 * @param callId
 *   An identifier used when responding to the tool call with output
 * @param id
 *   The unique ID of the computer call
 * @param pendingSafetyChecks
 *   The pending safety checks for the computer call
 * @param status
 *   The status of the item
 */
final case class ComputerToolCall(
  action: ComputerToolAction,
  callId: String,
  id: String,
  pendingSafetyChecks: Seq[PendingSafetyCheck] = Nil,
  status: ModelStatus // in_progress, completed, or incomplete
) extends ToolCall
    with Input
    with Output {
  override val `type`: String = "computer_call"
}

/**
 * Represents the hierarchy of computer tool actions.
 */
sealed trait ComputerToolAction {
  val `type`: String
}

object ComputerToolAction {

  /**
   * A click action.
   *
   * @param button
   *   The mouse button pressed during the click.
   * @param x
   *   The x-coordinate where the click occurred.
   * @param y
   *   The y-coordinate where the click occurred.
   */
  final case class Click(
    button: ButtonClick,
    x: Int,
    y: Int
  ) extends ComputerToolAction {
    val `type`: String = "click"
  }

  /**
   * Represents the type of mouse button used in a click action.
   */
  sealed trait ButtonClick extends EnumValue {
    override def toString: String = super.toString.toLowerCase
  }

  object ButtonClick {
    case object Left extends ButtonClick
    case object Right extends ButtonClick
    case object Wheel extends ButtonClick
    case object Back extends ButtonClick
    case object Forward extends ButtonClick

    def values: Seq[ButtonClick] = Seq(Left, Right, Wheel, Back, Forward)
  }

  /**
   * A double click action.
   *
   * @param x
   *   The x-coordinate where the double click occurred.
   * @param y
   *   The y-coordinate where the double click occurred.
   */
  final case class DoubleClick(
    x: Int,
    y: Int
  ) extends ComputerToolAction {
    val `type`: String = "double_click"
  }

  /**
   * A drag action.
   *
   * @param path
   *   An array of coordinates representing the path of the drag action.
   */
  final case class Drag(
    path: Seq[Coordinate]
  ) extends ComputerToolAction {
    val `type`: String = "drag"
  }

  /**
   * Represents a coordinate point with x and y values.
   *
   * @param x
   *   The x-coordinate.
   * @param y
   *   The y-coordinate.
   */
  final case class Coordinate(
    x: Int,
    y: Int
  )

  /**
   * A collection of keypresses the model would like to perform.
   *
   * @param keys
   *   The combination of keys the model is requesting to be pressed.
   */
  final case class KeyPress(
    keys: Seq[String]
  ) extends ComputerToolAction {
    val `type`: String = "keypress"
  }

  /**
   * A mouse move action.
   *
   * @param x
   *   The x-coordinate to move to.
   * @param y
   *   The y-coordinate to move to.
   */
  final case class Move(
    x: Int,
    y: Int
  ) extends ComputerToolAction {
    val `type`: String = "move"
  }

  /**
   * A screenshot action.
   */
  object Screenshot extends ComputerToolAction {
    val `type`: String = "screenshot"
  }

  /**
   * A scroll action.
   *
   * @param scrollX
   *   The horizontal scroll distance.
   * @param scrollY
   *   The vertical scroll distance.
   * @param x
   *   The x-coordinate where the scroll occurred.
   * @param y
   *   The y-coordinate where the scroll occurred.
   */
  final case class Scroll(
    scrollX: Int,
    scrollY: Int,
    x: Int,
    y: Int
  ) extends ComputerToolAction {
    val `type`: String = "scroll"
  }

  /**
   * An action to type in text.
   *
   * @param text
   *   The text to type.
   */
  final case class Type(
    text: String
  ) extends ComputerToolAction {
    val `type`: String = "type"
  }

  /**
   * A wait action.
   */
  object Wait extends ComputerToolAction {
    val `type`: String = "wait"
  }
}

/**
 * Represents a pending safety check.
 *
 * @param code
 *   The type of the pending safety check.
 * @param id
 *   The ID of the pending safety check.
 * @param message
 *   Details about the pending safety check.
 */
final case class PendingSafetyCheck(
  code: String,
  id: String,
  message: String
)
