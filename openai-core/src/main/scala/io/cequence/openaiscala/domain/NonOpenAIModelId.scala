package io.cequence.openaiscala.domain

// this feels a bit awkward, but this is a direction how the project evolves
object NonOpenAIModelId {

  // Anthropic
  val claude_3_opus_20240229 = "claude-3-opus-20240229"
  val claude_3_sonnet_20240229 = "claude-3-sonnet-20240229"
  val claude_3_haiku_20240307 = "claude-3-haiku-20240307"
  val claude_2_1 = "claude-2.1"
  val claude_2_0 = "claude-2.0"
  val claude_instant_1_2 = "claude-instant-1.2"

  // Llama2
  val llama2 = "llama2"                                                 // Ollama
  val llama_2_7b_chat = "llama-2-7b-chat"                               // OctoML
  val llama_v2_7b_chat = "llama-v2-7b-chat"                             // Fireworks AI
  val llama_2_13b_chat = "llama-2-13b-chat"                             // OctoML
  val llama_v2_13b_chat = "llama-v2-13b-chat"                           // Fireworks AI
  val llama_2_70b_chat = "llama-2-70b-chat"                             // OctoML
  val llama_v2_70b_chat = "llama-v2-70b-chat"                           // Fireworks AI
  val llama2_7b_summarize = "llama2-7b-summarize"                       // Fireworks AI
  val llamaguard_7b = "llamaguard-7b"                                   // OctoML
  val medllama2 = "medllama2"                                           // Ollama

  // Mistral
  val mistral_7b_instruct = "mistral-7b-instruct"                       // OctoML
  val mixtral_8x7b_instruct = "mixtral-8x7b-instruct"                   // Fireworks AI and OctoML
  val mistral_7b_instruct_4k = "mistral-7b-instruct-4k"                 // Fireworks AI
  val new_mixtral_chat = "new-mixtral-chat"                             // Fireworks AI
  val nous_hermes_2_mixtral_8x7b_dpo = "nous-hermes-2-mixtral-8x7b-dpo" // OctoML
  val nous_hermes_2_mistral_7b_dpo = "nous-hermes-2-mistral-7b-dpo"     // OctoML
  val hermes_2_pro_mistral_7b = "hermes-2-pro-mistral-7b"               // Fireworks

  // Other
  val firellava_13b = "firellava-13b"                                   // Fireworks AI
  val firefunction_v1 = "firefunction-v1"                               // Fireworks AI
  val bleat_adapter = "bleat-adapter"                                   // Fireworks AI (completion)
  val gemma = "gemma"                                                   // Ollama
  val gemma_7b_it = "gemma-7b-it"                                       // Fireworks AI and OctoML
  val smaug_72b_chat = "smaug-72b-chat"                                 // OctoML
}
