package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.{
  AssistantId,
  ChatRole,
  FunctionSpec,
  ModelId,
  ThreadMessage,
  ThreadToCreate
}

import scala.concurrent.Future

object CreateThreadAndRun extends Example {

  override protected def run: Future[_] =
    for {
      run <- service.createThreadAndRun(
        assistantId = AssistantId("asst_demmWHLUIEv1cJEnk1JWstYw"),
        thread = ThreadToCreate(
          Seq(ThreadMessage(content = "Explain me fusion energy.", role = ChatRole.User)),
          Map("metadata" -> "metadata")
        ),
        model = Some(ModelId.gpt_3_5_turbo_1106),
        instructions = Some("How much is 2 + 2?"),
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
