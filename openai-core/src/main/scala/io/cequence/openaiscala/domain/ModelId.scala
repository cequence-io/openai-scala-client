package io.cequence.openaiscala.domain

/**
 * OpenAI models available as of `2023-12-05`.
 *
 * @since Jan
 *   2023
 */
object ModelId {

  // Ada
  val ada = "ada"
  val ada_code_search_code = "ada-code-search-code"
  val ada_code_search_text = "ada-code-search-text"
  val ada_search_document = "ada-search-document"
  val ada_search_query = "ada-search-query"
  val ada_similarity = "ada-similarity"
  val ada_2020_05_03 = "ada:2020-05-03"
  @Deprecated // will be turned off on Jan 4th
  val code_search_ada_code_001 = "code-search-ada-code-001"
  @Deprecated // will be turned off on Jan 4th
  val code_search_ada_text_001 = "code-search-ada-text-001"
  @Deprecated // will be turned off on Jan 4th
  val text_search_ada_doc_001 = "text-search-ada-doc-001"
  @Deprecated // will be turned off on Jan 4th
  val text_search_ada_query_001 = "text-search-ada-query-001"
  @Deprecated // will be turned off on Jan 4th
  val text_ada_001 = "text-ada-001"
  //  val text_ada_001 = "text-ada:001"
  @Deprecated // will be turned off on Jan 4th
  val text_similarity_ada_001 = "text-similarity-ada-001"

  // Babbage
  val babbage = "babbage"
  val babbage_code_search_code = "babbage-code-search-code"
  val babbage_code_search_text = "babbage-code-search-text"
  val babbage_search_document = "babbage-search-document"
  val babbage_search_query = "babbage-search-query"
  val babbage_similarity = "babbage-similarity"
  val babbage_2020_05_03 = "babbage:2020-05-03"
  @Deprecated // will be turned off on Jan 4th
  val code_search_babbage_code_001 = "code-search-babbage-code-001"
  @Deprecated // will be turned off on Jan 4th
  val code_search_babbage_text_001 = "code-search-babbage-text-001"
  @Deprecated // will be turned off on Jan 4th
  val text_babbage_001 = "text-babbage-001"
  //  val text_babbage_001 = "text-babbage:001"
  @Deprecated // will be turned off on Jan 4th
  val text_search_babbage_doc_001 = "text-search-babbage-doc-001"
  @Deprecated // will be turned off on Jan 4th
  val text_search_babbage_query_001 = "text-search-babbage-query-001"
  @Deprecated // will be turned off on Jan 4th
  val text_similarity_babbage_001 = "text-similarity-babbage-001"
  val babbage_002 = "babbage-002"

  // Curie
  val curie = "curie"
  @Deprecated // will be turned off on Jan 4th
  val curie_instruct_beta = "curie-instruct-beta"
  val curie_search_document = "curie-search-document"
  val curie_search_query = "curie-search-query"
  val curie_similarity = "curie-similarity"
  val curie_2020_05_03 = "curie:2020-05-03"
  val if_curie_v2 = "if-curie-v2"
  @Deprecated // will be turned off on Jan 4th
  val text_curie_001 = "text-curie-001"
  //  val text_curie_001 = "text-curie:001"
  @Deprecated // will be turned off on Jan 4th
  val text_search_curie_doc_001 = "text-search-curie-doc-001"
  @Deprecated // will be turned off on Jan 4th
  val text_search_curie_query_001 = "text-search-curie-query-001"
  @Deprecated // will be turned off on Jan 4th
  val text_similarity_curie_001 = "text-similarity-curie-001"

  // Davinci
  val davinci = "davinci"
  val davinci_if_3_0_0 = "davinci-if:3.0.0"
  @Deprecated // will be turned off on Jan 4th
  val davinci_instruct_beta = "davinci-instruct-beta"
  val davinci_instruct_beta_2_0_0 = "davinci-instruct-beta:2.0.0"
  val davinci_search_document = "davinci-search-document"
  val davinci_search_query = "davinci-search-query"
  val davinci_similarity = "davinci-similarity"
  val davinci_2020_05_03 = "davinci:2020-05-03"
  val davinci_002 = "davinci-002"

