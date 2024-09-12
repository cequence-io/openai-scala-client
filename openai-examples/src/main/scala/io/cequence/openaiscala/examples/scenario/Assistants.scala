package io.cequence.openaiscala.examples.scenario

import io.cequence.openaiscala.domain.AssistantTool.FileSearchTool
import io.cequence.openaiscala.domain.settings.{FileUploadPurpose, UploadFileSettings}
import io.cequence.openaiscala.domain.{AssistantId, AssistantToolResource, ThreadMessage}
import io.cequence.openaiscala.examples.CreateVectorStore.service
import io.cequence.openaiscala.examples.Example

import java.io.File
import java.nio.file.Paths
import scala.concurrent.Future

object Assistants extends Example {

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
        model = "gpt-4o-2024-05-13",
        name = Some("Customer Relationship Assistant"),
        description = Some(
          "You are a trustworthy and reliable assistant that helps businesses with their customer relationship agreements."
        ),
        instructions = None,
        tools = Seq(FileSearchTool()),
        toolResources = Some(
          AssistantToolResource(
            AssistantToolResource.FileSearchResources(
              vectorStoreIds = Seq(vectorStore.id)
            )
          )
        )
      )

      thread <- service.createThread(
        messages = Seq(
          ThreadMessage(
            "I need help with my customer relationship agreement. List me any warnings I should be aware of."
          )
        )
      )

      run <- service.createRun(
        thread.id,
        AssistantId(assistant.id),
        stream = false
      )

      messages <- service.listThreadMessages(thread.id)

    } yield {
      println(s"File created: ${fileInfo.id}")
      println(s"Vector store created: ${vectorStore.id}")
      println(s"Assistant created: ${assistant.id}")
      println(s"Thread created: ${thread.id}")
      println(s"Run created: ${run.id}")
      messages.foreach(println)
    }
  }

}
