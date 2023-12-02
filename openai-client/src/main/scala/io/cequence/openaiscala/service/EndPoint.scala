package io.cequence.openaiscala.service

import io.cequence.openaiscala.domain.EnumValue

sealed abstract class EndPoint(value: String = "") extends EnumValue(value)

object EndPoint {
  case object models extends EndPoint
  case object completions extends EndPoint
  case object chat_completions extends EndPoint("chat/completions")
  case object edits extends EndPoint
  case object images_generations extends EndPoint("images/generations")
  case object images_edits extends EndPoint("images/edits")
  case object images_variations extends EndPoint("images/variations")
  case object embeddings extends EndPoint
  case object audio_speech extends EndPoint("audio/speech")
  case object audio_transcriptions extends EndPoint("audio/transcriptions")
  case object audio_translations extends EndPoint("audio/translations")
  case object files extends EndPoint
  case object fine_tunes extends EndPoint("fine_tuning/jobs")
  case object moderations extends EndPoint
  case object threads extends EndPoint
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
  case object hyperparameters extends Param
  case object n_epochs extends Param
  case object batch_size extends Param
  case object learning_rate_multiplier extends Param
  @Deprecated
  case object prompt_loss_weight extends Param
  @Deprecated
  case object compute_classification_metrics extends Param
  @Deprecated
  case object classification_n_classes extends Param
  @Deprecated
  case object classification_positive_class extends Param
  @Deprecated
  case object classification_betas extends Param
  case object language extends Param
  case object functions extends Param
  case object function_call extends Param
  case object after extends Param
  case object limit extends Param
  case object seed extends Param
  case object tools extends Param
  case object tool_choice extends Param
  case object encoding_format extends Param
  case object quality extends Param
  case object style extends Param
  case object voice extends Param
  case object speed extends Param
  case object metadata extends Param
}
