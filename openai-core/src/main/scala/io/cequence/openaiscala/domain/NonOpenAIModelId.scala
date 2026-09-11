package io.cequence.openaiscala.domain

// TODO: split by providers
object NonOpenAIModelId {

  // Anthropic
  val claude_fable_5_1 = "claude-fable-5-1"
  // Claude Mythos 5.1 / 5: the same underlying models as Fable 5.1 / 5 with fewer cyber/bio
  // safeguards - invite-only (Project Glasswing) on the Claude API, Bedrock, and Vertex AI.
  // Released 2026-09-01 (5.1) and 2026-06-09 (5); same specs/pricing as the Fable twins.
  val claude_mythos_5_1 = "claude-mythos-5-1"
  val claude_mythos_5 = "claude-mythos-5"
  val claude_fable_5 = "claude-fable-5"
  val claude_opus_5 = "claude-opus-5"
  val claude_opus_4_8 = "claude-opus-4-8"
  val claude_opus_4_7 = "claude-opus-4-7"
  val claude_opus_4_6 = "claude-opus-4-6"
  val claude_sonnet_5 = "claude-sonnet-5"
  val claude_sonnet_4_6 = "claude-sonnet-4-6"
  val claude_opus_4_5_20251101 = "claude-opus-4-5-20251101"
  val claude_opus_4_5 = "claude-opus-4-5"
  val claude_sonnet_4_5_20250929 = "claude-sonnet-4-5-20250929"
  val claude_sonnet_4_5 = "claude-sonnet-4-5"
  val claude_haiku_4_5_20251001 = "claude-haiku-4-5-20251001"
  val claude_haiku_4_5 = "claude-haiku-4-5"
  val claude_opus_4_1_20250805 = "claude-opus-4-1-20250805"
  val claude_opus_4_20250514 = "claude-opus-4-20250514"
  val claude_sonnet_4_20250514 = "claude-sonnet-4-20250514"
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
  val bedrock_claude_fable_5_1 = "anthropic.claude-fable-5-1"
  // invite-only; served on the bedrock-mantle `/anthropic/v1/messages` endpoint only
  val bedrock_claude_mythos_5_1 = "anthropic.claude-mythos-5-1"
  val bedrock_claude_mythos_5 = "anthropic.claude-mythos-5"
  val bedrock_claude_fable_5 = "anthropic.claude-fable-5"
  val bedrock_claude_opus_5 = "anthropic.claude-opus-5"
  val bedrock_claude_opus_4_8 = "anthropic.claude-opus-4-8"
  val bedrock_claude_opus_4_7 = "anthropic.claude-opus-4-7"
  val bedrock_claude_opus_4_6_v1 = "anthropic.claude-opus-4-6-v1"
  val bedrock_claude_sonnet_5 = "anthropic.claude-sonnet-5"
  val bedrock_claude_sonnet_4_6 = "anthropic.claude-sonnet-4-6"
  val bedrock_claude_haiku_4_5 = "anthropic.claude-haiku-4-5"
  val bedrock_claude_opus_4_5_20251101_v1_0 = "anthropic.claude-opus-4-5-20251101-v1:0"
  val bedrock_claude_sonnet_4_5_20250929_v1_0 = "anthropic.claude-sonnet-4-5-20250929-v1:0"
  val bedrock_claude_haiku_4_5_20251001_v1_0 = "anthropic.claude-haiku-4-5-20251001-v1:0"
  val bedrock_claude_opus_4_1_20250805_v1_0 = "anthropic.claude-opus-4-1-20250805-v1:0"
  val bedrock_claude_opus_4_20250514_v1_0 = "anthropic.claude-opus-4-20250514-v1:0"
  val bedrock_claude_sonnet_4_20250514_v1_0 = "anthropic.claude-sonnet-4-20250514-v1:0"
  val bedrock_claude_3_7_sonnet_20250219_v1_0 = "anthropic.claude-3-7-sonnet-20250219-v1:0"
  val bedrock_claude_3_5_sonnet_20241022_v2_0 = "anthropic.claude-3-5-sonnet-20241022-v2:0"
  val bedrock_claude_3_5_sonnet_20240620_v1_0 = "anthropic.claude-3-5-sonnet-20240620-v1:0"
  val bedrock_claude_3_5_haiku_20241022_v1_0 = "anthropic.claude-3-5-haiku-20241022-v1:0"
  val bedrock_claude_3_opus_20240229_v1_0 = "anthropic.claude-3-opus-20240229-v1:0"
  val bedrock_claude_3_sonnet_20240229_v1_0 = "anthropic.claude-3-sonnet-20240229-v1:0"
  val bedrock_claude_3_haiku_20240307_v1_0 = "anthropic.claude-3-haiku-20240307-v1:0"

