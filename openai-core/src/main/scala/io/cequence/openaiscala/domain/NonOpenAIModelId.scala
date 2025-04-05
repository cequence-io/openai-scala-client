package io.cequence.openaiscala.domain

// TODO: split by providers
object NonOpenAIModelId {

  // Anthropic
  val claude_3_7_sonnet_latest = "claude-3-7-sonnet-latest"
  val claude_3_7_sonnet_20250219 = "claude-3-7-sonnet-20250219"
  val claude_3_5_haiku_latest = "claude-3-5-haiku-latest"
  val claude_3_5_sonnet_20241022 = "claude-3-5-sonnet-20241022"
  val claude_3_5_sonnet_20240620 = "claude-3-5-sonnet-20240620"
  val claude_3_5_haiku_20241022 = "claude-3-5-haiku-20241022"
  val claude_3_opus_20240229 = "claude-3-opus-20240229"
  val claude_3_sonnet_20240229 = "claude-3-sonnet-20240229"
  val claude_3_haiku_20240307 = "claude-3-haiku-20240307"
  val claude_2_1 = "claude-2.1"
  val claude_2_0 = "claude-2.0"
  val claude_instant_1_2 = "claude-instant-1.2"

  // Anthropic Bedrock
  val bedrock_claude_3_7_sonnet_20250219_v1_0 = "anthropic.claude-3-7-sonnet-20250219-v1:0"
  val bedrock_claude_3_5_sonnet_20241022_v2_0 = "anthropic.claude-3-5-sonnet-20241022-v2:0"
  val bedrock_claude_3_5_sonnet_20240620_v1_0 = "anthropic.claude-3-5-sonnet-20240620-v1:0"
  val bedrock_claude_3_5_haiku_20241022_v1_0 = "anthropic.claude-3-5-haiku-20241022-v1:0"
  val bedrock_claude_3_opus_20240229_v1_0 = "anthropic.claude-3-opus-20240229-v1:0"
  val bedrock_claude_3_sonnet_20240229_v1_0 = "anthropic.claude-3-sonnet-20240229-v1:0"
  val bedrock_claude_3_haiku_20240307_v1_0 = "anthropic.claude-3-haiku-20240307-v1:0"

  // Nova (Bedrock)
  val amazon_nova_pro_v1_0 = "amazon.nova-pro-v1:0"
  val amazon_nova_lite_v1_0 = "amazon.nova-lite-v1:0"
  val amazon_nova_micro_v1_0 = "amazon.nova-micro-v1:0"

