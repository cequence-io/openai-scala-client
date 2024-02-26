package io.cequence.openaiscala.examples

object ModifyAssistant extends Example {

  override protected def run =
    for {
      thread <- service.modifyAssistant(
        assistantId = "asst_Btbc2h7dqyDU52g1KfBa2XE8",
        metadata = Map("user_id" -> "986415", "due_date" -> "2028-08-01")
      )
    } yield {
      println(thread)
    }
}