  // OpenAI (Bedrock - bedrock-mantle endpoint, OpenAI Responses API)
  // GPT-5.6 Sol/Terra/Luna (Bedrock launch 2026-07-13, 1M context); `openai.gpt-5.6-sol` is
  // verified against the AWS model card, Terra/Luna follow the same naming. On bedrock-runtime
  // use the `us.`/`global.` cross-region inference-profile prefix instead.
  val bedrock_openai_gpt_5_6_sol = "openai.gpt-5.6-sol"
  val bedrock_openai_gpt_5_6_terra = "openai.gpt-5.6-terra"
  val bedrock_openai_gpt_5_6_luna = "openai.gpt-5.6-luna"
  val bedrock_openai_gpt_5_5 = "openai.gpt-5.5"
  val bedrock_openai_gpt_5_4 = "openai.gpt-5.4"
  val bedrock_openai_gpt_oss_120b = "openai.gpt-oss-120b"
  val bedrock_openai_gpt_oss_20b = "openai.gpt-oss-20b"

  // Other providers (Bedrock - bedrock-mantle endpoint, `/v1/chat/completions`)
  // xAI Grok on Bedrock: served from the OpenAI-style `/openai/v1` base path (like the OpenAI
  // models above) - use `forBedrockMantle(isOpenAIModel = true)`. Grok 4.6: 500K context,
  // reasoning low/medium/high/xhigh; Grok 4.3: 1M context, reasoning none/low/medium/high.
  val bedrock_xai_grok_4_6 = "xai.grok-4.6"
  val bedrock_xai_grok_4_3 = "xai.grok-4.3"
  // Gemma 4 (bedrock-mantle only, `/openai/v1` base path)
  val bedrock_google_gemma_4_31b = "google.gemma-4-31b"
  val bedrock_google_gemma_4_26b_a4b = "google.gemma-4-26b-a4b"
  // standard `/v1` base path
  val bedrock_moonshotai_kimi_k2_5 = "moonshotai.kimi-k2.5"
  val bedrock_deepseek_v3_2 = "deepseek.v3.2"
  val bedrock_minimax_m2_5 = "minimax.minimax-m2.5"
  val bedrock_zai_glm_4_7_flash = "zai.glm-4.7-flash"
  val bedrock_google_gemma_3_27b_it = "google.gemma-3-27b-it"
  val bedrock_qwen_qwen3_coder_next = "qwen.qwen3-coder-next"
  val bedrock_qwen_qwen3_235b_a22b_2507 = "qwen.qwen3-235b-a22b-2507"
  val bedrock_mistral_mistral_large_3_675b_instruct = "mistral.mistral-large-3-675b-instruct"
  val bedrock_zai_glm_5 = "zai.glm-5"
  val bedrock_nvidia_nemotron_nano_3_30b = "nvidia.nemotron-nano-3-30b"
  val bedrock_mistral_ministral_3_8b_instruct = "mistral.ministral-3-8b-instruct"

  // Nova (Bedrock)
  val amazon_nova_pro_v1_0 = "amazon.nova-pro-v1:0"
  val amazon_nova_lite_v1_0 = "amazon.nova-lite-v1:0"
  val amazon_nova_micro_v1_0 = "amazon.nova-micro-v1:0"

  // Llama

  // 17B x 128E, 400B total params, 1M context window (500k currently supported)
  val meta_llama_llama_4_maverick_17b_128e_instruct_fp8 =
    "meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8" // Together AI

  // 17B x 16E, 109B total params, 10M token context (300k currently supported)
  val meta_llama_llama_4_scout_17b_1eE_instruct =
    "meta-llama/Llama-4-Scout-17B-16E-Instruct" // Together AI

  // 17B x 128E, 400B params, 1 mil context
  val llama4_maverick_instruct_basic = "llama4-maverick-instruct-basic" // Fireworks AI
  // 17B x 16E, 107B params, 128k context
  val llama4_scout_instruct_basic = "llama4-scout-instruct-basic" // Fireworks AI
  // 17B x 16E, 107B params
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val groq_llama_4_scout_17b_16e_instruct = "meta-llama/llama-4-scout-17b-16e-instruct" // Groq
  val groq_llama_4_maverick_17b_128e_instruct =
    "meta-llama/llama-4-maverick-17b-128e-instruct" // Groq

