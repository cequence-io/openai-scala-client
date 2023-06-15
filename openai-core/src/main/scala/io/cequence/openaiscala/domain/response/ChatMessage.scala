package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.{ChatRole, FunctionCallSpec}

@Deprecated // to be replaced by MessageSpec in the next release
case class ChatMessage(
  role: ChatRole,
  content: Option[String],
  name: Option[String] = None,
  function_call: Option[FunctionCallSpec] = None
)
