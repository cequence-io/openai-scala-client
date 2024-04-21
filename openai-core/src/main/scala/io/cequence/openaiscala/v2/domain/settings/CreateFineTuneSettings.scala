package io.cequence.openaiscala.v2.domain.settings

case class CreateFineTuneSettings(
  // The name of the base model to fine-tune.
  // You can select one of "ada", "babbage", "curie", "davinci", "gpt-3.5-turbo-0613", or a fine-tuned model created after 2022-04-21.
  // To learn more about these models, see the Models documentation.
  model: String,

  // Number of examples in each batch or "auto".
  // A larger batch size means that model parameters are updated less frequently, but with lower variance.
  // Defaults to auto
  batch_size: Option[Int] = None,

  // Scaling factor for the learning rate or "auto". A smaller learning rate may be useful to avoid overfitting.
  // Defaults to auto
  learning_rate_multiplier: Option[Int] = None,

  // The number of epochs to train the model for.
  // An epoch refers to one full cycle through the training dataset.
  // Defaults to auto
  n_epochs: Option[Int] = None,

  // A string of up to 40 characters that will be added to your fine-tuned model name.
  // For example, a suffix of "custom-model-name" would produce a model name like ada:ft-your-org:custom-model-name-2022-02-15-04-21-04.
  suffix: Option[String] = None
)
