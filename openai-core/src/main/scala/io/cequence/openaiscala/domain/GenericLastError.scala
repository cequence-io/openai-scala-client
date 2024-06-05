package io.cequence.openaiscala.domain

import io.cequence.wsclient.domain.EnumValue

case class GenericLastError[T <: EnumValue](
  code: T,
  message: String
)
