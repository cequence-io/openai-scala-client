package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveBatchFile extends Example {

  override protected def run: Future[_] =
    for {
      assistant <- service.retrieveBatchFile("batch_Ghy5a9EEXDLFqBcJqANpr17F")
    } yield {
      println(assistant)
    }

}
