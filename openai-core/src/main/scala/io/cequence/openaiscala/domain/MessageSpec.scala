package io.cequence.openaiscala.domain

case class MessageSpec(role: ChatRole, content: String)

sealed trait ChatRole

object ChatRole {
  case object User extends ChatRole
  case object System extends ChatRole
  case object Assistant extends ChatRole
}