  // 17B x 16E, 107B params
  // Cerebras public endpoints (2026-09) serve only gpt-oss-120b and qwen-3.8-27b; everything
  // else below tagged "Cerebras" has been retired there (see
  // https://inference-docs.cerebras.ai/support/deprecation).
  // Qwen 3.8 27B on Cerebras: 128K context / 40K output (paid; 64K / 32K free), tool calling,
  // strict structured outputs, image input (base64 PNG/JPEG), reasoning on by default
  // (`reasoning_effort = none` disables it)
  val cerebras_qwen_3_8_27b = "qwen-3.8-27b" // Cerebras
  @Deprecated // deprecated on Cerebras 2026-09-03 (use qwen-3.8-27b); still served 2026-09-10
  val cerebras_gemma_4_31b = "gemma-4-31b" // Cerebras
  @Deprecated // retired on Cerebras 2025-11-03 (use gpt-oss-120b / qwen-3.8-27b)
  val cerebras_llama_4_scout_17b_16e_instruct = "llama-4-scout-17b-16e-instruct" // Cerebras

  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama_3_3_70b_versatile = "llama-3.3-70b-versatile" // Groq
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama_3_3_70b_specdec = "llama-3.3-70b-specdec" // Groq
  val llama_v3p3_70b_instruct = "llama-v3p3-70b-instruct" // Fireworks AI
  val llama_3_3_70B_instruct_turbo = "meta-llama/Llama-3.3-70B-Instruct-Turbo" // Together AI
  val llama_3_3_70B_instruct_turbo_free =
    "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free" // Together AI
  @Deprecated // retired on Cerebras 2026-02-16 (use gpt-oss-120b)
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
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama_3_2_1b_preview = "llama-3.2-1b-preview" // Groq
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama_3_2_3b_preview = "llama-3.2-3b-preview" // Groq
  @Deprecated
  val llama_3_2_11b_text_preview = "llama-3.2-11b-text-preview" // Groq
  @Deprecated
  val llama_3_2_90b_text_preview = "llama-3.2-90b-text-preview" // Groq
  @Deprecated // retired on Cerebras 2026-05-27 (use gpt-oss-120b)
  val llama3_1_8b = "llama3.1-8b" // Cerebras
  @Deprecated // retired on Cerebras 2025-01-17
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
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama_3_1_405b_reasoning = "llama-3.1-405b-reasoning" // Groq
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama_3_1_70b_versatile = "llama-3.1-70b-versatile" // Groq
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama_3_1_8b_instant = "llama-3.1-8b-instant" // Groq
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama3_70b_8192 = "llama3-70b-8192" // Groq
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val llama3_8b_8192 = "llama3-8b-8192" // Groq
  val hermes_2_pro_llama_3_8b = "hermes-2-pro-llama-3-8b" // OctoML
  val llama2 = "llama2" // Ollama
  val llama_2_7b_chat = "llama-2-7b-chat"
  val llama_v2_7b_chat = "llama-v2-7b-chat" // Fireworks AI
  val llama_2_13b_chat = "llama-2-13b-chat" // OctoML
  val llama_v2_13b_chat = "llama-v2-13b-chat" // Fireworks AI
  val llama_2_70b_chat = "llama-2-70b-chat" // OctoML
  val llama_v2_70b_chat = "llama-v2-70b-chat" // Fireworks AI
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
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
  val groq_llama_prompt_guard_2_22m = "meta-llama/llama-prompt-guard-2-22m" // Groq
  val groq_llama_prompt_guard_2_86m = "meta-llama/llama-prompt-guard-2-86m" // Groq
  // Groq (preview) - Qwen 3.8 / 3.6 27B dense, MiniMax M2.7, and Groq's agentic compound systems
  val groq_qwen3_8_27b = "qwen/qwen3.8-27b" // Groq
  val groq_qwen3_6_27b = "qwen/qwen3.6-27b" // Groq
  val groq_compound =
    "groq/compound" // Groq (agentic system with built-in web search / code exec)
  val groq_compound_mini = "groq/compound-mini" // Groq
  val groq_allam_2_7b = "allam-2-7b" // Groq (Arabic)
  val groq_orpheus_v1_english = "canopylabs/orpheus-v1-english" // Groq (TTS)
  val groq_orpheus_arabic_saudi = "canopylabs/orpheus-arabic-saudi" // Groq (TTS)

