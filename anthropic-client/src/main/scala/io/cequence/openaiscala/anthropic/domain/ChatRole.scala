package io.cequence.openaiscala.anthropic.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait ChatRole extends EnumValue {
  override def toString: String = super.toString.toLowerCase
}

object ChatRole {
  case object System extends ChatRole
  case object User extends ChatRole
  case object Assistant extends ChatRole

  def allValues: Seq[ChatRole] = Seq(System, User, Assistant)
}
