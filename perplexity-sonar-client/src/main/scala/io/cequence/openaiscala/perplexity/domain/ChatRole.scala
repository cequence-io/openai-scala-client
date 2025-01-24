package io.cequence.openaiscala.perplexity.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait ChatRole extends EnumValue {
  override def toString: String = super.toString.toLowerCase
}

object ChatRole {
  case object System extends ChatRole
  case object User extends ChatRole
  case object Assistant extends ChatRole

  def values: Seq[ChatRole] = Seq(System, User, Assistant)
}