  // Llama
  val llama_3_3_70b_versatile = "llama-3.3-70b-versatile" // Groq
  val llama_3_3_70b_specdec = "llama-3.3-70b-specdec" // Groq
  val llama_v3p3_70b_instruct = "llama-v3p3-70b-instruct" // Fireworks AI
  val llama_3_3_70B_instruct_turbo = "meta-llama/Llama-3.3-70B-Instruct-Turbo" // Together AI
  val llama_3_3_70B_instruct_turbo_free =
    "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free" // Together AI
  val llama_3_3_70b = "llama-3.3-70b" // Cerebras
  val llama_v3p2_1b_instruct = "llama-v3p2-1b-instruct" // Fireworks AI
  val llama_v3p2_3b_instruct = "llama-v3p2-3b-instruct" // Fireworks AI
  val llama_v3p2_11b_vision_instruct = "llama-v3p2-11b-vision-instruct" // Fireworks AI
  val llama_v3p2_90b_vision_instruct = "llama-v3p2-90b-vision-instruct" // Fireworks AI
  val llama_3_2_90b_vision_instruct_turbo =
    "meta-llama/Llama-3.2-90B-Vision-Instruct-Turbo" // Together AI
  val llama_3_2_11b_vision_instruct_turbo =
    "meta-llama/Llama-3.2-11B-Vision-Instruct-Turbo" // Together AI
  val llama_3_2_3b_instruct_turbo = "meta-llama/Llama-3.2-3B-Instruct-Turbo" // Together AI
  val llama_vision_free = "meta-llama/Llama-Vision-Free" // Together AI
  val llama_3_2_1b_preview = "llama-3.2-1b-preview" // Groq
  val llama_3_2_3b_preview = "llama-3.2-3b-preview" // Groq
  @Deprecated
  val llama_3_2_11b_text_preview = "llama-3.2-11b-text-preview" // Groq
  @Deprecated
  val llama_3_2_90b_text_preview = "llama-3.2-90b-text-preview" // Groq
  val llama3_1_8b = "llama3.1-8b" // Cerebras
  val llama3_1_70b = "llama3.1-70b" // Cerebras
  val meta_llama_3_1_405b_instruct = "meta-llama-3.1-405b-instruct" // OctoML
  val meta_llama_3_1_70b_instruct = "meta-llama-3.1-70b-instruct" // OctoML
  val meta_llama_3_1_8b_instruct = "meta-llama-3.1-8b-instruct" // OctoML
  val meta_llama_3_70b_instruct = "meta-llama-3-70b-instruct" // OctoML
  val meta_llama_3_8b_instruct = "meta-llama-3-8b-instruct" // OctoML
  val llama_v3p1_405b_instruct = "llama-v3p1-405b-instruct" // Fireworks AI
  val llama_v3p1_70b_instruct = "llama-v3p1-70b-instruct" // Fireworks AI
  val llama_v3p1_8b_instruct = "llama-v3p1-8b-instruct" // Fireworks AI
  val llama_v3_70b_instruct = "llama-v3-70b-instruct" // Fireworks AI
  val llama_v3_8b_instruct = "llama-v3-8b-instruct" // Fireworks AI
  val llama_3_1_405b_reasoning = "llama-3.1-405b-reasoning" // Groq
  val llama_3_1_70b_versatile = "llama-3.1-70b-versatile" // Groq
  val llama_3_1_8b_instant = "llama-3.1-8b-instant" // Groq
  val llama3_70b_8192 = "llama3-70b-8192" // Groq
  val llama3_8b_8192 = "llama3-8b-8192" // Groq
  val hermes_2_pro_llama_3_8b = "hermes-2-pro-llama-3-8b" // OctoML
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
  val meta_llama_3_1_405b_instruct_turbo =
    "meta-llama/Meta-Llama-3.1-405B-Instruct-Turbo" // Together AI
  val meta_llama_3_1_70b_instruct_turbo =
    "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo" // Together AI
  val meta_llama_3_1_8b_instruct_turbo =
    "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo" // Together AI
  val llama_2_13b_chat_hf = "meta-llama/Llama-2-13b-chat-hf" // Together AI
  val llama_2_70b_chat_hf = "meta-llama/Llama-2-70b-chat-hf" // Together AI
  val llama_2_7b_chat_hf = "meta-llama/Llama-2-7b-chat-hf" // Together AI
  val llama_3_70b_chat_hf = "meta-llama/Llama-3-70b-chat-hf" // Together AI
  val meta_llama_3_70B_instruct_turbo =
    "meta-llama/Meta-Llama-3-70B-Instruct-Turbo" // Together AI
  val llama_3_8b_chat_hf = "meta-llama/Llama-3-8b-chat-hf" // Together AI
  @Deprecated
  val meta_llama_3_70b_instruct_to_ai = "meta-llama/Meta-Llama-3-70B-Instruct" // Together AI
  @Deprecated
  val meta_llama_3_8b_instruct_to_ai = "meta-llama/Meta-Llama-3-8B-Instruct" // Together AI

