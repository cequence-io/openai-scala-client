package io.cequence.openaiscala.examples

object DeleteAssistant extends Example {

  override protected def run =
    for {
      thread <- service.deleteAssistant(
        "asst_xxx"
      )
    } yield {
      println(thread)
    }
}
