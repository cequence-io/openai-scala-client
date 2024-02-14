package io.cequence.openaiscala.examples
import io.cequence.openaiscala.domain.{AssistantTool, FileId, ModelId}

import scala.concurrent.Future

object CreateAssistant extends Example {
  override protected def run: Future[_] =
    for {
      assistant <- service.createAssistant(
        model = ModelId.gpt_3_5_turbo,
        name = Some("Math Tutor"),
        instructions = Some(
          "You are a personal math tutor. When asked a question, write and run Python code to answer the question."
        ),
        fileIds = Seq("file-id"),
        tools = Seq(AssistantTool.CodeInterpreterTool)
      )
    } yield println(assistant)
}