  // Mistral
  // currently points to mistral-large-2407. mistral-large-2402 will be deprecated shortly.
  val mistral_large_latest = "mistral-large-latest" // Mistral
  val mistral_large_2407 = "mistral-large-2407" // Mistral
  val mistral_large_240 = "mistral-large-240" // Mistral
  // currently points to mistral-medium-2312. The previous mistral-medium has been dated and tagged as mistral-medium-2312. Mistral Medium will be deprecated shortly.
  val mistral_medium_latest = "mistral-medium-latest" // Mistral
  val mistral_medium_2312 = "mistral-medium-2312" // Mistral
  // mistral-small-latest: currently points to mistral-small-2402. Mistral Small will be deprecated shortly.
  val mistral_small_latest = "mistral-small-latest" // Mistral
  val mistral_small_2402 = "mistral-small-2402" // Mistral
  // open-mistral-nemo: currently points to open-mistral-nemo-2407.
  val open_mistral_nemo = "open-mistral-nemo" // Mistral
  val open_mistral_nemo_2407 = "open-mistral-nemo-2407" // Mistral
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
  val nous_hermes_2_mistral_7b_dpo_to_ai =
    "NousResearch/Nous-Hermes-2-Mistral-7B-DPO" // Together AI
  val nous_hermes_2_mixtral_8x7b_dpo_to_ai =
    "NousResearch/Nous-Hermes-2-Mixtral-8x7B-DPO" // Together AI
  val nous_hermes_2_mixtral_8x7b_sft_to_ai =
    "NousResearch/Nous-Hermes-2-Mixtral-8x7B-SFT" // Together AI
  val mistral_large = "mistral-large"
  val mistral_7b_instruct_v0_1 = "mistralai/Mistral-7B-Instruct-v0.1" // Together AI
  val mistral_7b_instruct_v0_2 = "mistralai/Mistral-7B-Instruct-v0.2" // Together AI
  val mistral_7b_instruct_v0_3 = "mistralai/Mistral-7B-Instruct-v0.3" // Together AI
  val mixtral_8x22b_instruct_v0_1 = "mistralai/Mixtral-8x22B-Instruct-v0.1" // Together AI
  val mixtral_8x7b_instruct_v0_1 = "mistralai/Mixtral-8x7B-Instruct-v0.1" // Together AI
  val mistral_7b_openorca = "Open-Orca/Mistral-7B-OpenOrca" // Together AI

  // Gemma
  val gemma2_9b_it = "gemma2-9b-it" // Fireworks AI
  val gemma = "gemma" // Ollama
  val gemma_7b_it = "gemma-7b-it" // Fireworks AI, OctoML, and Groq
  val gemma_2b_it_to_ai = "google/gemma-2b-it" // Together AI
  val gemma_7b_it_to_ai = "google/gemma-7b-it" // Together AI
  val gemma_2_9b_it_to_ai = "google/gemma-2-9b-it" // Together AI

  // Qwen
  val qwen2_vl_72b_instruct = "Qwen/Qwen2-VL-72B-Instruct" // Together AI - vision
  val qwen2_5_72b_instruct_turbo = "Qwen/Qwen2.5-72B-Instruct-Turbo" // Together AI
  val qwen_qwq_32b_preview = "qwen/qwq-32b-preview" // Together AI - reasoning
  val qwen1_5_0_5b_chat = "Qwen/Qwen1.5-0.5B-Chat" // Together AI
  val qwen1_5_1_8b_chat = "Qwen/Qwen1.5-1.8B-Chat" // Together AI
  val qwen1_5_110b_chat = "Qwen/Qwen1.5-110B-Chat" // Together AI
  val qwen1_5_14b_chat = "Qwen/Qwen1.5-14B-Chat" // Together AI
  val qwen1_5_32b_chat = "Qwen/Qwen1.5-32B-Chat" // Together AI
  val qwen1_5_4b_chat = "Qwen/Qwen1.5-4B-Chat" // Together AI
  val qwen1_5_72b_chat = "Qwen/Qwen1.5-72B-Chat" // Together AI
  val qwen1_5_7b_chat = "Qwen/Qwen1.5-7B-Chat" // Together AI
  val qwen2_72b_instruct = "Qwen/Qwen2-72B-Instruct" // Together AI

  // Google Gemini and Vertex AI
  val gemini_2_5_pro_preview_03_25 = "gemini-2.5-pro-preview-03-25"
  val gemini_2_5_pro_exp_03_25 = "gemini-2.5-pro-exp-03-25"
  val gemini_2_0_pro_exp_02_05 = "gemini-2.0-pro-exp-02-05"
  val gemini_2_0_pro_exp = "gemini-2.0-pro-exp"
  val gemini_2_0_flash_thinking_exp_01_21 = "gemini-2.0-flash-thinking-exp-01-21"
  val gemini_2_0_flash_thinking_exp_1219 = "gemini-2.0-flash-thinking-exp-1219"
  val gemini_2_0_flash_thinking_exp = "gemini-2.0-flash-thinking-exp"
  val gemini_2_0_flash_lite_preview_02_05 = "gemini-2.0-flash-lite-preview-02-05"
  val gemini_2_0_flash_lite_preview = "gemini-2.0-flash-lite-preview"
  val gemini_2_0_flash_001 = "gemini-2.0-flash-001"
  val gemini_2_0_flash = "gemini-2.0-flash"
  val gemini_2_0_flash_exp = "gemini-2.0-flash-exp"

