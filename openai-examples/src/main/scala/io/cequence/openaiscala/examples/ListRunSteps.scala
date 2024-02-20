package io.cequence.openaiscala.examples

object ListRunSteps extends Example {

  override protected def run =
    service
      .listRunSteps(
        "thread_JaumLGPPZmxu5rXOATI94IF5",
        "run_1"
      )
      .map(println)
}
