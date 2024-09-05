package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.{Attachment, FileId, MessageAttachmentTool}

import scala.util.Random
import scala.concurrent.Future

object CreateThreadMessage extends Example {

  override protected def run: Future[Unit] =
    for {
      message <- service.createThreadMessage(
        threadId = "thread_c6fFMmUw30l30SzG2KdUViMn",
        content = "Hello, what is AI really?",
        attachments = Seq(
          Attachment(
            Some(FileId("file-1")),
            tools = Seq(MessageAttachmentTool.CodeInterpreterSpec)
          ),
          Attachment(Some(FileId("file-2")), tools = Seq(MessageAttachmentTool.FileSearchSpec))
        ),
        metadata = Map("user_id" -> Random.nextInt().toString)
      )
    } yield {
      println(message)
    }
}
