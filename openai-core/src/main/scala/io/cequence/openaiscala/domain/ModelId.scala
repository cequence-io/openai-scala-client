package io.cequence.openaiscala.domain

/**
 * OpenAI models available as of `2026-09-08`.
 *
 * @since Jan
 *   2023
 */
object ModelId {

  // Ada
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val ada = "ada"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val ada_code_search_code = "ada-code-search-code"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val ada_code_search_text = "ada-code-search-text"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val ada_search_document = "ada-search-document"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val ada_search_query = "ada-search-query"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val ada_similarity = "ada-similarity"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val ada_2020_05_03 = "ada:2020-05-03"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_search_ada_code_001 = "code-search-ada-code-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_search_ada_text_001 = "code-search-ada-text-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_search_ada_doc_001 = "text-search-ada-doc-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_search_ada_query_001 = "text-search-ada-query-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_ada_001 = "text-ada-001"
  //  val text_ada_001 = "text-ada:001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_similarity_ada_001 = "text-similarity-ada-001"

  // Babbage
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val babbage = "babbage"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val babbage_code_search_code = "babbage-code-search-code"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val babbage_code_search_text = "babbage-code-search-text"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val babbage_search_document = "babbage-search-document"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val babbage_search_query = "babbage-search-query"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val babbage_similarity = "babbage-similarity"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val babbage_2020_05_03 = "babbage:2020-05-03"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_search_babbage_code_001 = "code-search-babbage-code-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_search_babbage_text_001 = "code-search-babbage-text-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_babbage_001 = "text-babbage-001"
  //  val text_babbage_001 = "text-babbage:001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_search_babbage_doc_001 = "text-search-babbage-doc-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_search_babbage_query_001 = "text-search-babbage-query-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_similarity_babbage_001 = "text-similarity-babbage-001"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-09-28 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val babbage_002 = "babbage-002"

  // Curie
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val curie = "curie"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val curie_instruct_beta = "curie-instruct-beta"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val curie_search_document = "curie-search-document"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val curie_search_query = "curie-search-query"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val curie_similarity = "curie-similarity"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val curie_2020_05_03 = "curie:2020-05-03"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val if_curie_v2 = "if-curie-v2"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_curie_001 = "text-curie-001"
  //  val text_curie_001 = "text-curie:001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_search_curie_doc_001 = "text-search-curie-doc-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_search_curie_query_001 = "text-search-curie-query-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_similarity_curie_001 = "text-similarity-curie-001"

  // Davinci
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci = "davinci"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci_if_3_0_0 = "davinci-if:3.0.0"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci_instruct_beta = "davinci-instruct-beta"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci_instruct_beta_2_0_0 = "davinci-instruct-beta:2.0.0"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci_search_document = "davinci-search-document"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci_search_query = "davinci-search-query"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci_similarity = "davinci-similarity"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci_2020_05_03 = "davinci:2020-05-03"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-09-28 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val davinci_002 = "davinci-002"

  @Deprecated
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_davinci_001 = "code-davinci-001"

  @Deprecated
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_davinci_002 = "code-davinci-002"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_davinci_edit_001 = "code-davinci-edit-001"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val if_davinci_v2 = "if-davinci-v2"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val if_davinci_3_0_0 = "if-davinci:3.0.0"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_davinci_001 = "text-davinci-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_davinci_002 = "text-davinci-002"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_davinci_003 = "text-davinci-003"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_davinci_edit_001 = "text-davinci-edit-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_davinci_insert_001 = "text-davinci-insert-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_davinci_insert_002 = "text-davinci-insert-002"
  //  val text_davinci_001 = "text-davinci:001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_search_davinci_doc_001 = "text-search-davinci-doc-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_search_davinci_query_001 = "text-search-davinci-query-001"
  @Deprecated // will be turned off on Jan 4th
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_similarity_davinci_001 = "text-similarity-davinci-001"

  // Moderation
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_moderation_latest = "text-moderation-latest"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_moderation_stable = "text-moderation-stable"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val text_moderation_007 = "text-moderation-007"
  val omni_moderation_latest = "omni-moderation-latest"
  val omni_moderation_2024_09_26 = "omni-moderation-2024-09-26"