  // Mistral
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val mistral_saba_24b = "mistral-saba-24b" // Groq
  // Mistral - ids as listed by `GET /v1/models` on 2026-09-10; the `*-latest` aliases are
  // rolling pointers
  val mistral_large_2512 = "mistral-large-2512" // Mistral Large 3
  val mistral_medium_2604 = "mistral-medium-2604" // Mistral Medium 3.5
  val mistral_medium_3_5 = "mistral-medium-3.5" // Mistral (alias of mistral-medium-2604)
  // not in the /models listing any more but still resolves (live-verified 2026-09-10)
  val mistral_medium_2508 = "mistral-medium-2508" // Mistral Medium 3.1
  val mistral_small_2603 = "mistral-small-2603" // Mistral Small 4
  val ministral_14b_2512 = "ministral-14b-2512" // Ministral 3 14B
  val ministral_8b_2512 = "ministral-8b-2512" // Ministral 3 8B
  val ministral_3b_2512 = "ministral-3b-2512" // Ministral 3 3B
  val ministral_14b_latest = "ministral-14b-latest" // Mistral (rolling alias)
  val ministral_8b_latest = "ministral-8b-latest" // Mistral (rolling alias)
  val ministral_3b_latest = "ministral-3b-latest" // Mistral (rolling alias)
  val magistral_medium_latest = "magistral-medium-latest" // Mistral (reasoning)
  val magistral_small_latest = "magistral-small-latest" // Mistral (reasoning)
  val codestral_2508 = "codestral-2508" // Mistral (code)
  val codestral_latest = "codestral-latest" // Mistral (rolling alias)
  val mistral_code_latest = "mistral-code-latest" // Mistral (code)
  val mistral_code_fim_latest = "mistral-code-fim-latest" // Mistral (fill-in-the-middle)
  val mistral_vibe_cli_latest = "mistral-vibe-cli-latest" // Mistral (coding agent)
  val mistral_vibe_cli_fast = "mistral-vibe-cli-fast" // Mistral (coding agent)
  val mistral_vibe_cli_with_tools = "mistral-vibe-cli-with-tools" // Mistral (coding agent)
  val mistral_zai_glm_5_2 = "zai-glm-5-2" // Mistral-hosted Z.ai GLM 5.2 (also "glm-5-2")
  val labs_leanstral_1_5 =
    "labs-leanstral-1-5" // Mistral (Lean 4 proof engineering; retires 2026-09-30)
  val mistral_ocr_4_1 = "mistral-ocr-4-1" // Mistral OCR 4.1 (GA 2026-08-31)
  val mistral_ocr_4_0 = "mistral-ocr-4-0" // Mistral OCR 4.0
  val mistral_ocr_2512 = "mistral-ocr-2512" // Mistral OCR 3
  val mistral_ocr_latest = "mistral-ocr-latest" // Mistral (rolling alias)
  val mistral_embed = "mistral-embed" // Mistral (embeddings)
  val codestral_embed = "codestral-embed" // Mistral (code embeddings)
  val mistral_moderation_2603 = "mistral-moderation-2603" // Mistral (moderation)
  val voxtral_small_2507 = "voxtral-small-2507" // Mistral (audio understanding)
  val voxtral_mini_2602 = "voxtral-mini-2602" // Mistral (audio transcription)
  val voxtral_mini_realtime_2602 = "voxtral-mini-realtime-2602" // Mistral (realtime audio)
  val voxtral_mini_transcribe_realtime_2602 =
    "voxtral-mini-transcribe-realtime-2602" // Mistral (realtime transcription)
  val voxtral_mini_tts_2603 = "voxtral-mini-tts-2603" // Mistral (text-to-speech)
  // rolling alias (resolves to the current Mistral Large)
  val mistral_large_latest = "mistral-large-latest" // Mistral
  @Deprecated // not listed by the Mistral models endpoint as of 2026-09-10
  val mistral_large_2407 = "mistral-large-2407" // Mistral
  @Deprecated // not listed by the Mistral models endpoint as of 2026-09-10
  val mistral_large_240 = "mistral-large-240" // Mistral
  // rolling alias (resolves to the current Mistral Medium)
  val mistral_medium_latest = "mistral-medium-latest" // Mistral
  @Deprecated // not listed by the Mistral models endpoint as of 2026-09-10
  val mistral_medium_2312 = "mistral-medium-2312" // Mistral
  // rolling alias (resolves to the current Mistral Small)
  val mistral_small_latest = "mistral-small-latest" // Mistral
  @Deprecated // not listed by the Mistral models endpoint as of 2026-09-10
  val mistral_small_2402 = "mistral-small-2402" // Mistral
  // open-mistral-nemo: currently points to open-mistral-nemo-2407.
  @Deprecated // not listed by the Mistral models endpoint as of 2026-09-10
  val open_mistral_nemo = "open-mistral-nemo" // Mistral
  @Deprecated // not listed by the Mistral models endpoint as of 2026-09-10
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
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
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

  // Zhipu AI / GLM
  val glm_4_6 = "glm-4.6"
  val glm_4_7 = "glm-4.7"
  val zhipu_glm_4_6 = "zhipu/glm-4.6"
  val zhipu_glm_4_7 = "zhipu/glm-4.7"

  // Google Gemini and Vertex AI

  // rolling aliases (resolve to the current stable model, e.g. gemini-flash-latest -> gemini-3.7-flash as of 2026-09)
  val gemini_flash_latest = "gemini-flash-latest"
  val gemini_flash_lite_latest = "gemini-flash-lite-latest"
  val gemini_pro_latest = "gemini-pro-latest"

