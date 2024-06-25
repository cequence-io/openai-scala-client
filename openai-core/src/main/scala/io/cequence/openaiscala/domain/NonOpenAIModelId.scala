package io.cequence.openaiscala.domain

// this feels a bit awkward, but this is the direction the project is increasingly pursuing
object NonOpenAIModelId {

  // Anthropic
  val claude_3_5_sonnet_20240620 = "claude-3-5-sonnet-20240620"
  val claude_3_opus_20240229 = "claude-3-opus-20240229"
  val claude_3_sonnet_20240229 = "claude-3-sonnet-20240229"
  val claude_3_haiku_20240307 = "claude-3-haiku-20240307"
  val claude_2_1 = "claude-2.1"
  val claude_2_0 = "claude-2.0"
  val claude_instant_1_2 = "claude-instant-1.2"

  // Llama2/3
  val llama_v3_8b_instruct = "llama-v3-8b-instruct" // Fireworks AI
  val llama3_8b_8192 = "llama3-8b-8192" // Groq
  val llama_v3_70b_instruct = "llama-v3-70b-instruct" // Fireworks AI
  val llama3_70b_8192 = "llama3-70b-8192" // Groq
  val meta_llama_3_8b_instruct = "meta-llama-3-8b-instruct" // OctoML
  val meta_llama_3_70b_instruct = "meta-llama-3-70b-instruct" // OctoML
  val llama2 = "llama2" // Ollama
  val llama_2_7b_chat = "llama-2-7b-chat"
  val llama_v2_7b_chat = "llama-v2-7b-chat" // Fireworks AI
  val llama_2_13b_chat = "llama-2-13b-chat" // OctoML
  val llama_v2_13b_chat = "llama-v2-13b-chat" // Fireworks AI
  val llama_2_70b_chat = "llama-2-70b-chat" // OctoML
  val llama_v2_70b_chat = "llama-v2-70b-chat" // Fireworks AI
  val llama2_70b_4096 = "llama2-70b-4096" // Groq
  val llama2_7b_summarize = "llama2-7b-summarize" // Fireworks AI (completion)
  val llamaguard_7b = "llamaguard-7b" // OctoML
  val medllama2 = "medllama2" // Ollama

  // Mistral
  val mixtral_8x22b = "mixtral-8x22b" // Fireworks AI and OctML (completion API)
  @Deprecated
  val mixtral_8x22b_instruct_preview = "mixtral-8x22b-instruct-preview" // Fireworks AI
  val mixtral_8x22b_instruct = "mixtral-8x22b-instruct" // Fireworks AI and OctoML
  val mixtral_8x22b_hf = "mixtral-8x22b-hf" // Fireworks AI
  val mixtral_8x22b_instruct_hf = "mixtral-8x22b-instruct-hf" // Fireworks AI
  val mixtral_8x22b_finetuned = "mixtral-8x22b-finetuned" // OctoML
  val mistral_7b_instruct = "mistral-7b-instruct" // OctoML
  val mixtral_8x7b_instruct = "mixtral-8x7b-instruct" // Fireworks AI and OctoML
  val mixtral_8x7b_32768 = "mixtral-8x7b-32768" // Groq
  val mistral_7b_instruct_4k = "mistral-7b-instruct-4k" // Fireworks AI
  val new_mixtral_chat = "new-mixtral-chat" // Fireworks AI
  val nous_hermes_2_mixtral_8x7b_dpo = "nous-hermes-2-mixtral-8x7b-dpo" // OctoML
  val nous_hermes_2_mistral_7b_dpo = "nous-hermes-2-mistral-7b-dpo" // OctoML
  val hermes_2_pro_mistral_7b = "hermes-2-pro-mistral-7b" // Fireworks
  val mistral_large = "mistral-large"

  // Other
  val drbx_instruct = "dbrx-instruct" // Fireworks AI
  val firellava_13b = "firellava-13b" // Fireworks AI
  val firefunction_v1 = "firefunction-v1" // Fireworks AI
  val bleat_adapter = "bleat-adapter" // Fireworks AI (completion)
  val gemma = "gemma" // Ollama
  val gemma_7b_it = "gemma-7b-it" // Fireworks AI, OctoML, and Groq
  val smaug_72b_chat = "smaug-72b-chat" // OctoML
  val cohere_command_r_plus = "cohere-command-r-plus"

  // Google Vertex AI
  val gemini_1_5_flash_001 = "gemini-1.5-flash-001"
  val gemini_1_5_pro_001 = "gemini-1.5-pro-001"
  val gemini_1_0_pro_001 = "gemini-1.0-pro-001"
  val gemini_1_0_pro_vision_001 = "gemini-1.0-pro-vision-001"
  val text_embedding_004 = "text-embedding-004"
}
