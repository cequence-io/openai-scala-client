package io.cequence.openaiscala.anthropic.domain.tools

import io.cequence.openaiscala.anthropic.domain.CacheControl
import io.cequence.wsclient.domain.EnumValue

/**
 * Text editor tool for editing text files.
 *
 * Name varies by version:
 *   - text_editor_20241022, text_editor_20250124 → "str_replace_editor"
 *   - text_editor_20250429, text_editor_20250728 → "str_replace_based_edit_tool"
 *
 * @param `type`
 *   Version of the text editor tool.
 * @param cacheControl
 *   Optional cache control for this tool.
 */
case class TextEditorTool(
  override val `type`: TextEditorToolType = TextEditorToolType.text_editor_20250728,
  cacheControl: Option[CacheControl] = None
) extends Tool {
  override val name: String = `type` match {
    case TextEditorToolType.text_editor_20241022 | TextEditorToolType.text_editor_20250124 =>
      "str_replace_editor"
    case TextEditorToolType.text_editor_20250429 | TextEditorToolType.text_editor_20250728 =>
      "str_replace_based_edit_tool"
  }
}

sealed trait TextEditorToolType extends EnumValue

object TextEditorToolType {
  case object text_editor_20250728 extends TextEditorToolType
  case object text_editor_20250429 extends TextEditorToolType
  case object text_editor_20250124 extends TextEditorToolType
  case object text_editor_20241022 extends TextEditorToolType

  def values: Seq[TextEditorToolType] = Seq(
    text_editor_20250728,
    text_editor_20250429,
    text_editor_20250124,
    text_editor_20241022
  )
}
