package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

sealed abstract class EndPoint(value: String = "") extends NamedEnumValue(value)

object EndPoint {
  case object messages extends EndPoint
}

sealed trait Param extends EnumValue

object Param {

  case object model extends Param
  case object messages extends Param
  case object system extends Param
  case object max_tokens extends Param
  case object metadata extends Param
  case object stop_sequences extends Param
  case object stream extends Param
  case object temperature extends Param
  case object top_p extends Param
  case object top_k extends Param
  case object thinking extends Param
  // bedrock
  case object anthropic_version extends Param
}
