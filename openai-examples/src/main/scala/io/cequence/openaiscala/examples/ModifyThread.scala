package io.cequence.openaiscala.examples

object ModifyThread extends Example {

  override protected def run =
    for {
      thread <- service.modifyThread(
        "thread_c6fFMmUw30l30SzG2KdUViMn",
        metadata = Map("user_id" -> "986415", "due_date" -> "2028-08-01")
      )
    } yield {
      println(thread)
    }
}
