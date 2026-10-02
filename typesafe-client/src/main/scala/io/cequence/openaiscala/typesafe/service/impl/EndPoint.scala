package io.cequence.openaiscala.typesafe.service.impl

import io.cequence.wsclient.domain.{EnumValue, NamedEnumValue}

sealed abstract class EndPoint(value: String = "") extends NamedEnumValue(value)

object EndPoint {
  case object systemOne extends EndPoint("v1/systemone")
  // Perplexity's Decisions API - the same questions and answers under another path
  case object decisions extends EndPoint("v1/decisions")
  case object models extends EndPoint("v1/models")
  // a host's own path (DecisionProvider.decisionsPath)
  final case class custom(path: String) extends EndPoint(path)
}

sealed trait Param extends EnumValue

object Param {
  case object state extends Param
  case object model extends Param
  case object questions extends Param
  // a query parameter of a host's model listing (DecisionModelListing.OpenAIStyle)
  final case class query(name: String) extends NamedEnumValue(name) with Param
}
