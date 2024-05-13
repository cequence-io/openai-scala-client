package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveBatchFileContent extends Example {

  override protected def run: Future[_] =
    for {
      maybeAssistant <- service.retrieveBatchFileContent("batch_Ghy5a9EEXDLFqBcJqANpr17F")
    } yield {
      maybeAssistant.foreach { assistant =>
        println(assistant)
      }
    }

}
