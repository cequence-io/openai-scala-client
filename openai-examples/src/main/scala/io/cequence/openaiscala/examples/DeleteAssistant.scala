package io.cequence.openaiscala.examples

object DeleteAssistant extends Example {

  override protected def run =
    for {
      thread <- service.deleteAssistant(
        "asst_Btbc2h7dqyDU52g1KfBa2XE9"
      )
    } yield {
      println(thread)
    }
}
