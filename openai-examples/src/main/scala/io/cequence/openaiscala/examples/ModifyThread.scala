package io.cequence.openaiscala.examples

object ModifyThread extends Example {

  override protected def run =
    for {
      thread <- service.modifyThread(
        "thread_r0uAuizlDYmx9de3pFVwuFsZ",
        metadata = Map("user_id" -> "986415", "due_date" -> "2028-08-01")
      )
    } yield {
      println(thread)
    }
}
