package io.cequence.openaiscala.examples

object CreateVectorStore extends Example {

  override protected def run =
    for {
      vectorStore <- service.createVectorStore(
        fileIds = Seq("file-xxx"),
        name = Some("Google 10-K Form")
      )
    } yield println(vectorStore)
}