  val gemini_3_1_pro = "gemini-3.1-pro"
  val gemini_3_1_pro_preview = "gemini-3.1-pro-preview"
  val gemini_3_1_pro_preview_customtools = "gemini-3.1-pro-preview-customtools"
  // Input token limit: 1048576; Output token limit: 65536
  val gemini_3_flash_preview = "gemini-3-flash-preview"
  // Input token limit: 1048576; Output token limit: 65536
  val gemini_3_pro = "gemini-3-pro"
  val gemini_3_pro_preview = "gemini-3-pro-preview"
  val gemini_3_pro_image = "gemini-3-pro-image"
  val gemini_3_pro_image_preview = "gemini-3-pro-image-preview"
  val gemini_2_5_pro = "gemini-2.5-pro"
  val gemini_2_5_pro_preview_06_05 = "gemini-2.5-pro-preview-06-05"
  val gemini_2_5_pro_preview_05_06 = "gemini-2.5-pro-preview-05-06"
  val gemini_2_5_pro_preview_03_25 = "gemini-2.5-pro-preview-03-25"
  val gemini_2_5_pro_exp_03_25 = "gemini-2.5-pro-exp-03-25"

  // Input token limit: 1048576; Output token limit: 65536
  // Gemini 3.8 Flash (GA 2026-09-02): 1,048,576 context, 65,536 max output; thinking levels
  // LOW/MEDIUM/HIGH only (MINIMAL returns an error, like 3.7 Flash); structured outputs,
  // function calling, and Batch API supported
  val gemini_3_8_flash = "gemini-3.8-flash"
  val gemini_3_7_flash = "gemini-3.7-flash"
  val gemini_3_6_flash = "gemini-3.6-flash"
  val gemini_3_5_flash = "gemini-3.5-flash"
  val gemini_3_5_flash_lite = "gemini-3.5-flash-lite"
  val gemini_3_1_flash_lite = "gemini-3.1-flash-lite"
  val gemini_3_1_flash_lite_preview = "gemini-3.1-flash-lite-preview"
  val gemini_3_1_flash_image = "gemini-3.1-flash-image"
  val gemini_3_1_flash_image_preview = "gemini-3.1-flash-image-preview"
  val gemini_3_1_flash_lite_image = "gemini-3.1-flash-lite-image"

  // Gemini TTS / transcription / live (audio)
  val gemini_2_5_flash_preview_tts = "gemini-2.5-flash-preview-tts"
  val gemini_2_5_pro_preview_tts = "gemini-2.5-pro-preview-tts"
  val gemini_3_1_flash_tts_preview = "gemini-3.1-flash-tts-preview"
  val gemini_3_5_transcribe = "gemini-3.5-transcribe"
  val gemini_3_5_transcribe_live = "gemini-3.5-transcribe-live"
  val gemini_3_5_live_translate_preview = "gemini-3.5-live-translate-preview"
  val gemini_3_1_flash_live_preview = "gemini-3.1-flash-live-preview"
  val gemini_2_5_flash_native_audio_latest = "gemini-2.5-flash-native-audio-latest"
  val gemini_2_5_flash_native_audio_preview_09_2025 =
    "gemini-2.5-flash-native-audio-preview-09-2025"
  val gemini_2_5_flash_native_audio_preview_12_2025 =
    "gemini-2.5-flash-native-audio-preview-12-2025"

  // Gemini specialised (robotics / computer use)
  val gemini_robotics_er_2_preview = "gemini-robotics-er-2-preview"
  val gemini_robotics_er_2_streaming_preview = "gemini-robotics-er-2-streaming-preview"
  val gemini_2_5_computer_use_preview_10_2025 = "gemini-2.5-computer-use-preview-10-2025"

  // Gemini Omni (native multimodal)
  val gemini_omni_1_1_flash = "gemini-omni-1.1-flash"
  val gemini_omni_flash_preview = "gemini-omni-flash-preview"
  // Other models served by the Gemini API (listed 2026-09-10)
  val gemma_4_31b_it = "gemma-4-31b-it" // Gemini API
  val gemma_4_26b_a4b_it = "gemma-4-26b-a4b-it" // Gemini API
  val deep_research_preview_04_2026 = "deep-research-preview-04-2026" // Gemini API (agent)
  val deep_research_max_preview_04_2026 =
    "deep-research-max-preview-04-2026" // Gemini API (agent)
  val deep_research_pro_preview_12_2025 =
    "deep-research-pro-preview-12-2025" // Gemini API (agent)
  val antigravity_preview_05_2026 = "antigravity-preview-05-2026" // Gemini API (agent)
  val nano_banana_pro_preview = "nano-banana-pro-preview" // Gemini API (image)
  val veo_3_1_generate_preview = "veo-3.1-generate-preview" // Gemini API (video)
  val veo_3_1_fast_generate_preview = "veo-3.1-fast-generate-preview" // Gemini API (video)
  val veo_3_1_lite_generate_preview = "veo-3.1-lite-generate-preview" // Gemini API (video)
  val lyria_3_5 = "lyria-3.5" // Gemini API (music)