  // Embeddings
  val text_embedding_ada_002 = "text-embedding-ada-002"
  val text_embedding_3_large = "text-embedding-3-large"
  val text_embedding_3_small = "text-embedding-3-small"

  // Audio
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val audio_transcribe_001 = "audio-transcribe-001"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val audio_transcribe_deprecated = "audio-transcribe-deprecated"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-02-26 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val whisper_1 = "whisper-1"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val whisper_1_2 = "whisper-1.2"
  val tts_1 = "tts-1"
  val tts_1_hd = "tts-1-hd"
  val tts_1_1106 = "tts-1-1106"
  val tts_1_hd_1106 = "tts-1-hd-1106"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val canary_tts = "canary-tts"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val canary_whisper = "canary-whisper"

  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_mini_search_preview_2025_03_11 = "gpt-4o-mini-search-preview-2025-03-11"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_search_preview_2025_03_11 = "gpt-4o-search-preview-2025-03-11"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-02-26 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_transcribe = "gpt-4o-transcribe"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-02-26 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_mini_transcribe = "gpt-4o-mini-transcribe"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-01-20 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_mini_transcribe_2025_03_20 = "gpt-4o-mini-transcribe-2025-03-20"
  val gpt_4o_mini_transcribe_2025_12_15 = "gpt-4o-mini-transcribe-2025-12-15"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-02-26 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_transcribe_diarize = "gpt-4o-transcribe-diarize"
  val gpt_4o_mini_tts = "gpt-4o-mini-tts"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_mini_tts_2025_03_20 = "gpt-4o-mini-tts-2025-03-20"
  val gpt_4o_mini_tts_2025_12_15 = "gpt-4o-mini-tts-2025-12-15"
  val gpt_transcribe = "gpt-transcribe"
  val gpt_live_transcribe = "gpt-live-transcribe"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-01-20 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_audio = "gpt-audio"
  val gpt_audio_1_5 = "gpt-audio-1.5"
  val gpt_audio_2025_08_28 = "gpt-audio-2025-08-28"
  val gpt_audio_mini_2025_12_15 = "gpt-audio-mini-2025-12-15"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-01-20 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_realtime = "gpt-realtime"
  val gpt_realtime_1_5 = "gpt-realtime-1.5"
  val gpt_realtime_2 = "gpt-realtime-2"
  val gpt_realtime_2_1 = "gpt-realtime-2.1"
  val gpt_realtime_2_1_mini = "gpt-realtime-2.1-mini"
  val gpt_realtime_2025_08_28 = "gpt-realtime-2025-08-28"
  val gpt_realtime_mini_2025_12_15 = "gpt-realtime-mini-2025-12-15"
  val gpt_realtime_translate = "gpt-realtime-translate"
  val gpt_realtime_whisper = "gpt-realtime-whisper"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_mini_audio_preview = "gpt-4o-mini-audio-preview"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_mini_vision_preview = "gpt-4o-mini-vision-preview"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_mini_voice_preview = "gpt-4o-mini-voice-preview"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_audio_preview = "gpt-4o-audio-preview"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_realtime_preview = "gpt-4o-realtime-preview"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_realtime_2024_12_17 = "gpt-4o-realtime-2024-12-17"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_realtime_2024_10_01_preview = "gpt-4o-realtime-2024-10-01-preview"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-01-20 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_audio_mini = "gpt-audio-mini"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_audio_mini_2025_10_06 = "gpt-audio-mini-2025-10-06"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2027-01-20 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_realtime_mini = "gpt-realtime-mini"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_realtime_mini_2025_10_06 = "gpt-realtime-mini-2025-10-06"
  // 128K context (with training data upto April 2023)
  // includes supports for vision in addition to gpt-4-turbo capabilities
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_vision_preview = "gpt-4-vision-preview"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_1106_vision_preview = "gpt-4-1106-vision-preview"

  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val computer_use_preview = "computer-use-preview"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val computer_use_preview_2025_03_11 = "computer-use-preview-2025-03-11"

