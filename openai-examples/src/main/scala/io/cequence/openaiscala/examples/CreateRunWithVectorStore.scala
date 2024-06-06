package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.AssistantToolResource.FileSearchResources
import io.cequence.openaiscala.domain.response.FileInfo
import io.cequence.openaiscala.domain.response.ResponseFormat.StringResponse
import io.cequence.openaiscala.domain.settings.CreateRunSettings
import io.cequence.openaiscala.domain.{
  BaseMessage,
  FileSearchSpec,
  FunctionSpec,
  ModelId,
  RequiredAction,
  ThreadMessage,
  UserMessage,
  VectorStore
}
import io.cequence.openaiscala.examples.CreateVectorStore.service
import io.cequence.openaiscala.examples.UploadFile.service

import java.io.File
import java.nio.file.Paths
import scala.collection.immutable.ListMap
import scala.concurrent.Future

object CreateRunWithVectorStore extends Example {

  val userId = "123"

  private def scheduleFile(): File =
    Paths.get("/Users/boris/proj/cequence/eBF programme 2024 - extracted.pdf").toFile

  private def uploadFile: Future[FileInfo] =
    service.uploadFile(scheduleFile())

  private def createVectorStore(file: FileInfo) =
    service.createVectorStore(fileIds = Seq(file.id), name = Some("Conference Schedule"))

  private def createPlanner(vectorStore: VectorStore) = for {
    assistant <- service.createAssistant(
      model = ModelId.gpt_4o,
      name = Some("Schedule planner"),
      instructions = Some(
        "You pick the talks meeting my criteria I should attend at a conference."
      ),
      tools = Seq(FileSearchSpec),
      toolResources = Seq(FileSearchResources(Seq(vectorStore.id)))
    )
  } yield assistant

  def createSpecMessagesThread =
    for {
      thread <- service.createThread(
        messages = Seq(
//          ThreadMessage(
//            "I want to digitise my procurement processes and optimize them, ideally by employing AI."
//          )
        ),
        metadata = Map("user_id" -> userId)
      )
    } yield thread

  override protected def run: Future[_] =
    for {
      fileInfo <- uploadFile
      vectorStore <- createVectorStore(fileInfo)
      assistant <- createPlanner(vectorStore)
      eventsThread <- createSpecMessagesThread

      run <- service.createRun(
        threadId = eventsThread.id,
        assistantId = assistant.id,
        instructions = None,
        additionalMessages = Seq(
          UserMessage(
            "I want to digitise my procurement processes and optimize them, ideally by employing AI."
          )
        ),
        tools = Seq(FileSearchSpec),
        responseToolChoice = Some(RequiredAction.EnforcedTool(FileSearchSpec)),
        settings = CreateRunSettings(
          model = ModelId.gpt_4o,
          metadata = Map("user_id" -> userId),
          temperature = Some(0.0),
          topP = Some(1.0),
          maxPromptTokens = Some(2048),
          maxCompletionTokens = Some(2048)
        ),
        stream = false
      )
    } yield println(run)
}
