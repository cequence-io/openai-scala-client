package io.cequence.openaiscala.examples

import scala.concurrent.Future
object ListModels extends Example {

  override protected def run: Future[Unit] =
    service.listModels.map(
      // newest first - hosts that send no creation date list theirs last
      _.sortBy(_.created.map(_.getTime)).reverse.foreach(model => println(model.id))
    )
}
