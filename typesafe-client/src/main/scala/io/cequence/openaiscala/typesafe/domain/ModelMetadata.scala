package io.cequence.openaiscala.typesafe.domain

/**
 * A model or alias available to the account (`GET /v1/models`); `name` is what
 * [[SystemOneRequest.model]] takes.
 *
 * @param release_date
 *   `YYYY-MM-DD`
 * @param input_modalities
 *   what the model reads - `text`, `image` - when the host says (Liquid AI, llama.cpp,
 *   OpenRouter)
 */
final case class ModelMetadata(
  name: String,
  description: String,
  release_date: String,
  input_modalities: Option[Seq[String]] = None
)
