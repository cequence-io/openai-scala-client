package io.cequence.openaiscala.domain.settings

case class CreateFineTuneSettings(
    // The name of the base model to fine-tune.
    // You can select one of "ada", "babbage", "curie", "davinci", or a fine-tuned model created after 2022-04-21.
    // To learn more about these models, see the Models documentation.
    // Defaults to 'curie'
    model: Option[String] = None,

    // The number of epochs to train the model for.
    // An epoch refers to one full cycle through the training dataset.
    // Defaults to 4
    n_epochs: Option[Int] = None,

    // The batch size to use for training.
    // The batch size is the number of training examples used to train a single forward and backward pass.
    // By default, the batch size will be dynamically configured to be ~0.2% of the number of examples in the training set,
    // capped at 256 - in general, we've found that larger batch sizes tend to work better for larger datasets.
    batch_size: Option[Int] = None,

    // The learning rate multiplier to use for training.
    // The fine-tuning learning rate is the original learning rate used for pretraining multiplied by this value.
    // By default, the learning rate multiplier is the 0.05, 0.1, or 0.2 depending on final batch_size (larger learning rates tend to perform better with larger batch sizes).
    // We recommend experimenting with values in the range 0.02 to 0.2 to see what produces the best results.
    learning_rate_multiplier: Option[Double] = None,

    // The weight to use for loss on the prompt tokens.
    // This controls how much the model tries to learn to generate the prompt (as compared to the completion which always has a weight of 1.0),
    // and can add a stabilizing effect to training when completions are short.
    // If prompts are extremely long (relative to completions), it may make sense to reduce this weight so as to avoid over-prioritizing learning the prompt.
    // Defaults to 0.01
    prompt_loss_weight: Option[Double] = None,

    // If set, we calculate classification-specific metrics such as accuracy and F-1 score using the validation set at the end of every epoch.
    // These metrics can be viewed in the <a href="https://beta.openai.com/docs/guides/fine-tuning/analyzing-your-fine-tuned-model">results file</a>.
    // In order to compute classification metrics, you must provide a validation_file.
    // Additionally, you must specify classification_n_classes for multiclass classification or classification_positive_class for binary classification.
    // Defaults to false
    compute_classification_metrics: Option[Boolean] = None,

    // The number of classes in a classification task.
    // This parameter is required for multiclass classification.
    classification_n_classes: Option[Int] = None,

    // The positive class in binary classification.
    // This parameter is needed to generate precision, recall, and F1 metrics when doing binary classification.
    classification_positive_class: Option[String] = None,

    // If this is provided, we calculate F-beta scores at the specified beta values.
    // The F-beta score is a generalization of F-1 score. This is only used for binary classification.
    // With a beta of 1 (i.e. the F-1 score), precision and recall are given the same weight.
    // A larger beta score puts more weight on recall and less on precision.
    // A smaller beta score puts more weight on precision and less on recall.
    classification_betas: Option[Seq[Double]] = None,

    // A string of up to 40 characters that will be added to your fine-tuned model name.
    // For example, a suffix of "custom-model-name" would produce a model name like ada:ft-your-org:custom-model-name-2022-02-15-04-21-04.
    suffix: Option[String] = None
)
