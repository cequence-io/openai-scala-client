package io.cequence.openaiscala.examples

import scala.concurrent.Future

object RetrieveBatchResponses extends Example {

  override protected def run: Future[_] =
    for {
      maybeBatchResponses <- service.retrieveBatchResponses("batch_xyz")
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
