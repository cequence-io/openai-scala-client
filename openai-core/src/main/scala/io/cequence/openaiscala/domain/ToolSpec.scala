package io.cequence.openaiscala.domain

sealed trait MessageAttachmentTool

object MessageAttachmentTool {
  case object CodeInterpreterSpec extends MessageAttachmentTool

  case object FileSearchSpec extends MessageAttachmentTool
}
