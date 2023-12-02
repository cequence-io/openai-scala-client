package io.cequence.openaiscala.examples

object RetrieveThread extends Example {

  override protected def run =
    for {
      thread <- service.retrieveThread(
        "thread_r0uAuizlDYmx9de3pFVwuFsZ"
      )
    } yield {
      println(thread)
    }
}