  val gemini_1_5_flash_8b_exp_0924 = "gemini-1.5-flash-8b-exp-0924"
  val gemini_1_5_flash_8b_exp_0827 = "gemini-1.5-flash-8b-exp-0827"
  val gemini_1_5_flash_8b_latest = "gemini-1.5-flash-8b-latest"
  val gemini_1_5_flash_8b_001 = "gemini-1.5-flash-8b-001"
  val gemini_1_5_flash_8b = "gemini-1.5-flash-8b"
  val gemini_1_5_flash_002 = "gemini-1.5-flash-002"
  val gemini_1_5_flash = "gemini-1.5-flash"
  val gemini_1_5_flash_001_tuning = "gemini-1.5-flash-001-tuning"
  val gemini_1_5_flash_001 = "gemini-1.5-flash-001"
  val gemini_1_5_flash_latest = "gemini-1.5-flash-latest"
  val gemini_1_5_pro = "gemini-1.5-pro"
  val gemini_1_5_pro_002 = "gemini-1.5-pro-002"
  val gemini_1_5_pro_001 = "gemini-1.5-pro-001"
  val gemini_1_5_pro_latest = "gemini-1.5-pro-latest"

  val gemini_1_0_pro_vision_001 = "gemini-1.0-pro-vision-001"
  val gemini_1_0_pro_vision_latest = "gemini-1.0-pro-vision-latest"
  val gemini_1_0_pro_001 = "gemini-1.0-pro-001"
  val gemini_1_0_pro = "gemini-1.0-pro"
  val gemini_1_0_pro_latest = "gemini-1.0-pro-latest"

  val gemini_pro = "gemini-pro"
  val gemini_pro_vision = "gemini-pro-vision"
  val gemini_exp_1206 = "gemini-exp-1206"
  val gemini_flash_experimental = "gemini-flash-experimental"
  val gemini_pro_experimental = "gemini-pro-experimental"
  val gemini_experimental = "gemini-experimental"
  val text_embedding_004 = "text-embedding-004"

  // Other
  val phi_3_vision_128k_instruct = "phi-3-vision-128k-instruct" // Fireworks AI
  val drbx_instruct = "dbrx-instruct" // Fireworks AI
  val dbrx_instruct_databricks_to_ai = "databricks/dbrx-instruct" // Together AI
  val dbrx_instruct_medaltv = "medaltv/dbrx-instruct" // Together AI
  val firellava_13b = "firellava-13b" // Fireworks AI
  val firefunction_v1 = "firefunction-v1" // Fireworks AI
  val bleat_adapter = "bleat-adapter" // Fireworks AI (completion)
  val smaug_72b_chat = "smaug-72b-chat" // OctoML
  val cohere_command_r_plus = "cohere-command-r-plus"
  val yi_large = "yi-large" // Fireworks AI - accounts/yi-01-ai/models/yi-large
  val nous_hermes_2_yi_34b = "NousResearch/Nous-Hermes-2-Yi-34B" // Together AI
  val yi_34b_chat = "zero-one-ai/Yi-34B-Chat" // Together AI
  val whisper_large_v3 = "whisper-large-v3" // Groq (audio)

