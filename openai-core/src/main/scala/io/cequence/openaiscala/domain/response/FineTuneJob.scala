package io.cequence.openaiscala.domain.response

import java.{util => ju}

case class FineTuneJob(
  // the object identifier, which can be referenced in the API endpoints.
  id: String,
  // the base model that is being fine-tuned
  model: String,
  // the Unix timestamp (in seconds) for when the fine-tuning job was created.
  created_at: ju.Date,
  // the Unix timestamp (in seconds) for when the fine-tuning job was finished. The value will be null if the fine-tuning job is still running.
  finished_at: Option[ju.Date],
  // the name of the fine-tuned model that is being created. The value will be null if the fine-tuning job is still running.
  fine_tuned_model: Option[String],
  // the organization that owns the fine-tuning job.
  organization_id: String,
  // the current status of the fine-tuning job, which can be either validating_files, queued, running, succeeded, failed, or cancelled.
  status: String,
  // the file ID used for training. You can retrieve the training data with the Files API.
  training_file: String,
  // the file ID used for validation. You can retrieve the validation results with the Files API.
  validation_file: Option[String],
  // the compiled results file ID(s) for the fine-tuning job. You can retrieve the results with the Files API.
  result_files: Seq[String],
  // the total number of billable tokens processed by this fine-tuning job. The value will be null if the fine-tuning job is still running.
  trained_tokens: Option[Int],
  // For fine-tuning jobs that have failed, this will contain more information on the cause of the failure.
  error: Option[String],
  // the hyperparameters used for the fine-tuning job. See the fine-tuning guide for more details.
  hyperparameters: FineTuneHyperparams,
) {
  @Deprecated
  def updated_at = finished_at
  @Deprecated
  def events: Option[Seq[FineTuneEvent]] = None
}

case class FineTuneEvent(
  created_at: ju.Date,
  level: String,
  message: String
)

// TODO: adapt
case class FineTuneHyperparams(
  batch_size: Option[Int],
  learning_rate_multiplier: Option[Double],
  n_epochs: Int,
  prompt_loss_weight: Double
)
