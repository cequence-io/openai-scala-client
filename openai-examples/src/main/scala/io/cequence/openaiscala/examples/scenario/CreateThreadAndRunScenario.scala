package io.cequence.openaiscala.examples.scenario

import io.cequence.openaiscala.domain.AssistantTool.FileSearchTool
import io.cequence.openaiscala.domain.settings.FileUploadPurpose
import io.cequence.openaiscala.domain.{AssistantId, AssistantToolResource, ModelId, Run, RunStatus}
import io.cequence.openaiscala.examples.{Example, PollingHelper}

import java.io.File
import scala.concurrent.Future

object CreateThreadAndRunScenario extends Example with PollingHelper {

  private def scheduleFile(): File = {
    val resource = getClass.getResource("/CRA.txt")
    if (resource == null) {
      throw new RuntimeException("Failed to load CRA.txt from resources")
    }
    new File(resource.getFile)
  }

  override protected def run: Future[_] = {
    for {
      fileInfo <- service.uploadFile(
        scheduleFile(),
        purpose = FileUploadPurpose.assistants
      )

      vectorStore <- service.createVectorStore(
        fileIds = Seq(fileInfo.id),
        name = Some("CUSTOMER RELATIONSHIP AGREEMENT")
      )

      assistant <- service.createAssistant(
        model = ModelId.gpt_4o_2024_05_13,
        name = Some("Customer Relationship Assistant"),
        description = Some(
          "You are a trustworthy and reliable assistant that helps businesses with their customer relationship agreements."
        ),
        instructions = Some(
          "Please assist with customer relationship agreements based on the provided document."
        ),
        tools = Seq(FileSearchTool()),
        toolResources = Some(
          AssistantToolResource(
            AssistantToolResource.FileSearchResources(
              vectorStoreIds = Seq(vectorStore.id)
            )
          )
        )
      )

      run <- service.createThreadAndRun(
        assistantId = AssistantId(assistant.id),
        thread = None,
        stream = false
      )

      runNew <- pollUntilDone((run: Run) => RunStatus.finishedStates.contains(run.status)) {
        service
          .retrieveRun(run.thread_id, run.id)
          .map(
            _.getOrElse(
              throw new IllegalStateException(s"Run with id ${run.id} not found.")
            )
          )
      }

      _ = println(s"Run status: ${runNew.status}")

      // get the messages
      threadMessages <- service.listThreadMessages(run.thread_id)

    } yield {
      println(s"File created: ${fileInfo.id}")
      println(s"Vector store created: ${vectorStore.id}")
      println(s"Assistant created: ${assistant.id}")
      println(s"Thread created: ${run.thread_id}")
      println(s"Run created: ${run.id}")
      threadMessages.foreach(println)
    }
  }

}
