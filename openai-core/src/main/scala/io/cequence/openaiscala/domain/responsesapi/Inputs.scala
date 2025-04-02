package io.cequence.openaiscala.domain.responsesapi

sealed trait Inputs

object Inputs {

  case class Text(text: String) extends Inputs

  case class Items(items: Input*) extends Inputs
}