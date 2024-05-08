package io.cequence.openaiscala.anthropic.domain


final case class ToolSpec(name: String, description: Option[String], inputSchema: Map[String, Any])
