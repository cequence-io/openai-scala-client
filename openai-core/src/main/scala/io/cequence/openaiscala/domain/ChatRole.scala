package io.cequence.openaiscala.domain

sealed trait ChatRole

object ChatRole {
  case object User extends ChatRole
  case object System extends ChatRole
  case object Assistant extends ChatRole
  @Deprecated
  case object Function extends ChatRole
  case object Tool extends ChatRole
}
