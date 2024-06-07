package io.cequence.openaiscala.examples

import scala.concurrent.Future
object CreateVectorStore extends Example {

  override protected def run: Future[Unit] =
    for {
      vectorStore <- service.createVectorStore(
        fileIds = Seq("file-xxx"),
        name = Some("Google 10-K Form")
      )
    } yield println(vectorStore)
}
