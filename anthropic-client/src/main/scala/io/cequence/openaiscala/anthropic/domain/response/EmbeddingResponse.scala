package io.cequence.openaiscala.anthropic.domain.response

case class EmbeddingResponse(
  data: Seq[Double],
  model: String,
  usage: EmbeddingUsageInfo
)

case class EmbeddingInfo(
  embedding: Seq[Double],
  index: Int
)

case class EmbeddingUsageInfo(
  total_tokens: Int
)
