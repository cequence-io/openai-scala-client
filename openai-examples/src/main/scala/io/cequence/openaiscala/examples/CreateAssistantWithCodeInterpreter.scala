package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantTool.CodeInterpreterTool
import io.cequence.openaiscala.domain.ModelId

import scala.concurrent.Future

object CreateAssistantWithCodeInterpreter extends Example {

  override protected def run: Future[Unit] =
    for {
      assistant <- service.createAssistant(
        model = ModelId.gpt_4o_2024_05_13,
        name = Some("Data Interpreter Assistant"),
        description = Some(
          "Helpful assistant that helps users interpret and visualize data and provide handy statistics on demand."
        ),
        instructions = Some(
          "You are a helpful assistant that helps users interpret and visualize data and provide handy statistics on demand."
        ),
        tools = Seq(CodeInterpreterTool)
      )
    } yield println(assistant)
}
