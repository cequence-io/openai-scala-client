package io.cequence.openaiscala.anthropic.domain.managedagents

import io.cequence.wsclient.domain.EnumValue
import play.api.libs.json.JsObject

/** Whether memory list/read returns content (`full`) or metadata only (`basic`). */
sealed trait MemoryView extends EnumValue

object MemoryView {
  case object basic extends MemoryView
  case object full extends MemoryView

  def values: Seq[MemoryView] = Seq(basic, full)
}

/** The kind of mutation a memory version records. */
sealed trait MemoryVersionOperation extends EnumValue

object MemoryVersionOperation {
  case object created extends MemoryVersionOperation
  case object modified extends MemoryVersionOperation
  case object deleted extends MemoryVersionOperation

  def values: Seq[MemoryVersionOperation] = Seq(created, modified, deleted)
}

/**
 * A workspace-scoped collection of small text documents that persists across sessions. Attach
 * to a session via a `memory_store` resource.
 *
 * @see
 *   <a href="https://platform.claude.com/docs/en/api/beta/memory_stores">Anthropic Memory
 *   Stores API</a>
 */
final case class MemoryStore(
  id: String,
  name: String,
  description: Option[String] = None,
  metadata: Map[String, String] = Map.empty,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None,
  archivedAt: Option[String] = None
) {
  val `type`: String = "memory_store"
}

/**
 * A single text document at a hierarchical path inside a memory store. Addressed by its `mem_`
 * id (stable across renames). `content` is populated only for `view=full`.
 */
final case class Memory(
  id: String,
  path: String,
  contentSha256: String,
  contentSizeBytes: Long,
  memoryStoreId: String,
  memoryVersionId: String,
  content: Option[String] = None,
  createdAt: Option[String] = None,
  updatedAt: Option[String] = None
) {
  val `type`: String = "memory"
}

/**
 * An entry returned when listing memories — either a memory or a directory-like path prefix.
 */
sealed trait MemoryEntry

object MemoryEntry {
  final case class Item(memory: Memory) extends MemoryEntry
  final case class Prefix(path: String) extends MemoryEntry {
    val `type`: String = "memory_prefix"
  }
}

/**
 * The actor that created or redacted a memory version. The discriminating `type` (e.g.
 * `api_key`, `agent`, `session`) is typed; every other identity field (`api_key_id`,
 * `agent_id`, `session_id`, …) is preserved verbatim in [[raw]].
 */
final case class MemoryActor(
  `type`: String,
  raw: JsObject = JsObject.empty
)

/**
 * One immutable, attributed row in a memory's append-only history. A redacted version returns
 * `content`/`path`/`contentSha256`/`contentSizeBytes` as `None` — branch on [[redactedAt]];
 * [[redactedBy]] then carries the redacting actor.
 */
final case class MemoryVersion(
  id: String,
  memoryId: String,
  memoryStoreId: String,
  operation: MemoryVersionOperation,
  path: Option[String] = None,
  contentSha256: Option[String] = None,
  contentSizeBytes: Option[Long] = None,
  content: Option[String] = None,
  createdBy: Option[MemoryActor] = None,
  createdAt: Option[String] = None,
  redactedBy: Option[MemoryActor] = None,
  redactedAt: Option[String] = None
) {
  val `type`: String = "memory_version"

  /** Convenience accessor for the creating actor's discriminator type. */
  def createdByType: Option[String] = createdBy.map(_.`type`)
}
