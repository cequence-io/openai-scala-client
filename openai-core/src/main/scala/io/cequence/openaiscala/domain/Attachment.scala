package io.cequence.openaiscala.domain

import scala.collection.immutable.Seq

final case class Attachment(
  // The ID of the file to attach to the message.
  fileId: Option[FileId],
  // The tools to add this file to.
  tools: Seq[MessageTool] = Nil
)

object Attachment {
  def unapply(attachment: Attachment): Option[(Option[FileId], Seq[MessageTool])] =
    Some((attachment.fileId, attachment.tools))
}
