package io.cequence.openaiscala.v2.domain.response

case class EmbeddingResponse(
  data: Seq[EmbeddingInfo],
  model: String,
  usage: EmbeddingUsageInfo
)

case class EmbeddingInfo(
  embedding: Seq[Double],
  index: Int
)

case class EmbeddingUsageInfo(
  prompt_tokens: Int,
  total_tokens: Int
)
