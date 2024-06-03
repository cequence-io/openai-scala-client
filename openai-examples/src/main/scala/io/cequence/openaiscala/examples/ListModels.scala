package io.cequence.openaiscala.examples

import scala.concurrent.Future
object ListModels extends Example {

  override protected def run: Future[Unit] =
    service.listModels.map(
      _.sortBy(_.created).reverse.foreach(fileInfo => println(fileInfo.id))
    )
}
