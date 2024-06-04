package io.cequence.openaiscala.domain

sealed trait ChunkingStrategy

object ChunkingStrategy {
  case object AutoChunkingStrategy extends ChunkingStrategy
  case class StaticChunkingStrategy private (
    maxChunkSizeTokens: Int,
    chunkOverlapTokens: Int
  ) extends ChunkingStrategy

  object StaticChunkingStrategy {
    def apply(
      maybeMaxChunkSizeTokens: Option[Int],
      maybeChunkOverlapTokens: Option[Int]
    ): StaticChunkingStrategy = {
      val maxChunkSizeTokens = maybeMaxChunkSizeTokens.getOrElse(800)
      val chunkOverlapTokens = maybeChunkOverlapTokens.getOrElse(400)

      if (maxChunkSizeTokens < 100 || maxChunkSizeTokens > 4096)
        throw new IllegalArgumentException("maxChunkSizeTokens must be between 100 and 4096")
      if (chunkOverlapTokens < 0 || chunkOverlapTokens > maxChunkSizeTokens / 2)
        throw new IllegalArgumentException(
          "chunkOverlapTokens must be between 0 and maxChunkSizeTokens/2"
        )
      new StaticChunkingStrategy(maxChunkSizeTokens, chunkOverlapTokens)
    }
  }
}
