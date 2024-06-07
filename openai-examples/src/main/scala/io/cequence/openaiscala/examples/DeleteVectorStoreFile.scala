package io.cequence.openaiscala.examples

import scala.concurrent.Future

object DeleteVectorStoreFile extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.deleteVectorStoreFile(
        vectorStoreId = "vs_rOW4vaWq0tsvES1IiWXYVMJg",
        fileId = "file-lCizfGBEVvKD6WDWvDyArgzg"
      )
    } yield {
      println(thread)
    }
}
