package io.cequence.openaiscala.domain.settings

case class CreateEmbeddingsSettings(
  // ID of the model to use.
  model: String,

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None
)