  val gemini_2_5_flash = "gemini-2.5-flash"
  val gemini_2_5_flash_lite = "gemini-2.5-flash-lite"
  val gemini_2_5_flash_image = "gemini-2.5-flash-image"
  val gemini_2_5_flash_live_api = "gemini-2.5-flash-live-api"
  val gemini_2_5_flash_preview_04_17_thinking = "gemini-2.5-flash-preview-04-17-thinking"
  val gemini_2_5_flash_preview_04_17 = "gemini-2.5-flash-preview-04-17"
  val gemini_2_5_flash_preview_05_20 = "gemini-2.5-flash-preview-05-20"

  val gemini_2_0_pro_exp_02_05 = "gemini-2.0-pro-exp-02-05"
  val gemini_2_0_pro_exp = "gemini-2.0-pro-exp"
  val gemini_2_0_flash_thinking_exp_01_21 = "gemini-2.0-flash-thinking-exp-01-21"
  val gemini_2_0_flash_thinking_exp_1219 = "gemini-2.0-flash-thinking-exp-1219"
  val gemini_2_0_flash_thinking_exp = "gemini-2.0-flash-thinking-exp"
  val gemini_2_0_flash_lite_preview_02_05 = "gemini-2.0-flash-lite-preview-02-05"
  val gemini_2_0_flash_lite_preview = "gemini-2.0-flash-lite-preview"
  val gemini_2_0_flash_lite = "gemini-2.0-flash-lite"
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
  val gemini_embedding_2 = "gemini-embedding-2"
  val gemini_embedding_2_preview = "gemini-embedding-2-preview"
  val gemini_embedding_001 = "gemini-embedding-001"

  // Minimax

  // MiniMax M2.x - MoE 230B total / 10B active, coding & agentic
  // Fireworks AI uses short form (full path: accounts/fireworks/models/minimax-m2*)
  val minimax_m2 = "minimax-m2" // Fireworks AI
  val minimax_m2p1 = "minimax-m2p1" // Fireworks AI
  val minimax_m2p5 = "minimax-m2p5" // Fireworks AI
  val minimax_m2p7 = "minimax-m2p7" // Fireworks AI
  val minimaxai_minimax_m2 = "MiniMaxAI/MiniMax-M2" // Together AI
  val minimaxai_minimax_m2_1 = "MiniMaxAI/MiniMax-M2.1" // Together AI
  val minimaxai_minimax_m2_5 = "MiniMaxAI/MiniMax-M2.5" // Together AI
  val minimaxai_minimax_m2_7 = "MiniMaxAI/MiniMax-M2.7" // Together AI
  val sambanova_minimax_m2 = "MiniMax-M2" // SambaNova
  val sambanova_minimax_m2_5 = "MiniMax-M2.5" // SambaNova
  val sambanova_minimax_m2_7 = "MiniMax-M2.7" // SambaNova
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val moonshotai_kimi_k2_instruct = "moonshotai/kimi-k2-instruct" // Groq
  // context 262,144
  val moonshotai_kimi_k2_instruct_0905 = "moonshotai/kimi-k2-instruct-0905"

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
  val whisper_large_v3_turbo = "whisper-large-v3-turbo" // Groq (audio)

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
  // Grok 4.6 (2026-08-12): 500K context, knowledge cutoff 2026-02-01, reasoning effort
  // low/medium/high/xhigh - xAI's current flagship for coding and agentic work
  val grok_4_6 = "grok-4.6"
  // Grok 4.5 (2026-07): 500K context, coding / agent workflows
  val grok_4_5 = "grok-4.5"
  val grok_4_5_latest = "grok-4.5-latest"
  // Grok Build 0.1: 256K context, xAI's coding-agent model
  val grok_build_0_1 = "grok-build-0.1"
  // context 1,000,000
  val grok_4_3 = "grok-4.3"
  val grok_4_3_latest = "grok-4.3-latest"
  val grok_4_20_0309_reasoning = "grok-4.20-0309-reasoning"
  val grok_4_20_0309_non_reasoning = "grok-4.20-0309-non-reasoning"
  val grok_4_20_multi_agent_0309 = "grok-4.20-multi-agent-0309"
  // stable aliases of the dated 4.20 ids above (live-verified 2026-09-11)
  val grok_4_20 = "grok-4.20" // -> grok-4.20-0309-reasoning
  val grok_4_20_non_reasoning = "grok-4.20-non-reasoning"
  // multi-agent is rejected by the chat completions API ("Multi Agent requests are not
  // allowed on chat completions") - use it through the Responses API
  val grok_4_20_multi_agent = "grok-4.20-multi-agent"
  // Grok image / video gen
  val grok_imagine_image = "grok-imagine-image"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_imagine_image_pro = "grok-imagine-image-pro"
  val grok_imagine_image_quality = "grok-imagine-image-quality"
  val grok_imagine_video = "grok-imagine-video"
  val grok_imagine_image_2_0 = "grok-imagine-image-2.0" // recommended for images
  val grok_imagine_video_1_5 = "grok-imagine-video-1.5" // recommended for videos
  // Grok voice (speech-to-speech)
  val grok_voice_think_fast_2_0 = "grok-voice-think-fast-2.0"
  @Deprecated
  val grok_voice_think_fast_1_0 = "grok-voice-think-fast-1.0"
  // context 2,000,000
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_4_1_fast_reasoning = "grok-4-1-fast-reasoning"
  // context 2,000,000
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_4_1_fast_non_reasoning = "grok-4-1-fast-non-reasoning"
  // context 256,000
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_4 = "grok-4"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_4_latest = "grok-4-latest"
  // context 256,000
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_4_0709 = "grok-4-0709"
  // context 2,000,000
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_4_fast_reasoning = "grok-4-fast-reasoning"
  // context 2,000,000
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_4_fast_non_reasoning = "grok-4-fast-non-reasoning"
  // context 256,000
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_code_fast_1 = "grok-code-fast-1"
  // context 131,072
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_beta = "grok-3-beta"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3 = "grok-3"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_latest = "grok-3-latest"