  // Image gen
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val dall_e_3 = "dall-e-3"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val dall_e_2 = "dall-e-2"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-01 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_image_1_mini = "gpt-image-1-mini"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-01 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_image_1_5 = "gpt-image-1.5"
  val gpt_image_2 = "gpt-image-2"
  val gpt_image_2_2026_04_21 = "gpt-image-2-2026-04-21"
  // GPT Image 2.5 (released 2026-09-08): Flare = fast default (~50% lower latency than
  // gpt-image-2), Sunburst = slower, higher-precision editing tier
  val gpt_image_2_5_flare = "gpt-image-2.5-flare"
  val gpt_image_2_5_flare_2026_09_08 = "gpt-image-2.5-flare-2026-09-08"
  val gpt_image_2_5_sunburst = "gpt-image-2.5-sunburst"
  val gpt_image_2_5_sunburst_2026_09_08 = "gpt-image-2.5-sunburst-2026-09-08"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-01 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val chatgpt_image_latest = "chatgpt-image-latest"

  // Video gen
  @deprecated(
    "Shut down by OpenAI on 2026-09-24 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val sora_2 = "sora-2"
  @deprecated(
    "Shut down by OpenAI on 2026-09-24 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val sora_2_pro = "sora-2-pro"

  // GPT-3.5 (ChatGPT)

  // The default 'gpt-3.5-turbo' will point to gpt-3.5-turbo-0125 starting Feb 15th.
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_3_5_turbo = "gpt-3.5-turbo"
  @Deprecated // supported till 09/13/2023, 4k context (March 1st snapshot)
  val gpt_3_5_turbo_0301 = "gpt-3.5-turbo-0301"
  // 4k context (June 13th snapshot), fine-tuned for function calling
  @Deprecated // supported till 09/13/2023
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_3_5_turbo_0613 = "gpt-3.5-turbo-0613"

  // 16k context
  val gpt_3_5_turbo_16k = "gpt-3.5-turbo-16k"
  // 16k context (June 13th snapshot), fine-tuned for function calling
  @Deprecated // supported till 09/13/2023
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_3_5_turbo_16k_0613 = "gpt-3.5-turbo-16k-0613"
  // 16k context (Jan 25th 2024 snapshot)
  val gpt_3_5_turbo_0125 = "gpt-3.5-turbo-0125"

  val gpt_3_5_turbo_instruct_0914 = "gpt-3.5-turbo-instruct-0914"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-09-28 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_3_5_turbo_instruct = "gpt-3.5-turbo-instruct"
  // 16k context, gpt_3_5_turbo will point to this model from Dec 11, 2023
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-09-28 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_3_5_turbo_1106 = "gpt-3.5-turbo-1106"

  // O models - Q*/Strawberry

  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o4_mini = "o4-mini"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o4_mini_deep_research = "o4-mini-deep-research"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o4_mini_deep_research_2025_06_26 = "o4-mini-deep-research-2025-06-26"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o4_mini_2025_04_16 = "o4-mini-2025-04-16"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-11 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o3_pro_2025_06_10 = "o3-pro-2025-06-10"
  val o3_pro = "o3-pro"
  val o3 = "o3"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o3_deep_research_2025_06_26 = "o3-deep-research-2025-06-26"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o3_deep_research = "o3-deep-research"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-11 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o3_2025_04_16 = "o3-2025-04-16"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o3_mini = "o3-mini"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o3_mini_2025_01_31 = "o3-mini-2025-01-31"
  // High-compute version of o3-mini
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o3_mini_high = "o3-mini-high"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o1 = "o1"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o1_2024_12_17 = "o1-2024-12-17"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o1_preview = "o1-preview"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o1_preview_2024_09_12 = "o1-preview-2024-09-12"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o1_mini = "o1-mini"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o1_mini_2024_09_12 = "o1-mini-2024-09-12"
  // High-compute version of o1 for advanced reasoning
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o1_pro = "o1-pro"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val o1_pro_2025_03_19 = "o1-pro-2025-03-19"

  // GPT-5.6 (GA; created 2026-06-23). Sol/Terra/Luna reasoning tiers replace pro/mini/nano.
  // Reasoning-first: sampling params rejected. reasoning_effort on chat completions supports
  // none/low/medium/high/xhigh; 'max' is Responses-API-only (chat completions rejects it) and
  // 'minimal' is rejected by both APIs. Verified against the live API 2026-07-11.
  // rolling ChatGPT-style alias (chat completions; reasoning_effort supports only 'medium',
  // sampling params rejected - see ChatCompletionSettingsConversions.chatLatest)
  val chat_latest = "chat-latest"

  // GPT-6 Astra (limited preview 2026-09-03, API GA 2026-09-04, live-verified 2026-09-05): 1,050,000 context, 128,000
  // max output, knowledge cutoff 2026-04-30, text+image in / text out, chat completions +
  // Responses API + Batch API, structured outputs, prompt caching, MCP/web search/etc. on the
  // Responses API. reasoning_effort low/medium/high/xhigh on chat completions ('max' is
  // Responses-API-only; 'none' and 'minimal' are rejected everywhere); all sampling params
  // (temperature/top_p/penalties/logprobs) are rejected. Function tools are NOT supported on the
  // chat completions API at all - createChatToolCompletion is routed through the Responses API.
  val gpt_6_astra = "gpt-6-astra"

  // GPT-6 Sol/Luna (created 2026-09-14, live-verified 2026-09-22). Unlike Astra they keep
  // GPT-5.6's rules: all sampling params rejected, max_tokens -> max_completion_tokens,
  // reasoning_effort none/low/medium/high/xhigh on chat completions ('max' Responses-API-only,
  // 'minimal' rejected everywhere), and function tools on chat completions only with
  // reasoning_effort 'none' (any other value, or none at all, is a 400). There is no
  // `gpt-6-terra` (404) as of 2026-09-22.
  val gpt_6_sol = "gpt-6-sol"
  val gpt_6_luna = "gpt-6-luna"

  val gpt_5_6_sol = "gpt-5.6-sol"
  val gpt_5_6_terra = "gpt-5.6-terra"
  val gpt_5_6_luna = "gpt-5.6-luna"
  // Daybreak Red: GPT-5.6 Cyber - vulnerability research / exploit reproduction specialist;
  // requires Trusted Access for Cyber enrollment (not generally available)
  val gpt_5_6_cyber = "gpt-5.6-cyber"

  // OpenAI models as served on Amazon Bedrock (`bedrock-mantle` and the OpenAI-compatible
  // `bedrock-runtime` surface) - the same models under a provider-prefixed id; the per-model
  // parameter rules apply to these ids too (see ChatCompletionSettingsConversions)
  // GPT-5.6 Sol/Terra/Luna (Bedrock launch 2026-07-13, 1M context); `openai.gpt-5.6-sol` is
  // verified against the AWS model card, Terra/Luna follow the same naming. On bedrock-runtime
  // use the `us.`/`global.` cross-region inference-profile prefix instead.
  // GPT-6 (live-verified 2026-09-22): inference profiles `us.`/`global.` only (no `eu.`),
  // `global.openai.gpt-6-luna` / `-sol` answer in eu-central-1
  val bedrock_openai_gpt_6_astra = "openai.gpt-6-astra"
  val bedrock_openai_gpt_6_sol = "openai.gpt-6-sol"
  val bedrock_openai_gpt_6_luna = "openai.gpt-6-luna"
  val bedrock_openai_gpt_5_6_sol = "openai.gpt-5.6-sol"
  val bedrock_openai_gpt_5_6_terra = "openai.gpt-5.6-terra"
  val bedrock_openai_gpt_5_6_luna = "openai.gpt-5.6-luna"
  val bedrock_openai_gpt_5_5 = "openai.gpt-5.5"
  // dated snapshots, live-verified on bedrock-mantle 2026-09-14; unlike the undated ids they
  // are not (yet) served by the OpenAI-compatible bedrock-runtime surface
  val bedrock_openai_gpt_5_5_2026_04_23 = "openai.gpt-5.5-2026-04-23"
  val bedrock_openai_gpt_5_4 = "openai.gpt-5.4"
  val bedrock_openai_gpt_5_4_2026_03_05 = "openai.gpt-5.4-2026-03-05"
  val bedrock_openai_gpt_oss_120b = "openai.gpt-oss-120b"
  val bedrock_openai_gpt_oss_20b = "openai.gpt-oss-20b"

  // GPT-5.5
  val gpt_5_5 = "gpt-5.5"
  val gpt_5_5_2026_04_23 = "gpt-5.5-2026-04-23"
  val gpt_5_5_pro = "gpt-5.5-pro"
  val gpt_5_5_pro_2026_04_23 = "gpt-5.5-pro-2026-04-23"

  // GPT-5.4
  // 1,050,000 context window, 128k max output tokens, Aug 31, 2025 knowledge cutoff, Reasoning token support
  val gpt_5_4 = "gpt-5.4"
  val gpt_5_4_2026_03_05 = "gpt-5.4-2026-03-05"
  val gpt_5_4_pro = "gpt-5.4-pro"
  val gpt_5_4_pro_2026_03_05 = "gpt-5.4-pro-2026-03-05"
  // 400,000 context window, 128k max output tokens, Aug 31, 2025 knowledge cutoff, Reasoning token support
  val gpt_5_4_mini = "gpt-5.4-mini"
  val gpt_5_4_mini_2026_03_17 = "gpt-5.4-mini-2026-03-17"
  val gpt_5_4_nano = "gpt-5.4-nano"
  val gpt_5_4_nano_2026_03_17 = "gpt-5.4-nano-2026-03-17"

  // GPT-5.3
  @deprecated(
    "Shut down by OpenAI on 2026-08-10 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_3_chat_latest = "gpt-5.3-chat-latest"
  val gpt_5_3_codex = "gpt-5.3-codex"
  val gpt_5_3_codex_mini = "gpt-5.3-codex-mini"
  val gpt_5_3_codex_max = "gpt-5.3-codex-max"

  // GPT-5.2
  // 400k context window, 128k max output tokens, Aug 31, 2025 knowledge cutoff, Reasoning token support
  val gpt_5_2 = "gpt-5.2"
  val gpt_5_2_2025_12_11 = "gpt-5.2-2025-12-11"
  val gpt_5_2_pro = "gpt-5.2-pro"
  val gpt_5_2_pro_2025_12_11 = "gpt-5.2-pro-2025-12-11"
  @deprecated(
    "Shut down by OpenAI on 2026-08-10 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_2_chat_latest = "gpt-5.2-chat-latest"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_2_codex = "gpt-5.2-codex"

  // GPT-5.1
  // 400k context window, 128k max output tokens, Sep 30, 2024 knowledge cutoff, Reasoning token support
  val gpt_5_1 = "gpt-5.1"
  val gpt_5_1_2025_11_13 = "gpt-5.1-2025-11-13"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_1_codex_mini = "gpt-5.1-codex-mini"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_1_codex_max = "gpt-5.1-codex-max"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_1_chat_latest = "gpt-5.1-chat-latest"
  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_1_codex = "gpt-5.1-codex"

  // GPT-5
  val gpt_5 = "gpt-5"
  val gpt_5_pro = "gpt-5-pro"
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-11 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_pro_2025_10_06 = "gpt-5-pro-2025-10-06"

  // 400k context window, 128k max output tokens, Oct 01, 2024 knowledge cutoff
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-11 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_2025_08_07 = "gpt-5-2025-08-07"

  val gpt_5_mini = "gpt-5-mini"
  // 400k context window, 128,000 max output tokens, May 31, 2024 knowledge cutoff
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-11 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_mini_2025_08_07 = "gpt-5-mini-2025-08-07"

  val gpt_5_nano = "gpt-5-nano"
  // 400k context window, 128,000 max output tokens, May 31, 2024 knowledge cutoff
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-12-11 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_nano_2025_08_07 = "gpt-5-nano-2025-08-07"

  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_chat_latest = "gpt-5-chat-latest"

  @deprecated(
    "Shut down by OpenAI on 2026-07-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_5_codex = "gpt-5-codex"

  val gpt_5_search_api = "gpt-5-search-api"
  val gpt_5_search_api_2025_10_14 = "gpt-5-search-api-2025-10-14"

  // GPT-4.5

  // currently points to gpt-4.5-preview-2025-02-27
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_5_preview = "gpt-4.5-preview"
  // 128k context, knowledge cutoff is Oct 2023
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_5_preview_2025_02_27 = "gpt-4.5-preview-2025-02-27"

  // GPT 4.1
  // 1,047,576 context window, 32,768 max output tokens, Jun 01, 2024 knowledge cutoff
  val gpt_4_1 = "gpt-4.1"
  // 1,047,576 context window, 32,768 max output tokens, Jun 01, 2024 knowledge cutoff
  val gpt_4_1_2025_04_14 = "gpt-4.1-2025-04-14"
  // 1,047,576 context window, 32,768 max output tokens, Jun 01, 2024 knowledge cutoff
  val gpt_4_1_mini = "gpt-4.1-mini"
  // 1,047,576 context window, 32,768 max output tokens, Jun 01, 2024 knowledge cutoff
  val gpt_4_1_mini_2025_04_14 = "gpt-4.1-mini-2025-04-14"
  // 1,047,576 context window, 32,768 max output tokens, Jun 01, 2024 knowledge cutoff
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_1_nano = "gpt-4.1-nano"
  // 1,047,576 context window, 32,768 max output tokens, Jun 01, 2024 knowledge cutoff
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_1_nano_2025_04_14 = "gpt-4.1-nano-2025-04-14"

  // GPT-4
  // web search
  val gpt_4o_search_preview = "gpt-4o-search-preview"
  // web search
  val gpt_4o_mini_search_preview = "gpt-4o-mini-search-preview"
  // flagship multimodal model, 128K context, currently points to "gpt-4o-2024-08-06, training data up to Oct 2023
  val gpt_4o = "gpt-4o"
  // context window: 128,000 tokens, output tokens:	16,384 tokens, Up to Oct 2023
  val gpt_4o_2024_11_20 = "gpt-4o-2024-11-20"
  // context window: 128,000 tokens, output tokens:	16,384 tokens, Up to Oct 2023
  val gpt_4o_2024_08_06 = "gpt-4o-2024-08-06"
  // context window: 128,000 tokens, output tokens:	4,096 tokens, Up to Oct 2023
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4o_2024_05_13 = "gpt-4o-2024-05-13"
  // cost-efficient small model, 128K context, currently points to gpt-4o-mini-2024-07-18
  val gpt_4o_mini = "gpt-4o-mini"
  // cost-efficient small model, 128K context, training data up to Oct 2023
  val gpt_4o_mini_2024_07_18 = "gpt-4o-mini-2024-07-18"
  // dynamic model continuously updated to the current version of GPT-4o in ChatGPT.
  // Intended for research and evaluation [2].	128,000 tokens	16,384 tokens	Up to Oct 2023
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val chatgpt_4o_latest = "chatgpt-4o-latest"
  // 8k context, uses the version 0301 till June 27th, then 0613
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4 = "gpt-4"
  @Deprecated // supported till 09/13/2023, 8k context (March 14th snapshot)
  val gpt_4_0314 = "gpt-4-0314"
  // 8k context (June 13th snapshot), fine-tuned for function calling
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_0613 = "gpt-4-0613"
  // 32k context, uses the version 0314 till June 27th, then 0613
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_32k = "gpt-4-32k"
  @Deprecated // supported till 09/13/2023, 32k context (March 14th snapshot)
  val gpt_4_32k_0314 = "gpt-4-32k-0314"
  // 32k context (June 13th snapshot), fine-tuned for function calling
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_32k_0613 = "gpt-4-32k-0613"

  // GPT-4 Turbo

  // The latest GPT-4 Turbo model with vision capabilities. Points to gpt-4-turbo-2024-04-09.
  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_turbo = "gpt-4-turbo"
  // GPT-4 Turbo + Vision model (with training data up to Dec 2023)
  val gpt_4_turbo_2024_04_09 = "gpt-4-turbo-2024-04-09"
  // name alias, which will always point to the latest GPT-4 Turbo preview model
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_turbo_preview = "gpt-4-turbo-preview"
  // 128K context (with training data upto April 2023) - Nov 6th 2023 snapshot
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_1106_preview = "gpt-4-1106-preview"
  // 128K context (with training data upto April 2023) - Jan 25th 2024 snapshot
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_4_0125_preview = "gpt-4-0125-preview"

  @deprecated(
    "Scheduled for shutdown by OpenAI on 2026-10-23 - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val gpt_image_1 = "gpt-image-1"

  // Other
  @Deprecated
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_cushman_001 = "code-cushman-001"

  @Deprecated
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val code_cushman_002 = "code-cushman-002"
  @deprecated(
    "No longer served by OpenAI - see https://platform.openai.com/docs/deprecations",
    "1.3.1"
  )
  val cushman_2020_05_03 = "cushman:2020-05-03"
}
