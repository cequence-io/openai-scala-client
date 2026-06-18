package io.cequence.openaiscala.anthropic.service

import io.cequence.openaiscala.anthropic.domain.managedagents.{
  Memory,
  MemoryEntry,
  MemoryStore,
  MemoryVersion,
  MemoryVersionOperation,
  MemoryView,
  PagedResponse
}
import io.cequence.openaiscala.anthropic.domain.settings.{
  AnthropicCreateMemoryStoreSettings,
  AnthropicUpdateMemoryStoreSettings
}

import scala.concurrent.Future

/**
 * Anthropic Managed Agents API — memory stores, memories, and memory versions. A memory store
 * is workspace-scoped persistent text that survives across sessions. Requires the
 * `managed-agents-2026-04-01` beta; not available on Bedrock.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/memory_stores">Anthropic Memory
 *   Stores API</a>
 */
trait AnthropicMemoryStoreService {

  /** Creates a memory store (`POST /v1/memory_stores`). */
  def createMemoryStore(settings: AnthropicCreateMemoryStoreSettings): Future[MemoryStore]

  /** Lists memory stores (`GET /v1/memory_stores`). */
  def listMemoryStores(
    includeArchived: Option[Boolean] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[MemoryStore]]

  /** Retrieves a memory store (`GET /v1/memory_stores/{id}`). */
  def getMemoryStore(memoryStoreId: String): Future[MemoryStore]

  /** Updates a memory store (`POST /v1/memory_stores/{id}`). */
  def updateMemoryStore(
    memoryStoreId: String,
    settings: AnthropicUpdateMemoryStoreSettings
  ): Future[MemoryStore]

  /** Deletes a memory store (`DELETE /v1/memory_stores/{id}`). */
  def deleteMemoryStore(memoryStoreId: String): Future[Unit]

  /** Archives a memory store (`POST /v1/memory_stores/{id}/archive`). */
  def archiveMemoryStore(memoryStoreId: String): Future[MemoryStore]

  // -- Memories --

  /**
   * Creates a memory at `path` (`POST /v1/memory_stores/{id}/memories`). Fails with a 409
   * conflict if the path is already occupied.
   */
  def createMemory(
    memoryStoreId: String,
    path: String,
    content: String
  ): Future[Memory]

  /** Lists memories (`GET /v1/memory_stores/{id}/memories`). */
  def listMemories(
    memoryStoreId: String,
    pathPrefix: Option[String] = None,
    depth: Option[Int] = None,
    view: Option[MemoryView] = None,
    orderBy: Option[String] = None,
    order: Option[String] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[MemoryEntry]]

  /** Retrieves a memory by id (`GET /v1/memory_stores/{id}/memories/{memoryId}`). */
  def getMemory(
    memoryStoreId: String,
    memoryId: String,
    view: Option[MemoryView] = None
  ): Future[Memory]

  /**
   * Updates a memory's content and/or path (`POST
   * /v1/memory_stores/{id}/memories/{memoryId}`).
   *
   * @param expectedContentSha256
   *   Optimistic-concurrency precondition; on mismatch the call fails with a 409.
   */
  def updateMemory(
    memoryStoreId: String,
    memoryId: String,
    content: Option[String] = None,
    path: Option[String] = None,
    expectedContentSha256: Option[String] = None
  ): Future[Memory]

  /** Deletes a memory (`DELETE /v1/memory_stores/{id}/memories/{memoryId}`). */
  def deleteMemory(
    memoryStoreId: String,
    memoryId: String
  ): Future[Unit]

  // -- Memory versions --

  /** Lists memory versions (`GET /v1/memory_stores/{id}/memory_versions`), newest first. */
  def listMemoryVersions(
    memoryStoreId: String,
    memoryId: Option[String] = None,
    operation: Option[MemoryVersionOperation] = None,
    limit: Option[Int] = None,
    page: Option[String] = None
  ): Future[PagedResponse[MemoryVersion]]

  /** Retrieves a memory version (`GET /v1/memory_stores/{id}/memory_versions/{versionId}`). */
  def getMemoryVersion(
    memoryStoreId: String,
    memoryVersionId: String
  ): Future[MemoryVersion]

  /**
   * Redacts a memory version's content while preserving the audit row (`POST
   * /v1/memory_stores/{id}/memory_versions/{versionId}/redact`).
   */
  def redactMemoryVersion(
    memoryStoreId: String,
    memoryVersionId: String
  ): Future[MemoryVersion]
}
