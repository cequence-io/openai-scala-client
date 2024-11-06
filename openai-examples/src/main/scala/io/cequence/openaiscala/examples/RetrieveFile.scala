package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveFile extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveFile("file-xyz")
    } yield {
      println(assistant)
    }

}
