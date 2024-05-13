package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveFile extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveFile("file-A9V1zO4XpjjqBke8Kdp78vMU")
    } yield {
      println(assistant)
    }

}
