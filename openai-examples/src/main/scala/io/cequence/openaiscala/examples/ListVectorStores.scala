package io.cequence.openaiscala.examples

object ListVectorStores extends Example {

  override protected def run =
    for {
      vectorStores <- service.listVectorStores()
    } yield {
      vectorStores.foreach(println)
    }
}

