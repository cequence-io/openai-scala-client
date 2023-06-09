package io.cequence.openaiscala.domain.response

import java.{util => ju}

case class FineTuneJob(
    id: String,
    model: String,
    created_at: ju.Date,
    events: Option[Seq[FineTuneEvent]],
    fine_tuned_model: Option[String],
    hyperparams: FineTuneHyperparams,
    organization_id: String,
    result_files: Seq[FileInfo],
    status: String, // e.g. pending or cancelled
    validation_files: Seq[FileInfo],
    training_files: Seq[FileInfo],
    updated_at: ju.Date
)

case class FineTuneEvent(
    created_at: ju.Date,
    level: String,
    message: String
)

case class FineTuneHyperparams(
    batch_size: Option[Int],
    learning_rate_multiplier: Option[Double],
    n_epochs: Int,
    prompt_loss_weight: Double
)
