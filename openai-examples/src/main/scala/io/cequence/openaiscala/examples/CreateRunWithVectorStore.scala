package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantToolResource.FileSearchResources
import io.cequence.openaiscala.domain.response.FileInfo
import io.cequence.openaiscala.domain.settings.CreateRunSettings
import io.cequence.openaiscala.domain._
import io.cequence.openaiscala.service.adapter.OpenAIServiceAdapters
import io.cequence.openaiscala.service.{OpenAIService, OpenAIServiceFactory}

import java.io.File
import java.nio.file.Paths
import scala.concurrent.Future

object CreateRunWithVectorStore extends Example {

  private val adapters = OpenAIServiceAdapters.forFullService
  override protected val service: OpenAIService =
    adapters.log(
      OpenAIServiceFactory(),
      "openAIService1",
      println(_) // simple logging
    )

  val userId = "123"
  val model = ModelId.gpt_3_5_turbo

  private def scheduleFile(): File =
    Paths.get("/Users/boris/proj/cequence/eBF programme 2024 - extracted.pdf").toFile

  private def uploadFile: Future[FileInfo] =
    service.uploadFile(scheduleFile())

  private def createVectorStore(file: FileInfo) = {
    service.createVectorStore(fileIds = Seq(file.id), name = Some("Conference Schedule"))
  }

  private def createPlanner(vectorStoreId: String) =
    for {
      assistant <- service.createAssistant(
        model = model,
        name = Some("Schedule planner"),
        instructions = Some(
          "You pick the talks meeting my criteria I should attend at a conference."
        ),
        tools = Seq(FileSearchSpec),
        toolResources = Seq(FileSearchResources(Seq(vectorStoreId)))
      )
    } yield assistant

  def createSpecMessagesThread(vectorStoreId: String): Future[Thread] =
    for {
      thread <- service.createThread(
        messages = Seq(
          ThreadMessage(
            "I want to digitise my procurement processes and optimize them, ideally by employing AI."
          )
        ),
        metadata = Map("user_id" -> userId),
        toolResources = Seq(FileSearchResources(Seq(vectorStoreId)))
      )
      _ = println(thread)
    } yield thread

  val vectorStoreId = "vs_6nTuNJKVytSoFke9nvnpptUZ" // createVectorStore(fileInfo).map(_.id)
  val assistantId = AssistantId("asst_gIharZ60V7hvf5pQvvjkw7Mf")
  override protected def run: Future[_] =
    for {
//      fileInfo <- uploadFile
//      assistant <- createPlanner(vectorStoreId)
      eventsThread <- createSpecMessagesThread(vectorStoreId)

      _ <- service.listThreadMessages(eventsThread.id).map { messages =>
        println(messages)
      }

      thread <- service.retrieveThread(eventsThread.id)
      _ = println(thread)

      run <- service.createRun(
        threadId = eventsThread.id,
        assistantId = assistantId,
        tools = Seq(FileSearchSpec),
        responseToolChoice = Some(ToolChoice.EnforcedTool(RunTool.FileSearchTool)),
        settings = CreateRunSettings(),
        stream = false
      )

      _ = java.lang.Thread.sleep(2000)
      updatedRun <- service.retrieveRun(eventsThread.id, run.id)

      _ = java.lang.Thread.sleep(2000)
      _ = println("============= Thread messages =============")
      messages <- service.listThreadMessages(eventsThread.id)
      _ = messages.map { message =>
        message.content.map((x: ThreadMessageContent) => println(x))
      }

    } yield {
      println(run)
      println(updatedRun)
    }
}
