package io.cequence.openaiscala.domain.responsesapi.tools

import io.cequence.wsclient.domain.EnumValue

/**
 * A custom tool that processes input using a specified format.
 *
 * @param name
 *   The name of the custom tool, used to identify it in tool calls.
 * @param description
 *   Optional description of the custom tool, used to provide more context.
 * @param format
 *   Optional input format for the custom tool. Default is unconstrained text.
 */
case class CustomTool(
  name: String,
  description: Option[String] = None,
  format: Option[CustomToolFormat] = None
) extends Tool {
  override val `type`: String = "custom"
}

/**
 * Input format for a custom tool.
 */
sealed trait CustomToolFormat {
  def `type`: String
}

/**
 * Unconstrained free-form text format.
 */
case object TextFormat extends CustomToolFormat {
  override val `type`: String = "text"
}

/**
 * A grammar format defined by the user.
 *
 * @param definition
 *   The grammar definition.
 * @param syntax
 *   The syntax of the grammar definition. One of lark or regex.
 */
case class GrammarFormat(
  definition: String,
  syntax: GrammarSyntax
) extends CustomToolFormat {
  override val `type`: String = "grammar"
}

/**
 * Grammar syntax type.
 */
sealed trait GrammarSyntax extends EnumValue

object GrammarSyntax {
  case object lark extends GrammarSyntax
  case object regex extends GrammarSyntax
}
