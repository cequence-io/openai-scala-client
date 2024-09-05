package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.ThreadAndRun.Message.{AssistantMessage, UserMessage}
import io.cequence.openaiscala.domain.{AssistantId, ThreadAndRun}

import scala.concurrent.Future

object CreateThreadAndRun extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.createThreadAndRun(
        assistantId = AssistantId("assistant-abc123"),
        thread = Some(ThreadAndRun(
          messages = Seq(
            UserMessage("Explain deep learning to a 5 year old."),
            AssistantMessage("Deep learning is a type of machine learning that trains a computer to perform human-like tasks, such as recognizing speech, identifying images, or making decisions."),
            UserMessage("Could you please provide even simpler explanation?")
          ),
          toolResources = Seq.empty,
          metadata = Map.empty
        )),
        stream = false
      )
    } yield {
      println(thread)
    }

}
