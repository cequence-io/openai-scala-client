package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.{FunctionSpec, ModelId}

import scala.concurrent.Future

object CreateRun extends Example {

  override protected def run: Future[_] =
    for {
      run <- service.createRun(
        threadId = "thread_JaumLGPPZmxu5rXOATI94IF5",
        assistantId = "asst_demmWHLUIEv1cJEnk1JWstYw",
        model = Some(ModelId.gpt_3_5_turbo_1106),
        instructions = Some("How much is 2 + 2?"),
        additionalInstructions = None,
        tools = Seq(
          FunctionSpec(
            name = "function-name",
            description = Some("calculates something"),
            Map.empty
          )
        ),
        metadata = Map("metadata" -> "metadata")
      )
    } yield println(run)
}