  val chronos_hermes_13b = "Austism/chronos-hermes-13b" // Together AI
  val mythomax_l2_13b = "Gryphe/MythoMax-L2-13b" // Together AI
  val nous_capybara_7b_v1p9 = "NousResearch/Nous-Capybara-7B-V1p9" // Together AI
  val nous_hermes_llama2_13b = "NousResearch/Nous-Hermes-Llama2-13b" // Together AI
  val nous_hermes_llama_2_7b = "NousResearch/Nous-Hermes-llama-2-7b" // Together AI
  val snowflake_arctic_instruct = "Snowflake/snowflake-arctic-instruct" // Together AI
  val remm_slerp_l2_13b = "Undi95/ReMM-SLERP-L2-13B" // Together AI
  val toppy_m_7b = "Undi95/Toppy-M-7B" // Together AI
  val wizardlm_13b_v1_2 = "WizardLM/WizardLM-13B-V1.2" // Together AI
  val olmo_7b_instruct = "allenai/OLMo-7B-Instruct" // Together AI
  val codellama_13b_instruct_hf = "codellama/CodeLlama-13b-Instruct-hf" // Together AI
  val codellama_34b_instruct_hf = "codellama/CodeLlama-34b-Instruct-hf" // Together AI
  val codellama_70b_instruct_hf = "codellama/CodeLlama-70b-Instruct-hf" // Together AI
  val codellama_7b_instruct_hf = "codellama/CodeLlama-7b-Instruct-hf" // Together AI
  val dolphin_2_5_mixtral_8x7b =
    "cognitivecomputations/dolphin-2.5-mixtral-8x7b" // Together AI
  val deepseek_coder_33b_instruct = "deepseek-ai/deepseek-coder-33b-instruct" // Together AI
  val deepseek_llm_67b_chat = "deepseek-ai/deepseek-llm-67b-chat" // Together AI
  val platypus2_70b_instruct = "garage-bAInd/Platypus2-70B-instruct" // Together AI
  val vicuna_13b_v1_5 = "lmsys/vicuna-13b-v1.5" // Together AI
  val vicuna_7b_v1_5 = "lmsys/vicuna-7b-v1.5" // Together AI
  val wizardlm_2_8x22b = "microsoft/WizardLM-2-8x22B" // Together AI
  val openchat_3_5_1210 = "openchat/openchat-3.5-1210" // Together AI
  val snorkel_mistral_pairrm_dpo = "snorkelai/Snorkel-Mistral-PairRM-DPO" // Together AI
  val openhermes_2_mistral_7b = "teknium/OpenHermes-2-Mistral-7B" // Together AI
  val openhermes_2p5_mistral_7b = "teknium/OpenHermes-2p5-Mistral-7B" // Together AI
  val stripedhyena_nous_7b = "togethercomputer/StripedHyena-Nous-7B" // Together AI
  val alpaca_7b = "togethercomputer/alpaca-7b" // Together AI
  val solar_10_7b_instruct_v1_0 = "upstage/SOLAR-10.7B-Instruct-v1.0" // Together AI

  // Grok
  // TODO: check these (assumed) names once the models are released
  val grok_3 = "grok_3"
  val grok_3_latest = "grok-3-latest"
  val grok_3_mini = "grok-3-mini"
  val grok_3_reasoning = "grok-3-reasoning"
  val grok_3_mini_reasoning = "grok-3-mini-reasoning"

  // context 131072
  val grok_2_latest = "grok-2-latest"
  val grok_2 = "grok-2"
  val grok_2_1212 = "grok-2-1212"
  // context 131072
  val grok_beta = "grok-beta"
  // context 32768
  val grok_2_vision_latest = "grok-2-vision-latest"
  val grok_2_vision = "grok-2-vision"
  val grok_2_vision_1212 = "grok-2-vision-1212"
  // context 8192
  val grok_vision_beta = "grok-vision-beta"

  // Deepseek
  val deepseek_chat = "deepseek-chat" // Deepseek
  val deepseek_coder = "deepseek-coder" // Deepseek
  val deepseek_reasoner = "deepseek-reasoner" // Deepseek
  val deepseek_r1_distill_llama_70b =
    "deepseek-r1-distill-llama-70b" // Groq, Cerebras and Fireworks
  val deepseek_ai_deepseek_r1_distill_llama_70b_free =
    "deepseek-ai/DeepSeek-R1-Distill-Llama-70B-free" // Together AI
  val deepseek_ai_deepseek_r1_distill_llama_70b =
    "deepseek-ai/DeepSeek-R1-Distill-Llama-70B" // Together AI
  val deepseek_ai_deepseek_r1_distill_qwen_14b =
    "deepseek-ai/DeepSeek-R1-Distill-Qwen-14B" // Together AI
  val deepseek_ai_deepseek_r1_distill_qwen_1_5b =
    "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B" // Together AI
  val deepseek_ai_deepseek_r1 = "deepseek-ai/DeepSeek-R1" // Together AI
  val deepseek_r1 = "deepseek-r1" // Fireworks
  val deepseek_v3 = "deepseek-v3" // Fireworks
  val deepseek_v3_0324 = "deepseek-v3-0324" // Fireworks
  val deepseek_v2_lite_chat = "deepseek-v2-lite-chat" // Fireworks
  val deepseek_ai_deepseek_v3 = "deepseek-ai/DeepSeek-V3" // Together AI

