package io.cequence.openaiscala.gemini.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait Modality extends EnumValue

object Modality {
  case object MODALITY_UNSPECIFIED extends Modality
  case object TEXT extends Modality
  case object IMAGE extends Modality
  case object VIDEO extends Modality
  case object AUDIO extends Modality
  case object DOCUMENT extends Modality

  def values: Seq[Modality] = Seq(
    MODALITY_UNSPECIFIED,
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT
  )
}
