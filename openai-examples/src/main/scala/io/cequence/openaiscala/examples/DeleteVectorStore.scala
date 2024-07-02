package io.cequence.openaiscala.examples

import scala.concurrent.Future
object DeleteVectorStore extends Example {

  override protected def run: Future[Unit] =
    for {
      vectorStore <- service.deleteVectorStore("vs_xxx")
    } yield println(vectorStore)
}
