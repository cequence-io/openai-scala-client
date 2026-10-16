package io.cequence.openaiscala.domain.response

import java.{util => ju}

/**
 * A model as `GET /models` (or `GET /models/{id}`) describes it. Only the `id` is guaranteed:
 * the OpenAI-compatible hosts send different fields (live 2026-10-15) - Gemini's OpenAI
 * endpoint, DeepSeek, SambaNova and Cohere no `created`, OpenRouter and Together no
 * `owned_by`, Anthropic a `created_at` date string instead of `created` (read into `created`).
 */
case class ModelInfo(
  id: String,
  created: Option[ju.Date] = None,
  owned_by: Option[String] = None,
  @Deprecated
  root: Option[String] = None,
  @Deprecated
  parent: Option[String] = None,
  @Deprecated
  permission: Seq[Permission] = Nil
)

case class Permission(
  id: String,
  created: ju.Date,
  allow_create_engine: Boolean,
  allow_sampling: Boolean,
  allow_logprobs: Boolean,
  allow_search_indices: Boolean,
  allow_view: Boolean,
  allow_fine_tuning: Boolean,
  organization: String,
  group: Option[String],
  is_blocking: Boolean
)