  // context 131,072
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_fast_beta = "grok-3-fast-beta"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_fast = "grok-3-fast"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_fast_latest = "grok-3-fast-latest"

  // context 131,072
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_mini_beta = "grok-3-mini-beta"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_mini = "grok-3-mini"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_mini_latest = "grok-3-mini-latest"

  // context 131,072
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_mini_fast_beta = "grok-3-mini-fast-beta"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_mini_fast = "grok-3-mini-fast"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_3_mini_fast_latest = "grok-3-mini-fast-latest"

  // context 131072
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_2_latest = "grok-2-latest"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_2 = "grok-2"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_2_1212 = "grok-2-1212"
  // context 131072
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_beta = "grok-beta"
  // context 32768
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_2_vision_latest = "grok-2-vision-latest"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_2_vision = "grok-2-vision"
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_2_vision_1212 = "grok-2-vision-1212"
  // context 8192
  @Deprecated // not listed by the xAI models endpoint as of 2026-09-10
  val grok_vision_beta = "grok-vision-beta"

  // Deepseek
  // DeepSeek V4 (direct API). `GET /models` lists `deepseek-flash` and `deepseek-v4-pro`
  // (2026-09-10); `deepseek-v4-flash` (-> DeepSeek-V4-Flash-0731) still resolves as an alias,
  // `deepseek-v4-pro` -> DeepSeek-V4-Pro-0813. Thinking vs non-thinking is a request mode, not
  // a separate model id anymore.
  val deepseek_flash = "deepseek-flash" // Deepseek
  val deepseek_v4_flash = "deepseek-v4-flash" // Deepseek
  val deepseek_v4_pro = "deepseek-v4-pro" // Deepseek
  val deepseek_v4_flash_vision_exp =
    "deepseek-v4-flash-vision-exp" // Deepseek (experimental, image input)
  // Legacy aliases: DeepSeek announced their retirement for 2026-07-24, but `deepseek-chat`
  // still resolved to V4 Flash on 2026-09-10 - don't rely on it, use the V4 ids above.
  @Deprecated // retirement announced (2026-07-24), use deepseek_v4_flash / deepseek_flash
  val deepseek_chat = "deepseek-chat" // Deepseek
  @Deprecated // legacy
  val deepseek_coder = "deepseek-coder" // Deepseek
  @Deprecated // retirement announced (2026-07-24), use deepseek_v4_flash (thinking mode)
  val deepseek_reasoner = "deepseek-reasoner" // Deepseek
  val deepseek_r1_distill_llama_70b =
    "deepseek-r1-distill-llama-70b" // Groq and Fireworks (retired on Cerebras 2025-08-12)
  @Deprecated // not listed by the Groq models endpoint as of 2026-09-10
  val deepseek_r1_distill_qwen_32b = "deepseek-r1-distill-qwen-32b" // Groq
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
  val deepseek_ai_deepseek_v4_pro = "deepseek-ai/DeepSeek-V4-Pro" // Together AI
  val deepseek_ai_deepseek_v4_pro_0813 = "deepseek-ai/DeepSeek-V4-Pro-0813" // Together AI
  val deepseek_ai_deepseek_v4_flash_0731 = "deepseek-ai/DeepSeek-V4-Flash-0731" // Together AI
  // DeepSeek V4.1 Flash - reasoning model, 1M context, tools + image input + strict json_schema
  // (live-verified 2026-09-11). Fireworks spells decimals with "p" (cf. glm-5p2, qwen3p8-max);
  // prepend "accounts/fireworks/models/" as the other Fireworks ids here do. Novita's form is
  // `novita_deepseek_v4_1_flash` below. Not served by Together AI, DeepSeek's own API,
  // SambaNova, Cerebras, Groq or Bedrock, whose newest DeepSeek is V3.2.
  val deepseek_v4p1_flash = "deepseek-v4p1-flash" // Fireworks
  // Together AI - other 2026 open-weight flagships (listed 2026-09-10)
  val qwen_qwen3_8_flash = "Qwen/Qwen3.8-Flash" // Together AI
  val qwen_qwen3_8_2_4t_a95b = "Qwen/Qwen3.8-2.4T-A95B" // Together AI
  val qwen_qwen3_6_plus = "Qwen/Qwen3.6-Plus" // Together AI
  val moonshotai_kimi_k3 = "moonshotai/Kimi-K3" // Together AI
  val moonshotai_kimi_k2_7_code = "moonshotai/Kimi-K2.7-Code" // Together AI
  val moonshotai_kimi_k2_6 = "moonshotai/Kimi-K2.6" // Together AI
  val zai_org_glm_5_3 = "zai-org/GLM-5.3" // Together AI
  val zai_org_glm_5_3_flash = "zai-org/GLM-5.3-Flash" // Together AI
  val zai_org_glm_5_2 = "zai-org/GLM-5.2" // Together AI
  val zai_org_glm_5_1 = "zai-org/GLM-5.1" // Together AI
  val zai_org_glm_5 = "zai-org/GLM-5" // Together AI
  val minimaxai_minimax_m3 = "MiniMaxAI/MiniMax-M3" // Together AI
  val google_gemma_4_31b_it = "google/gemma-4-31B-it" // Together AI
  val google_gemma_4_26b_a4b_it = "google/gemma-4-26B-A4B-it" // Together AI
  val nvidia_nemotron_3_super_120b_a12b_fp8 =
    "nvidia/NVIDIA-Nemotron-3-Super-120B-A12B-FP8" // Together AI
  val nvidia_nemotron_3_ultra_550b_a55b = "nvidia/nemotron-3-ultra-550b-a55b" // Together AI

