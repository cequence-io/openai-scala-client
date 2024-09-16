package io.cequence.openaiscala.domain

sealed trait RunTool

object RunTool {
  case object CodeInterpreterTool extends RunTool
  case object FileSearchTool extends RunTool
  case class FunctionTool(name: String) extends RunTool
}
