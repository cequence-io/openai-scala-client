package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.ThreadMessage
import scala.concurrent.Future

object CreateThread extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.createThread(
        messages = Seq(
          ThreadMessage("Hello, what is AI?"), // file_ids = Seq("file-abc123")
          ThreadMessage("How does AI work? Explain it in simple terms.")
        ),
        metadata = Map("user_id" -> "986413")
      )
    } yield {
      println(thread)
    }
}
