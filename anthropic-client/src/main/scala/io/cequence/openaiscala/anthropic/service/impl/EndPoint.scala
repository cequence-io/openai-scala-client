package io.cequence.openaiscala.anthropic.service.impl

import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

sealed abstract class EndPoint(value: String = "") extends NamedEnumValue(value)

object EndPoint {
  case object messages extends EndPoint
  case object skills extends EndPoint
  case object files extends EndPoint
}

sealed abstract class Param(value: String = "") extends NamedEnumValue(value)

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
  case object container extends Param
  case object tools extends Param
  case object tool_choice extends Param
  case object mcp_servers extends Param
  case object output_format extends Param
  // bedrock
  case object anthropic_version extends Param
  // skills
  case object page extends Param
  case object limit extends Param
  case object source extends Param
  case object display_title extends Param
  case object files extends Param("files[]")
  // files
  case object file extends Param
  // files pagination
  case object before_id extends Param
  case object after_id extends Param
}
