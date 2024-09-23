package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantToolResource.CodeInterpreterResources
import io.cequence.openaiscala.domain.ThreadAndRun.Message.{AssistantMessage, UserMessage}
import io.cequence.openaiscala.domain.{AssistantToolResource, FileId, ThreadAndRun}

import scala.concurrent.Future

object CreateThreadAndRun extends Example {

  override protected def run: Future[Unit] =
    for {
      thread <- service.createThreadAndRun(
        assistantId = "asst_GEKjNc6lewoiulFt32mWSqKl",
        thread = Some(
          ThreadAndRun(
            messages = Seq(
              UserMessage("Explain deep learning to a 5 year old."),
              AssistantMessage(
                "Deep learning is a type of machine learning that trains a computer to perform human-like tasks, such as recognizing speech, identifying images, or making decisions."
              ),
              UserMessage("Could you please provide even simpler explanation?")
            ),
            toolResources = AssistantToolResource.empty,
            metadata = Map.empty
          )
        ),
        stream = false
      )

//      Vector Store: CUSTOMER RELATIONSHIP AGREEMENT[vs_sRwpBFIFYyfWQ3og8X9CQs3A] (3 files)
//      - file-y5Q8IgmBvQ547z7vi9PDOzZQ (vector_store.file)
//        - file-9pb59EqrMCRpDxivmDQ6AxqW (vector_store.file)
//        - file-DQQrxLykRzcA54rqMyyfygyV (vector_store.file)

      threadWithCodeInterpreter <- service.createThreadAndRun(
        assistantId = "asst_GEKjNc6lewoiulFt32mWSqKl",
        thread = Some(
          ThreadAndRun(
            messages = Seq(
              UserMessage("Tell me about usage of FP in Cequence."),
              AssistantMessage(
                "Cequence does use functional programming."
              ),
              UserMessage("Could you please provide more comprehensive answer?")
            ),
            toolResources = AssistantToolResource(
              CodeInterpreterResources(fileIds =
                Seq(
                  FileId("file-y5Q8IgmBvQ547z7vi9PDOzZQ"),
                  FileId("file-9pb59EqrMCRpDxivmDQ6AxqW"),
                  FileId("file-DQQrxLykRzcA54rqMyyfygyV")
                )
              )
            ),
            metadata = Map.empty
          )
        ),
        stream = false
      )
    } yield {
      println(thread)
    }

}
