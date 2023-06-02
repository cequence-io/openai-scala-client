package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.EnumValue

sealed abstract class Command(value: String = "") extends EnumValue(value)

object Command extends Enumeration {
  case object models extends Command
  case object completions extends Command
  case object chat_completions extends Command("chat/completions")
  case object edits extends Command
  case object images_generations extends Command("images/generations")
  case object images_edits extends Command("images/edits")
  case object images_variations extends Command("images/variations")
  case object embeddings extends Command
  case object audio_transcriptions extends Command("audio/transcriptions")
  case object audio_translations extends Command("audio/translations")
  case object files extends Command
  case object fine_tunes extends Command("fine-tunes")
  case object moderations extends Command
}

sealed abstract class Tag extends EnumValue()

object Tag {
  case object model extends Tag
  case object prompt extends Tag
  case object suffix extends Tag
  case object max_tokens extends Tag
  case object temperature extends Tag
  case object top_p extends Tag
  case object n extends Tag
  case object stream extends Tag
  case object logprobs extends Tag
  case object echo extends Tag
  case object stop extends Tag
  case object presence_penalty extends Tag
  case object frequency_penalty extends Tag
  case object best_of extends Tag
  case object logit_bias extends Tag
  case object user extends Tag
  case object messages extends Tag
  case object input extends Tag
  case object image extends Tag
  case object mask extends Tag
  case object instruction extends Tag
  case object size extends Tag
  case object response_format extends Tag
  case object file extends Tag
  case object purpose extends Tag
  case object training_file extends Tag
  case object validation_file extends Tag
  case object n_epochs extends Tag
  case object batch_size extends Tag
  case object learning_rate_multiplier extends Tag
  case object prompt_loss_weight extends Tag
  case object compute_classification_metrics extends Tag
  case object classification_n_classes extends Tag
  case object classification_positive_class extends Tag
  case object classification_betas extends Tag
  case object language extends Tag
}