package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveFile extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveFile("file-2bZn9Vu6WicoTMOAEGW92pml")
    } yield {
      println(assistant)
    }

}
