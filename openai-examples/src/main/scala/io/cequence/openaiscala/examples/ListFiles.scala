package io.cequence.openaiscala.examples

object ListFiles extends Example {

  override protected def run =
    service.listFiles.map(_.foreach(println))
}
