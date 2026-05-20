package io.cequence.openaiscala.gemini.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait ThinkingLevel extends EnumValue

object ThinkingLevel {
  case object THINKING_LEVEL_UNSPECIFIED extends ThinkingLevel
  case object MINIMAL
      extends ThinkingLevel // Gemini 3 Flash / Flash-Lite / Flash-Image only (not Pro)
  case object LOW extends ThinkingLevel
  case object MEDIUM extends ThinkingLevel
  case object HIGH extends ThinkingLevel

  def values: Seq[ThinkingLevel] = Seq(
    THINKING_LEVEL_UNSPECIFIED,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH
  )
}
