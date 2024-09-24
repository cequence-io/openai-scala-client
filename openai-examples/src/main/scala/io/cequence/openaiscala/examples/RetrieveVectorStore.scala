package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveVectorStore extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveVectorStore(
        vectorStoreId = "vs_9pl9kTn3ggjzDKYX5AT9JuIG"
      )
    } yield {
      println(assistant)
    }

}
