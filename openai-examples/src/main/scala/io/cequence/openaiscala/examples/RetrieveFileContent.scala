package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveFileContent extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveFileContent("file-A9V1zO4XpjjqBke8Kdp78vMU")
    } yield {
      println(assistant)
    }

}
