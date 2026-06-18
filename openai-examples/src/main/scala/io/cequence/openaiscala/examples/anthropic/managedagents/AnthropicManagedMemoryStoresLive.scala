package io.cequence.openaiscala.examples.anthropic.managedagents

import io.cequence.openaiscala.anthropic.domain.managedagents.{MemoryEntry, MemoryView}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMemoryStoreSettings,
  AnthropicUpdateMemoryStoreSettings
}

import scala.concurrent.Future

/**
 * Live end-to-end check of the Memory Stores API: create a store, create/get/update a memory
 * (with a content_sha256 precondition), list memories, list and redact memory versions, then
 * delete the memory and the store.
 */
object AnthropicManagedMemoryStoresLive extends AnthropicManagedAgentsExample {

  override protected def run: Future[_] =
    for {
      store <- service.createMemoryStore(
        AnthropicCreateMemoryStoreSettings(
          name = "openai-scala-client smoke store",
          description = Some("smoke-test store")
        )
      )
      _ = println(s"store=${store.id}")
      _ <- memoryFlow(store.id).transformWith { result =>
        service.deleteMemoryStore(store.id).transform(_ => result)
      }
    } yield ()

  private def memoryFlow(storeId: String): Future[Unit] =
    for {
      mem <- service.createMemory(
        storeId,
        path = "/notes/hello.md",
        content = "Hello, memory."
      )
      _ = println(
        s"create memory: id=${mem.id} path=${mem.path} sha=${mem.contentSha256.take(8)}"
      )

      updated <- service.updateMemory(
        storeId,
        mem.id,
        content = Some("Hello, updated memory."),
        expectedContentSha256 = Some(mem.contentSha256)
      )
      _ = println(
        s"update: new sha=${updated.contentSha256.take(8)} size=${updated.contentSizeBytes}"
      )

      full <- service.getMemory(storeId, mem.id, Some(MemoryView.full))
      _ = println(s"get (full): content=${full.content}")

      entries <- service.listMemories(storeId, view = Some(MemoryView.basic))
      memCount = entries.data.collect { case _: MemoryEntry.Item => 1 }.size
      _ = println(s"list: entries=${entries.data.size} (memories=$memCount)")

      versions <- service.listMemoryVersions(storeId, memoryId = Some(mem.id))
      _ = println(
        s"versions: ${versions.data.size} (ops=${versions.data.map(_.operation.toString)})"
      )

      _ <- versions.data.lastOption match {
        case Some(v) =>
          service.redactMemoryVersion(storeId, v.id).map { r =>
            println(s"redact: redactedAt=${r.redactedAt}")
          }
        case None => Future.unit
      }

      _ <- service.deleteMemory(storeId, mem.id)
      _ = println("memory deleted")

      s2 <- service.updateMemoryStore(
        storeId,
        AnthropicUpdateMemoryStoreSettings(description = Some("updated smoke-test store"))
      )
      _ = println(s"update store: description=${s2.description}")
    } yield ()
}
