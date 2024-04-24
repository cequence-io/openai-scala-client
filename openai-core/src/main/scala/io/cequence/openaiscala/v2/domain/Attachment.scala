package io.cequence.openaiscala.v2.domain

final case class Attachment(
  // The ID of the file to attach to the message.
  fileId: Option[FileId],
  // The tools to add this file to.
  tools: Seq[MessageTool] = Nil
)
