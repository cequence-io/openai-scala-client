package io.cequence.openaiscala.v2.domain.settings

case class CreateCompletionSettings(
  // ID of the model to use
  model: String,

  // The suffix that comes after a completion of inserted text.
  suffix: Option[String] = None,

  // The maximum number of tokens to generate in the completion.
  // The token count of your prompt plus max_tokens cannot exceed the model's context length.
  // Most models have a context length of 2048 tokens (except for the newest models, which support 4096). Defaults to 16.
  max_tokens: Option[Int] = None,

  // What sampling temperature to use, between 0 and 2.
  // Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic.
  // We generally recommend altering this or top_p but not both. Defaults to 1.
  temperature: Option[Double] = None,

  // An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass.
  // So 0.1 means only the tokens comprising the top 10% probability mass are considered.
  // We generally recommend altering this or temperature but not both. Defaults to 1.
  top_p: Option[Double] = None,

  // How many completions to generate for each prompt.
  // Note: Because this parameter generates many completions, it can quickly consume your token quota.
  // Use carefully and ensure that you have reasonable settings for max_tokens and stop. Defaults to 1.
  n: Option[Int] = None,

  // Include the log probabilities on the logprobs most likely tokens, as well the chosen tokens.
  // For example, if logprobs is 5, the API will return a list of the 5 most likely tokens.
  // The API will always return the logprob of the sampled token, so there may be up to logprobs+1 elements in the response.
  // The maximum value for logprobs is 5.
  logprobs: Option[Int] = None,

  // Echo back the prompt in addition to the completion. Defaults to false.
  echo: Option[Boolean] = None,

  // Up to 4 sequences where the API will stop generating further tokens.
  // The returned text will not contain the stop sequence.
  stop: Seq[String] = Nil, // Option[String or Array]

  // Number between -2.0 and 2.0.
  // Positive values penalize new tokens based on whether they appear in the text so far, increasing the model's likelihood to talk about new topics.
  // Defaults to 0.
  presence_penalty: Option[Double] = None,

  // Number between -2.0 and 2.0.
  // Positive values penalize new tokens based on their existing frequency in the text so far, decreasing the model's likelihood to repeat the same line verbatim.
  // Defaults to 0.
  frequency_penalty: Option[Double] = None,

  // Generates best_of completions server-side and returns the "best" (the one with the highest log probability per token).
  // Results cannot be streamed. When used with n, best_of controls the number of candidate completions and n specifies how many to return – best_of must be greater than n.
  // Note: Because this parameter generates many completions, it can quickly consume your token quota.
  // Use carefully and ensure that you have reasonable settings for max_tokens and stop. Defaults to 1.
  best_of: Option[Int] = None,

  // Modify the likelihood of specified tokens appearing in the completion.
  // Accepts a json object that maps tokens (specified by their token ID in the GPT tokenizer) to an associated bias value from -100 to 100.
  // You can use this tokenizer tool (which works for both GPT-2 and GPT-3) to convert text to token IDs.
  // Mathematically, the bias is added to the logits generated by the model prior to sampling.
  // The exact effect will vary per model, but values between -1 and 1 should decrease or increase likelihood of selection;
  // values like -100 or 100 should result in a ban or exclusive selection of the relevant token.
  // As an example, you can pass {"50256": -100} to prevent the <|endoftext|> token from being generated.
  logit_bias: Map[String, Int] = Map(),

  // A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
  user: Option[String] = None,

  // If specified, our system will make a best effort to sample deterministically, such that repeated requests with the same seed and parameters should return the same result.
  // Determinism is not guaranteed, and you should refer to the system_fingerprint response parameter to monitor changes in the backend.
  seed: Option[Int] = None // NEW
)
