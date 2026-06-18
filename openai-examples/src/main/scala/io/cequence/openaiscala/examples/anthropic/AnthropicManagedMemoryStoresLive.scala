package io.cequence.openaiscala.examples.anthropic

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.cequence.openaiscala.anthropic.domain.managedagents.{MemoryEntry, MemoryView}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMemoryStoreSettings,
  AnthropicUpdateMemoryStoreSettings
}
import io.cequence.openaiscala.anthropic.service.AnthropicServiceFactory

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

/**
 * Live end-to-end check of the Memory Stores API: create a store, create/get/update a memory
 * (with a content_sha256 precondition), list memories, list and redact memory versions, then
 * delete the memory and the store. Requires `ANTHROPIC_API_KEY` with the
 * `managed-agents-2026-04-01` beta.
 */
object AnthropicManagedMemoryStoresLive {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def main(args: Array[String]): Unit = {
    val service = AnthropicServiceFactory()

    try {
      println("=== create store ===")
      val store = Await.result(
        service.createMemoryStore(
          AnthropicCreateMemoryStoreSettings(
            name = "openai-scala-client smoke store",
            description = Some("smoke-test store")
          )
        ),
        2.minutes
      )
      println(s"store=${store.id}")

      try {
        println("\n=== create memory ===")
        val mem = Await.result(
          service.createMemory(store.id, path = "/notes/hello.md", content = "Hello, memory."),
          1.minute
        )
        println(s"id=${mem.id} path=${mem.path} sha=${mem.contentSha256.take(8)}…")

        println("\n=== update memory (with precondition) ===")
        val updated = Await.result(
          service.updateMemory(
            store.id,
            mem.id,
            content = Some("Hello, updated memory."),
            expectedContentSha256 = Some(mem.contentSha256)
          ),
          1.minute
        )
        println(s"new sha=${updated.contentSha256.take(8)}… size=${updated.contentSizeBytes}")

        println("\n=== get (full view) ===")
        val full =
          Await.result(service.getMemory(store.id, mem.id, Some(MemoryView.full)), 1.minute)
        println(s"content=${full.content}")

        println("\n=== list memories ===")
        val entries =
          Await.result(service.listMemories(store.id, view = Some(MemoryView.basic)), 1.minute)
        val memCount = entries.data.collect { case _: MemoryEntry.Item => 1 }.size
        println(s"entries=${entries.data.size} (memories=$memCount)")

        println("\n=== list versions ===")
        val versions =
          Await.result(service.listMemoryVersions(store.id, memoryId = Some(mem.id)), 1.minute)
        println(
          s"versions=${versions.data.size} (ops=${versions.data.map(_.operation.toString)})"
        )

        println("\n=== redact oldest version ===")
        versions.data.lastOption.foreach { v =>
          val redacted = Await.result(service.redactMemoryVersion(store.id, v.id), 1.minute)
          println(s"redactedAt=${redacted.redactedAt}")
        }

        println("\n=== delete memory ===")
        Await.result(service.deleteMemory(store.id, mem.id), 1.minute)
        println("memory deleted")

        println("\n=== update store ===")
        val s2 = Await.result(
          service.updateMemoryStore(
            store.id,
            AnthropicUpdateMemoryStoreSettings(description = Some("updated smoke-test store"))
          ),
          1.minute
        )
        println(s"description=${s2.description}")
      } finally {
        Await.result(service.deleteMemoryStore(store.id), 1.minute)
        println("store deleted")
      }

      println("\nMemory stores smoke test passed.")
    } finally {
      service.close()
      system.terminate()
    }
  }
}