  @Deprecated
  val code_davinci_001 = "code-davinci-001"

  @Deprecated
  val code_davinci_002 = "code-davinci-002"
  @Deprecated // will be turned off on Jan 4th
  val code_davinci_edit_001 = "code-davinci-edit-001"
  val if_davinci_v2 = "if-davinci-v2"
  val if_davinci_3_0_0 = "if-davinci:3.0.0"
  @Deprecated // will be turned off on Jan 4th
  val text_davinci_001 = "text-davinci-001"
  @Deprecated // will be turned off on Jan 4th
  val text_davinci_002 = "text-davinci-002"
  @Deprecated // will be turned off on Jan 4th
  val text_davinci_003 = "text-davinci-003"
  @Deprecated // will be turned off on Jan 4th
  val text_davinci_edit_001 = "text-davinci-edit-001"
  @Deprecated // will be turned off on Jan 4th
  val text_davinci_insert_001 = "text-davinci-insert-001"
  @Deprecated // will be turned off on Jan 4th
  val text_davinci_insert_002 = "text-davinci-insert-002"
  //  val text_davinci_001 = "text-davinci:001"
  @Deprecated // will be turned off on Jan 4th
  val text_search_davinci_doc_001 = "text-search-davinci-doc-001"
  @Deprecated // will be turned off on Jan 4th
  val text_search_davinci_query_001 = "text-search-davinci-query-001"
  @Deprecated // will be turned off on Jan 4th
  val text_similarity_davinci_001 = "text-similarity-davinci-001"

  // Moderation
  val text_moderation_latest = "text-moderation-latest"
  val text_moderation_stable = "text-moderation-stable"
  val text_moderation_007 = "text-moderation-007"

  // Embeddings
  val text_embedding_ada_002 = "text-embedding-ada-002"
  val text_embedding_3_large = "text-embedding-3-large"
  val text_embedding_3_small = "text-embedding-3-small"

  // Audio
  val audio_transcribe_001 = "audio-transcribe-001"
  val audio_transcribe_deprecated = "audio-transcribe-deprecated"
  val whisper_1 = "whisper-1"
  val whisper_1_2 = "whisper-1.2"
  val tts_1_hd = "tts-1-hd"
  val tts_1_1106 = "tts-1-1106"
  val tts_1_hd_1106 = "tts-1-hd-1106"
  val canary_tts = "canary-tts"
  val canary_whisper = "canary-whisper"

  // Image gen
  val dall_e_3 = "dall-e-3"
  val dall_e_2 = "dall-e-2"

  // GPT-3.5 (ChatGPT)

  // The default ‘gpt-3.5-turbo’ will point to gpt-3.5-turbo-0125 starting Feb 15th.
  val gpt_3_5_turbo = "gpt-3.5-turbo"
  @Deprecated // supported till 09/13/2023, 4k context (March 1st snapshot)
  val gpt_3_5_turbo_0301 = "gpt-3.5-turbo-0301"
  // 4k context (June 13th snapshot), fine-tuned for function calling
  @Deprecated // supported till 09/13/2023
  val gpt_3_5_turbo_0613 = "gpt-3.5-turbo-0613"

  // 16k context
  val gpt_3_5_turbo_16k = "gpt-3.5-turbo-16k"
  // 16k context (June 13th snapshot), fine-tuned for function calling
  @Deprecated // supported till 09/13/2023
  val gpt_3_5_turbo_16k_0613 = "gpt-3.5-turbo-16k-0613"
  // 16k context (Jan 25th 2024 snapshot)
  val gpt_3_5_turbo_0125 = "gpt-3.5-turbo-0125"

  val gpt_3_5_turbo_instruct_0914 = "gpt-3.5-turbo-instruct-0914"
  val gpt_3_5_turbo_instruct = "gpt-3.5-turbo-instruct"
  // 16k context, gpt_3_5_turbo will point to this model from Dec 11, 2023
  val gpt_3_5_turbo_1106 = "gpt-3.5-turbo-1106"

