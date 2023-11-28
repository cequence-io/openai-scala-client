package io.cequence.openaiscala.domain.settings

import io.cequence.openaiscala.domain.EnumValue

case class CreateEmbeddingsSettings(
  // ID of the model to use.
  model: String,

  // The format to return the embeddings in. Can be either float or base64.
  // Defaults to float
  encoding_format: Option[EmbeddingsEncodingFormat] = None,

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None
)

sealed abstract class EmbeddingsEncodingFormat extends EnumValue()

object EmbeddingsEncodingFormat {
  case object float extends EmbeddingsEncodingFormat
  case object base64 extends EmbeddingsEncodingFormat
}
