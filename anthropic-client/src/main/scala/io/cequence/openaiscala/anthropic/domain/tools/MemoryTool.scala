package io.cequence.openaiscala.anthropic.domain.tools

/**
 * Memory tool for persisting information across conversations. Name is always "memory". Type is "memory_20250818".
 */
case class MemoryTool() extends Tool {
  override val name: String = "memory"
  override val `type`: String = "memory_20250818"
}
