package io.cequence.openaiscala.domain.response

import io.cequence.openaiscala.domain.FineTune

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
  // TODO: create an enum type for status
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
  error: Option[FineTuneError],
  // the hyperparameters used for the fine-tuning job. See the fine-tuning guide for more details.
  hyperparameters: FineTuneHyperparams,
  // A list of integrations to enable for this fine-tuning job.
  integrations: Option[Seq[FineTune.Integration]],
  // The seed used for the fine-tuning job.
  seed: Int
)

case class FineTuneEvent(
  id: String,
  created_at: ju.Date,
  level: String,
  message: String,
  data: Option[Map[String, Any]]
)

// The fine_tuning.job.checkpoint object represents a model checkpoint for a fine-tuning job that is ready to use.
case class FineTuneCheckpoint(
  // The checkpoint identifier, which can be referenced in the API endpoints.
  id: String,
  // The Unix timestamp (in seconds) for when the checkpoint was created.
  created_at: ju.Date,
  // The name of the fine-tuned checkpoint model that is created.
  fine_tuned_model_checkpoint: String,
  // The step number that the checkpoint was created at.
  step_number: Long,
  // Metrics at the step number during the fine-tuning job.
  metrics: Metrics,
  // The name of the fine-tuning job that this checkpoint was created from.
  fine_tuning_job_id: String
)

final case class Metrics(
  step: Long,
  train_loss: Double,
  train_mean_token_accuracy: Double,
  valid_loss: Double,
  valid_mean_token_accuracy: Double,
  full_valid_loss: Double,
  full_valid_mean_token_accuracy: Double
)

case class FineTuneHyperparams(
  // Number of examples in each batch or "auto".
  // A larger batch size means that model parameters are updated less frequently, but with lower variance.
  batch_size: Option[Either[Int, String]],

  // Scaling factor for the learning rate or "auto". A smaller learning rate may be useful to avoid overfitting.
  learning_rate_multiplier: Option[Either[Int, String]],

  // the number of epochs or "auto" (if not specified initially)
  // "auto" decides the optimal number of epochs based on the size of the dataset.
  n_epochs: Either[Int, String]
)

case class FineTuneError(
  // A machine-readable error code.
  code: String,
  // A human-readable error message.
  message: String,
  // The parameter that was invalid, usually training_file or validation_file.
  // This field will be null if the failure was not parameter-specific
  param: Option[String]
)