  // Q*/Strawberry
  val o3_mini = "o3-mini"
  val o3_mini_2025_01_31 = "o3-mini-2025-01-31"
  val o1 = "o1"
  val o1_2024_12_17 = "o1-2024-12-17"
  val o1_preview = "o1-preview"
  val o1_preview_2024_09_12 = "o1-preview-2024-09-12"
  val o1_mini = "o1-mini"
  val o1_mini_2024_09_12 = "o1-mini-2024-09-12"

  // GPT-4.5

  // currently points to gpt-4.5-preview-2025-02-27
  val gpt_4_5_preview = "gpt-4.5-preview"
  // 128k context, knowledge cutoff is Oct 2023
  val gpt_4_5_preview_2025_02_27 = "gpt-4.5-preview-2025-02-27"
  // GPT-4
  // flagship multimodal model, 128K context, currently points to "gpt-4o-2024-08-06, training data up to Oct 2023
  val gpt_4o = "gpt-4o"
  // context window: 128,000 tokens, output tokens:	16,384 tokens, Up to Oct 2023
  val gpt_4o_2024_11_20 = "gpt-4o-2024-11-20"
  // context window: 128,000 tokens, output tokens:	16,384 tokens, Up to Oct 2023
  val gpt_4o_2024_08_06 = "gpt-4o-2024-08-06"
  // context window: 128,000 tokens, output tokens:	4,096 tokens, Up to Oct 2023
  val gpt_4o_2024_05_13 = "gpt-4o-2024-05-13"
  // cost-efficient small model, 128K context, currently points to gpt-4o-mini-2024-07-18
  val gpt_4o_mini = "gpt-4o-mini"
  // cost-efficient small model, 128K context, training data up to Oct 2023
  val gpt_4o_mini_2024_07_18 = "gpt-4o-mini-2024-07-18"
  // dynamic model continuously updated to the current version of GPT-4o in ChatGPT.
  // Intended for research and evaluation [2].	128,000 tokens	16,384 tokens	Up to Oct 2023
  val chatgpt_4o_latest = "chatgpt-4o-latest"
  // 8k context, uses the version 0301 till June 27th, then 0613
  val gpt_4 = "gpt-4"
  @Deprecated // supported till 09/13/2023, 8k context (March 14th snapshot)
  val gpt_4_0314 = "gpt-4-0314"
  // 8k context (June 13th snapshot), fine-tuned for function calling
  val gpt_4_0613 = "gpt-4-0613"
  // 32k context, uses the version 0314 till June 27th, then 0613
  val gpt_4_32k = "gpt-4-32k"
  @Deprecated // supported till 09/13/2023, 32k context (March 14th snapshot)
  val gpt_4_32k_0314 = "gpt-4-32k-0314"
  // 32k context (June 13th snapshot), fine-tuned for function calling
  val gpt_4_32k_0613 = "gpt-4-32k-0613"

  // GPT-4 Turbo

  // The latest GPT-4 Turbo model with vision capabilities. Points to gpt-4-turbo-2024-04-09.
  val gpt_4_turbo = "gpt-4-turbo"
  // GPT-4 Turbo + Vision model (with training data up to Dec 2023)
  val gpt_4_turbo_2024_04_09 = "gpt-4-turbo-2024-04-09"
  // name alias, which will always point to the latest GPT-4 Turbo preview model
  val gpt_4_turbo_preview = "gpt-4-turbo-preview"
  // 128K context (with training data upto April 2023) - Nov 6th 2023 snapshot
  val gpt_4_1106_preview = "gpt-4-1106-preview"
  // 128K context (with training data upto April 2023) - Jan 25th 2024 snapshot
  val gpt_4_0125_preview = "gpt-4-0125-preview"
  // 128K context (with training data upto April 2023)
  // includes supports for vision in addition to gpt-4-turbo capabilities
  val gpt_4_vision_preview = "gpt-4-vision-preview"
  val gpt_4_1106_vision_preview = "gpt-4-1106-vision-preview"

  // Other
  @Deprecated
  val code_cushman_001 = "code-cushman-001"

  @Deprecated
  val code_cushman_002 = "code-cushman-002"
  val cushman_2020_05_03 = "cushman:2020-05-03"
}
