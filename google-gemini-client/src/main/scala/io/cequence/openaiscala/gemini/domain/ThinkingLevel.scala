package io.cequence.openaiscala.gemini.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait ThinkingLevel extends EnumValue

object ThinkingLevel {
  case object THINKING_LEVEL_UNSPECIFIED extends ThinkingLevel
  case object LOW extends ThinkingLevel
  case object HIGH extends ThinkingLevel

  def values: Seq[ThinkingLevel] = Seq(
    THINKING_LEVEL_UNSPECIFIED,
    LOW,
    HIGH
  )
}
