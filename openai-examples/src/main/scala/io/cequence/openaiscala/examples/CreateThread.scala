package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.{AssistantToolResource, FileId, ThreadMessage}

import scala.concurrent.Future

object CreateThread extends Example {

  val codeInterpreter: AssistantToolResource = AssistantToolResource(
    AssistantToolResource.CodeInterpreterResources(Seq(FileId("file1.txt")))
  )
  val fileSearch: AssistantToolResource = AssistantToolResource(
    AssistantToolResource.FileSearchResources(vectorStoreIds = Seq("vector_store_1"))
  )

  override protected def run: Future[Unit] =
    for {
      thread <- service.createThread(
        messages = Seq(
          ThreadMessage("Hello, what is AI?"), // file_ids = Seq("file-abc123")
          ThreadMessage("How does AI work? Explain it in simple terms.")
        ),
        toolResources = Seq(codeInterpreter, fileSearch),
        metadata = Map("user_id" -> "986413")
      )
    } yield {
      println(thread)
    }
}
