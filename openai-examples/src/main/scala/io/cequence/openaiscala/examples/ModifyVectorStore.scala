package io.cequence.openaiscala.examples

import scala.concurrent.Future

object ModifyVectorStore extends Example {

  override protected def run: Future[Unit] =
    for {
      vectorStore <- service.modifyVectorStore(
        vectorStoreId = "vs_xxx",
        name = Some("Google 10-K Form")
      )
    } yield println(vectorStore)
}
