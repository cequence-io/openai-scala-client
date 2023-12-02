package io.cequence.openaiscala.examples

object DeleteThread extends Example {

  override protected def run =
    for {
      thread <- service.deleteThread(
        "thread_r0uAuizlDYmx9de3pFVwuFsZ"
      )
    } yield {
      println(thread)
    }
}
