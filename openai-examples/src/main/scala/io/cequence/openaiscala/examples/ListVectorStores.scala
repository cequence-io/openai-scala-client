package io.cequence.openaiscala.examples

import scala.concurrent.Future
object ListVectorStores extends Example {

  override protected def run: Future[Unit] =
    for {
      vectorStores <- service.listVectorStores()
    } yield {
      vectorStores.foreach(println)
    }
}
