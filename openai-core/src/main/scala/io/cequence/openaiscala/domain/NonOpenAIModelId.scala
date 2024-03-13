package io.cequence.openaiscala.domain

// this feels a bit awkward, but this is a direction how the project evolves
object NonOpenAIModelId {

  // Anthropic
  val claude_3_opus_20240229 = "claude-3-opus-20240229"
  val claude_3_sonnet_20240229 = "claude-3-sonnet-20240229"
  val claude_2_1 = "claude-2.1"
  val claude_2_0 = "claude-2.0"
  val claude_instant_1_2 = "claude-instant-1.2"

  // Other
  val llama2 = "llama2"
  val llama_2_13b_chat = "llama-2-13b-chat"
  val llamaguard_7b = "llamaguard-7b"
  val nous_hermes_2_mixtral_8x7b_dpo = "nous-hermes-2-mixtral-8x7b-dpo"
  val nous_hermes_2_mistral_7b_dpo = "nous-hermes-2-mistral-7b-dpo"
  val mistral_7b_instruct = "mistral-7b-instruct"
  val mixtral_8x7b_instruct = "mixtral-8x7b-instruct"
  val gemma = "gemma"
  val gemma_7b_it = "gemma-7b-it"
  val smaug_72b_chat = "smaug-72b-chat"
  val medllama2 = "medllama2"
}
