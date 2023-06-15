package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.EnumValue

sealed abstract class EndPoint(value: String = "") extends EnumValue(value)

object EndPoint extends Enumeration {
  case object models extends EndPoint
  case object completions extends EndPoint
  case object chat_completions extends EndPoint("chat/completions")
  case object edits extends EndPoint
  case object images_generations extends EndPoint("images/generations")
  case object images_edits extends EndPoint("images/edits")
  case object images_variations extends EndPoint("images/variations")
  case object embeddings extends EndPoint
  case object audio_transcriptions extends EndPoint("audio/transcriptions")
  case object audio_translations extends EndPoint("audio/translations")
  case object files extends EndPoint
  case object fine_tunes extends EndPoint("fine-tunes")
  case object moderations extends EndPoint
}

sealed abstract class Param extends EnumValue()

object Param {
  case object model extends Param
  case object prompt extends Param
  case object suffix extends Param
  case object max_tokens extends Param
  case object temperature extends Param
  case object top_p extends Param
  case object n extends Param
  case object stream extends Param
  case object logprobs extends Param
  case object echo extends Param
  case object stop extends Param
  case object presence_penalty extends Param
  case object frequency_penalty extends Param
  case object best_of extends Param
  case object logit_bias extends Param
  case object user extends Param
  case object messages extends Param
  case object input extends Param
  case object image extends Param
  case object mask extends Param
  case object instruction extends Param
  case object size extends Param
  case object response_format extends Param
  case object file extends Param
  case object purpose extends Param
  case object training_file extends Param
  case object validation_file extends Param
  case object n_epochs extends Param
  case object batch_size extends Param
  case object learning_rate_multiplier extends Param
  case object prompt_loss_weight extends Param
  case object compute_classification_metrics extends Param
  case object classification_n_classes extends Param
  case object classification_positive_class extends Param
  case object classification_betas extends Param
  case object language extends Param
  case object functions extends Param
  case object function_call extends Param
}