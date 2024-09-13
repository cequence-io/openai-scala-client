package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.FileId

import scala.concurrent.Future

object RetrieveVectorStoreFile extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveVectorStoreFile(
        vectorStoreId = "vs_xxx",
        fileId = FileId("vsf_xxx")
      )
    } yield {
      println(assistant)
    }

}
