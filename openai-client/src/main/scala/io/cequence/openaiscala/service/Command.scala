package io.cequence.openaiscala.service

object Command extends Enumeration {
  val models = Value
  val completions = Value
  val chat_completions = Value("chat/completions")
  val edits = Value
  val images_generations = Value("images/generations")
  val images_edits = Value("images/edits")
  val images_variations = Value("images/variations")
  val embeddings = Value
  val audio_transcriptions = Value("audio/transcriptions")
  val audio_translations = Value("audio/translations")
  val files = Value
  val fine_tunes = Value("fine-tunes")
  val moderations = Value

  @Deprecated
  val engines = Value
}

object Tag extends Enumeration {
  val model, prompt, suffix, max_tokens, temperature, top_p, n, stream, logprobs, echo, stop,
  presence_penalty, frequency_penalty, best_of, logit_bias, user, messages,
  input, image, mask, instruction, size, response_format, file, purpose, file_id,
  training_file, validation_file, n_epochs, batch_size, learning_rate_multiplier, prompt_loss_weight,
  compute_classification_metrics, classification_n_classes, classification_positive_class,
  classification_betas, fine_tune_id, language = Value
}
