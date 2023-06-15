package io.cequence.openaiscala.domain

sealed trait ChatRole

object ChatRole {
  case object User extends ChatRole
  case object System extends ChatRole
  case object Assistant extends ChatRole

  case object Function extends ChatRole
}
