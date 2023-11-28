package io.cequence.openaiscala.examples

object ListModels extends Example {

  override protected def run =
    service.listModels.map(
      _.sortBy(_.created).reverse.foreach(fileInfo => println(fileInfo.id))
    )
}
