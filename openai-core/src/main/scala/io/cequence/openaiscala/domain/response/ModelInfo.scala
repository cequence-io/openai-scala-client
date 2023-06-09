package io.cequence.openaiscala.domain.response

import java.{util => ju}

case class ModelInfo(
    id: String,
    created: ju.Date,
    owned_by: String,
    root: String,
    parent: Option[String],
    permission: Seq[Permission]
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
