package io.cequence.openaiscala.perplexity.service.impl

import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

sealed abstract class EndPoint(value: String = "") extends NamedEnumValue(value)

object EndPoint {
  case object chatCompletion extends EndPoint("chat/completions")
  case object agent extends EndPoint("v1/agent")
  case object models extends EndPoint("v1/models")
  // Perplexity's OpenAI-compatible alias of v1/agent
  case object responses extends EndPoint("v1/responses")
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

  // any body field by its JSON name (the Agent API body is built as JSON in AgentJsonFormats)
  final case class Raw(name: String) extends Param {
    override def toString: String = name
  }
}
