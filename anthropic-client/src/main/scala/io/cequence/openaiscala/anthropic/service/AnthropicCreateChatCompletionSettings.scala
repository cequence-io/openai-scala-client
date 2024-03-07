package io.cequence.openaiscala.anthropic.service

final case class AnthropicCreateChatCompletionSettings(
  // The model that will complete your prompt.
  // See [[models|https://docs.anthropic.com/claude/docs/models-overview]] for additional details and options.
  model: String,
  // System prompt.
  //A system prompt is a way of providing context and instructions to Claude, such as specifying a particular goal or role. See our [[guide to system prompts|https://docs.anthropic.com/claude/docs/system-prompts]].
  system: Option[String] = None,
  // The maximum number of tokens to generate before stopping.
  // Note that our models may stop before reaching this maximum. This parameter only specifies the absolute maximum number of tokens to generate.
  // Different models have different maximum values for this parameter. See [[models|https://docs.anthropic.com/claude/docs/models-overview]] for details.
  max_tokens: Int,
  // An object describing metadata about the request.
  // user_id - An external identifier for the user who is associated with the request.
  // This should be a uuid, hash value, or other opaque identifier. Anthropic may use this id to help detect abuse. Do not include any identifying information such as name, email address, or phone number.
  metadata: Map[String, String] = Map.empty,
  // Custom text sequences that will cause the model to stop generating.
  // Our models will normally stop when they have naturally completed their turn, which will result in a response stop_reason of "end_turn".
  // If you want the model to stop generating when it encounters custom strings of text, you can use the stop_sequences parameter. If the model encounters one of the custom sequences, the response stop_reason value will be "stop_sequence" and the response stop_sequence value will contain the matched stop sequence.
  stop_sequences: Seq[String] = Seq.empty,
  // Amount of randomness injected into the response.
  // Defaults to 1.0. Ranges from 0.0 to 1.0. Use temperature closer to 0.0 for analytical / multiple choice, and closer to 1.0 for creative and generative tasks.
  // Note that even with temperature of 0.0, the results will not be fully deterministic.
  temperature: Option[Double] = None,
  // Use nucleus sampling.
  // In nucleus sampling, we compute the cumulative distribution over all the options for each subsequent token in decreasing probability order and cut it off once it reaches a particular probability specified by top_p. You should either alter temperature or top_p, but not both.
  // Recommended for advanced use cases only. You usually only need to use temperature.
  top_p: Option[Double] = None,
  // Only sample from the top K options for each subsequent token.
  // Used to remove "long tail" low probability responses. Learn more technical details here.
  // Recommended for advanced use cases only. You usually only need to use temperature.
  top_k: Option[Int] = None
)
