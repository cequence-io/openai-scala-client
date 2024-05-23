package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveBatchResponses extends Example {

  override protected def run: Future[_] =
    for {
//      maybeBatchResponses <- service.retrieveBatchResponses("file-A9V1zO4XpjjqBke8Kdp78vMU")
      maybeBatchResponses <- service.retrieveBatchResponses("batch_Ghy5a9EEXDLFqBcJqANpr17F")
    } yield {
      maybeBatchResponses match {
        case Some(batchResponses) =>
          batchResponses.responses.foreach { response =>
            println(response)
          }
        case None =>
          println("No batch responses found.")
      }
    }

}
