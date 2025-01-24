package io.cequence.openaiscala.perplexity.service.impl

import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

sealed abstract class EndPoint(value: String = "") extends NamedEnumValue(value)

object EndPoint {
  case object chatCompletion extends EndPoint("chat/completions")
}

sealed trait Param extends EnumValue

object Param {

  case object model extends Param
  case object messages extends Param
  case object frequency_penalty extends Param
  case object max_tokens extends Param
  case object presence_penalty extends Param
  case object response_format extends Param
  case object return_images extends Param
  case object return_related_questions extends Param
  case object search_domain_filter extends Param
  case object search_recency_filter extends Param
  case object stream extends Param
  case object temperature extends Param
  case object top_k extends Param
  case object top_p extends Param
}
