package io.cequence.openaiscala.domain

/**
 * OpenAI models available as of `2023-03-07`.
 *
 * @since Jan 2023
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
  val code_search_ada_code_001 = "code-search-ada-code-001"
  val code_search_ada_text_001 = "code-search-ada-text-001"
  val text_embedding_ada_002 = "text-embedding-ada-002"
  val text_search_ada_doc_001 = "text-search-ada-doc-001"
  val text_search_ada_query_001 = "text-search-ada-query-001"
  val text_ada_001 = "text-ada-001"
  //  val text_ada_001 = "text-ada:001"
  val text_similarity_ada_001 = "text-similarity-ada-001"

  // Babbage
  val babbage = "babbage"
  val babbage_code_search_code = "babbage-code-search-code"
  val babbage_code_search_text = "babbage-code-search-text"
  val babbage_search_document = "babbage-search-document"
  val babbage_search_query = "babbage-search-query"
  val babbage_similarity = "babbage-similarity"
  val babbage_2020_05_03 = "babbage:2020-05-03"
  val code_search_babbage_code_001 = "code-search-babbage-code-001"
  val code_search_babbage_text_001 = "code-search-babbage-text-001"
  val text_babbage_001 = "text-babbage-001"
  //  val text_babbage_001 = "text-babbage:001"
  val text_search_babbage_doc_001 = "text-search-babbage-doc-001"
  val text_search_babbage_query_001 = "text-search-babbage-query-001"
  val text_similarity_babbage_001 = "text-similarity-babbage-001"

  // Curie
  val curie = "curie"
  val curie_instruct_beta = "curie-instruct-beta"
  val curie_search_document = "curie-search-document"
  val curie_search_query = "curie-search-query"
  val curie_similarity = "curie-similarity"
  val curie_2020_05_03 = "curie:2020-05-03"
  val if_curie_v2 = "if-curie-v2"
  val text_curie_001 = "text-curie-001"
  //  val text_curie_001 = "text-curie:001"
  val text_search_curie_doc_001 = "text-search-curie-doc-001"
  val text_search_curie_query_001 = "text-search-curie-query-001"
  val text_similarity_curie_001 = "text-similarity-curie-001"

  // Davinci
  val davinci = "davinci"
  val davinci_if_3_0_0 = "davinci-if:3.0.0"
  val davinci_instruct_beta = "davinci-instruct-beta"
  val davinci_instruct_beta_2_0_0 = "davinci-instruct-beta:2.0.0"
  val davinci_search_document = "davinci-search-document"
  val davinci_search_query = "davinci-search-query"
  val davinci_similarity = "davinci-similarity"
  val davinci_2020_05_03 = "davinci:2020-05-03"
  val code_davinci_002 = "code-davinci-002"
  val code_davinci_edit_001 = "code-davinci-edit-001"
  val if_davinci_v2 = "if-davinci-v2"
  val if_davinci_3_0_0 = "if-davinci:3.0.0"
  val text_davinci_001 = "text-davinci-001"
  val text_davinci_002 = "text-davinci-002"
  val text_davinci_003 = "text-davinci-003"
  val text_davinci_edit_001 = "text-davinci-edit-001"
  val text_davinci_insert_001 = "text-davinci-insert-001"
  val text_davinci_insert_002 = "text-davinci-insert-002"
  //  val text_davinci_001 = "text-davinci:001"
  val text_search_davinci_doc_001 = "text-search-davinci-doc-001"
  val text_search_davinci_query_001 = "text-search-davinci-query-001"
  val text_similarity_davinci_001 = "text-similarity-davinci-001"

  // Moderation
  val text_moderation_latest = "text-moderation-latest"
  val text_moderation_stable = "text-moderation-stable"

  // Audio
  val audio_transcribe_001 = "audio-transcribe-001"
  val audio_transcribe_deprecated = "audio-transcribe-deprecated"
  val whisper_1 = "whisper-1"
  val whisper_1_2 = "whisper-1.2"

  // GPT-3.5 (ChatGPT)
  val gpt_3_5_turbo = "gpt-3.5-turbo"
  val gpt_3_5_turbo_0301 = "gpt-3.5-turbo-0301"

  // GPT-4
  val gpt_4 = "gpt-4" // 8k context
  val gpt_4_0314 = "gpt-4-0314" // 8k context (March 14th snapshot)
  val gpt_4_32k = "gpt-4-32k" // 32k context
  val gpt_4_32k_0314 = "gpt-4-32k-0314" // 32k context (March 14th snapshot)

  // Other
  val code_cushman_001 = "code-cushman-001"
  val cushman_2020_05_03 = "cushman:2020-05-03"
}