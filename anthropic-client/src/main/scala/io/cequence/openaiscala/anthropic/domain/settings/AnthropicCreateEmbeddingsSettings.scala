package io.cequence.openaiscala.anthropic.domain.settings

case class AnthropicCreateEmbeddingsSettings(
  // ID of the model to use.
  model: String,

  // Common property used to distinguish between types of data.
  input_type: Option[String] = None,

  // The number of dimensions the resulting output embeddings should have. Only supported in text-embedding-3 and later models.
  truncate: String = "END"
)