  // Sonar (Perplexity)
  // 128k context length
  val sonar_deep_research = "sonar-deep-research"
  // 128k context length
  val sonar_reasoning_pro = "sonar-reasoning-pro"
  // 128k context length
  val sonar_reasoning = "sonar-reasoning"
  // 200k context length
  val sonar_pro = "sonar-pro"
  // 128k context length
  val sonar = "sonar"
  // 128k context length
  val r1_1776 = "r1-1776"
  // These models will be deprecated and will no longer be available to use after 2/22/2025
  // 127k context window
  val llama_3_1_sonar_small_128k_online = "llama-3.1-sonar-small-128k-online"
  val llama_3_1_sonar_large_128k_online = "llama-3.1-sonar-large-128k-online"
  val llama_3_1_sonar_huge_128k_online = "llama-3.1-sonar-huge-128k-online"

  // Novita
  val novita_deepseek_r1 = "deepseek/deepseek-r1"
  val novita_deepseek_v3 = "deepseek/deepseek_v3"
  val novita_llama_3_3_70b_instruct = "meta-llama/llama-3.3-70b-instruct"
  val novita_deepseek_r1_distill_llama_70b = "deepseek/deepseek-r1-distill-llama-70b"
  val novita_llama_3_1_8b_instruct = "meta-llama/llama-3.1-8b-instruct"
  val novita_llama_3_1_70b_instruct = "meta-llama/llama-3.1-70b-instruct"
  val novita_mistral_nemo = "mistralai/mistral-nemo"
  val novita_deepseek_r1_distill_qwen_14b = "deepseek/deepseek-r1-distill-qwen-14b"
  val novita_deepseek_r1_distill_qwen_32b = "deepseek/deepseek-r1-distill-qwen-32b"
  val novita_l3_8b_stheno_v3_2 = "Sao10K/L3-8B-Stheno-v3.2"
  val novita_mythomax_l2_13b = "gryphe/mythomax-l2-13b"
  val novita_deepseek_r1_distill_llama_8b = "deepseek/deepseek-r1-distill-llama-8b"
  val novita_qwen_2_5_72b_instruct = "qwen/qwen-2.5-72b-instruct"
  val novita_llama_3_8b_instruct = "meta-llama/llama-3-8b-instruct"
  val novita_wizardlm_2_8x22b = "microsoft/wizardlm-2-8x22b"
  val novita_gemma_2_9b_it = "google/gemma-2-9b-it"
  val novita_mistral_7b_instruct = "mistralai/mistral-7b-instruct"
  val novita_llama_3_70b_instruct = "meta-llama/llama-3-70b-instruct"
  val novita_openchat_7b = "openchat/openchat-7b"
  val novita_hermes_2_pro_llama_3_8b = "nousresearch/hermes-2-pro-llama-3-8b"
  val novita_l3_70b_euryale_v2_1 = "sao10k/l3-70b-euryale-v2.1"
  val novita_dolphin_mixtral_8x22b = "cognitivecomputations/dolphin-mixtral-8x22b"
  val novita_airoboros_l2_70b = "jondurbin/airoboros-l2-70b"
  val novita_nous_hermes_llama2_13b = "nousresearch/nous-hermes-llama2-13b"
  val novita_openhermes_2_5_mistral_7b = "teknium/openhermes-2.5-mistral-7b"
  val novita_midnight_rose_70b = "sophosympatheia/midnight-rose-70b"
  val novita_l3_8b_lunaris = "sao10k/l3-8b-lunaris"
  val novita_qwen_2_vl_72b_instruct = "qwen/qwen-2-vl-72b-instruct"
  val novita_llama_3_2_1b_instruct = "meta-llama/llama-3.2-1b-instruct"
  val novita_llama_3_2_11b_vision_instruct = "meta-llama/llama-3.2-11b-vision-instruct"
  val novita_llama_3_2_3b_instruct = "meta-llama/llama-3.2-3b-instruct"
  val novita_llama_3_1_8b_instruct_bf16 = "meta-llama/llama-3.1-8b-instruct-bf16"
  val novita_l31_70b_euryale_v2_2 = "sao10k/l31-70b-euryale-v2.2"
  val novita_qwen_2_7b_instruct = "qwen/qwen-2-7b-instruct"
}