  // openai oss
  val gpt_oss_20b = "gpt-oss-20b" // fireworks
  val gpt_oss_120b = "gpt-oss-120b" // Fireworks, Cerebras
  val openai_gpt_oss_120b = "openai/gpt-oss-120b" // groq, Together AI, Novita
  val openai_gpt_oss_20b = "openai/gpt-oss-20b" // groq, Together AI, Novita
  // context 131,072
  val openai_gpt_oss_safeguard_20b = "openai/gpt-oss-safeguard-20b"

  // Sonar (Perplexity)
  // NOTE: Perplexity is retiring the Sonar Chat Completions endpoint (`/chat/completions`) on
  // 2026-09-27 in favour of its Agent API (`POST /v1/agent`, Responses-style contract). The
  // model ids below stay valid on the Agent API, but this library's SonarService/asOpenAI adapter
  // targets the chat-completions contract.
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
  // Novita - 2026 additions (listed 2026-09-10)
  // the only spelling Novita accepts - the `deepseek/` prefix is mandatory and `v4p1` is
  // rejected there (Fireworks is the mirror image: see `deepseek_v4p1_flash`)
  val novita_deepseek_v4_1_flash = "deepseek/deepseek-v4.1-flash"
  val novita_deepseek_v4_flash = "deepseek/deepseek-v4-flash"
  val novita_deepseek_v4_flash_vision_exp = "deepseek/deepseek-v4-flash-vision-exp"
  val novita_deepseek_v4_pro = "deepseek/deepseek-v4-pro"
  val novita_deepseek_v3_2 = "deepseek/deepseek-v3.2"
  val novita_qwen3_8_max = "qwen/qwen3.8-max"
  val novita_qwen3_8_flash = "qwen/qwen3.8-flash"
  val novita_qwen3_8_2_4t_a95b = "qwen/qwen3.8-2.4t-a95b"
  val novita_qwen3_6_plus = "qwen/qwen3.6-plus"
  val novita_kimi_k3 = "moonshotai/kimi-k3"
  val novita_kimi_k2_7_code = "moonshotai/kimi-k2.7-code"
  val novita_kimi_k2_6 = "moonshotai/kimi-k2.6"
  val novita_kimi_k2_5 = "moonshotai/kimi-k2.5"
  val novita_glm_5_3 = "zai-org/glm-5.3"
  val novita_glm_5_3_flash = "zai-org/glm-5.3-flash"
  val novita_glm_5_2 = "zai-org/glm-5.2"
  val novita_glm_5 = "zai-org/glm-5"
  val novita_minimax_m3 = "minimax/minimax-m3"
  val novita_minimax_m2_7 = "minimax/minimax-m2.7"
  val novita_minimax_m2_5 = "minimax/minimax-m2.5"
  val novita_gemma_4_31b_it = "google/gemma-4-31b-it"
  val novita_deepseek_v3_1 = "deepseek/deepseek-v3.1"
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
