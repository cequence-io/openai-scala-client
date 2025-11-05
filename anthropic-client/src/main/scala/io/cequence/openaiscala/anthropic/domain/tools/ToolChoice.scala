package io.cequence.openaiscala.anthropic.domain.tools

import io.cequence.openaiscala.domain.HasType

/**
 * How the model should use the provided tools. The model can use a specific tool, any
 * available tool, or decide by itself whether to use tools.
 */
sealed trait ToolChoice extends HasType

object ToolChoice {

  /**
   * The model will automatically decide whether to use tools.
   *
   * @param disableParallelToolUse
   *   Whether to disable parallel tool use. Defaults to false. If set to true, the model will
   *   output at most one tool use.
   */
  case class Auto(
    disableParallelToolUse: Option[Boolean] = scala.None
  ) extends ToolChoice {
    override val `type`: String = "auto"
  }

  /**
   * The model must use one of the provided tools.
   *
   * @param disableParallelToolUse
   *   Whether to disable parallel tool use. Defaults to false. If set to true, the model will
   *   output exactly one tool use.
   */
  case class Any(
    disableParallelToolUse: Option[Boolean] = scala.None
  ) extends ToolChoice {
    override val `type`: String = "any"
  }

  /**
   * The model must use the specified tool.
   *
   * @param name
   *   The name of the tool to use.
   * @param disableParallelToolUse
   *   Whether to disable parallel tool use. Defaults to false. If set to true, the model will
   *   output exactly one tool use.
   */
  case class Tool(
    name: String,
    disableParallelToolUse: Option[Boolean] = scala.None
  ) extends ToolChoice {
    override val `type`: String = "tool"
  }

  /**
   * The model must not use any tools.
   */
  case object None extends ToolChoice {
    override val `type`: String = "none"
  }
}
