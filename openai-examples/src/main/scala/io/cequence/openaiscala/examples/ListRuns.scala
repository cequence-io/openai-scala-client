package io.cequence.openaiscala.examples

object ListRuns extends Example {

  override protected def run =
    service
      .listRuns(
        threadId = "thread_JaumLGPPZmxu5rXOATI94IF5"
      )
      .map(println)
}
