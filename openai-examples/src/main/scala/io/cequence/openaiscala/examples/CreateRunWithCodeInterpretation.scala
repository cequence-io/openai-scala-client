package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantTool.CodeInterpreterTool
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.domain.settings.CreateRunSettings
import io.cequence.wsclient.service.PollingHelper

import scala.concurrent.Future

object CreateRunWithCodeInterpretation extends Example with PollingHelper {

  private val data = """Number of Items,Max Number of Tokens,Execution Time per Item (secs)
                       |500,512,0.1308133502
                       |200,512,0.1634943628
                       |100,512,0.3063321185
                       |50,512,0.4779050493
                       |500,256,0.056150517
                       |200,256,0.0720494139
                       |100,256,0.1357714772
                       |50,256,0.2181458521
                       |500,128,0.0264534912
                       |200,128,0.0353085577
                       |100,128,0.0669811797
                       |50,128,0.1067434263
                       |500,64,0.0134469638
                       |200,64,0.0172120214
                       |100,64,0.0360785079
                       |50,64,0.0527386284
                       |""".stripMargin

  private val assistantId = "asst_xxx"

  override protected def run: Future[_] =
    for {
      thread <- service.createThread(
        messages = Seq(ThreadMessage(s"Plot the following data:\n$data"))
      )

      run <- service.createRun(
        threadId = thread.id,
        assistantId = assistantId,
        instructions = None,
        responseToolChoice = Some(ToolChoice.EnforcedTool(RunTool.CodeInterpreterTool)),
        tools = Seq(CodeInterpreterTool),
        settings = CreateRunSettings(
          model = Some(ModelId.gpt_4o),
          temperature = Some(0.0),
          topP = Some(1.0),
          maxPromptTokens = Some(4000),
          maxCompletionTokens = Some(2048)
        ),
        stream = false
      )

      // poll until done
      runNew <- pollUntilDone((run: Run) => run.isFinished) {
        service
          .retrieveRun(thread.id, run.id)
          .map(
            _.getOrElse(throw new IllegalStateException(s"Run with id ${run.id} not found."))
          )
      }

      _ = println(s"Run status: ${runNew.status}")

      // get the messages
      threadMessages <- service.listThreadMessages(thread.id)

      // cleanup - delete run and thread
    } yield {
      println("Run id   : " + run.id)
      println("Thread id: " + run.thread_id)

      // take the last message
      val lastMessage = threadMessages
        .sortBy(_.created_at)
        .lastOption
        .getOrElse(
          throw new IllegalStateException("No messages found in the thread")
        )

      val fileIds = lastMessage.content.flatMap(_.image_file.map(_.file_id))
      val texts = lastMessage.content.flatMap(_.text.map(_.value))

      println("File ids: " + fileIds.mkString(", "))
      println("Texts   : " + texts.mkString("\n"))
    }
}
