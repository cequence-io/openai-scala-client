package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.ToolOutput

object SubmitToolOutputsToRun extends Example {

  override protected def run =
    for {
      thread <- service.submitToolOutputsToRun(
        "thread_c6fFMmUw30l30SzG2KdUViMn",
        "run_1",
        toolOutputs = Seq(
          ToolOutput(toolCallId = Some("call_abc123"), output = Some("28C")),
          ToolOutput(toolCallId = Some("call_abc124"), output = Some("29C"))
        )
      )
    } yield {
      println(thread)
    }
}
