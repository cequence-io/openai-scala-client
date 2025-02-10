package io.cequence.openaiscala.gemini.domain

import io.cequence.wsclient.domain.EnumValue

sealed trait ChatRole extends EnumValue {
  override def toString: String = super.toString.toLowerCase
}

object ChatRole {
  case object User extends ChatRole
  case object Model extends ChatRole

  def values: Seq[ChatRole] = Seq(User, Model)
}
