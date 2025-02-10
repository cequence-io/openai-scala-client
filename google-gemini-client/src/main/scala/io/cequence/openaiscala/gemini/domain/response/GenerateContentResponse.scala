package io.cequence.openaiscala.gemini.domain.response

import io.cequence.openaiscala.OpenAIScalaClientException
import io.cequence.openaiscala.gemini.domain.Part.TextPart
import io.cequence.openaiscala.gemini.domain.{Content, HarmCategory, HarmProbability, Modality}
import io.cequence.wsclient.domain.EnumValue

case class GenerateContentResponse(
  candidates: Seq[Candidate] = Nil,
  promptFeedback: Option[PromptFeedback] = None,
  usageMetadata: UsageMetadata,
  modelVersion: String
) {
  def contentHeadTexts: Seq[String] =
    candidates.headOption
      .map(_.content.parts.collect { case TextPart(text) => text })
      .getOrElse(
        throw new OpenAIScalaClientException(
          s"No candidates in the Gemini generate content response for mode ${modelVersion}."
        )
      )

  def contentHeadText: String =
    contentHeadTexts.mkString("\n")
}

/**
 * @param content
 *   Output only. Generated content returned from the model.
 * @param finishReason
 *   Optional. Output only. The reason why the model stopped generating tokens. If empty, the
 *   model has not stopped generating tokens.
 * @param safetyRatings
 *   List of ratings for the safety of a response candidate. There is at most one rating per
 *   category.
 * @param citationMetadata
 *   Output only. Citation information for model-generated candidate. This field may be
 *   populated with recitation information for any text included in the content. These are
 *   passages that are "recited" from copyrighted material in the foundational LLM's training
 *   data.
 * @param tokenCount
 *   Output only. Token count for this candidate.
 * @param groundingAttributions
 *   Output only. Attribution information for sources that contributed to a grounded answer.
 *   This field is populated for GenerateAnswer calls.
 * @param groundingMetadata
 *   Output only. Grounding metadata for the candidate. This field is populated for
 *   GenerateContent calls.
 * @param avgLogprobs
 *   Output only. Average log probability score of the candidate.
 * @param logprobsResult
 *   Output only. Log-likelihood scores for the response tokens and top tokens.
 * @param index
 *   Output only. Index of the candidate in the list of response candidates.
 */
case class Candidate(
  content: Content,
  finishReason: Option[FinishReason] = None,
  safetyRatings: Seq[SafetyRating] = Nil,
  citationMetadata: Option[CitationMetadata] = None,
  tokenCount: Option[Int] = None,
  groundingAttributions: Seq[GroundingAttribution] = Nil,
  groundingMetadata: Option[GroundingMetadata] = None,
  avgLogprobs: Option[Double] = None,
//  logprobsResult: Option[LogprobsResult] = None, TODO: cyclic ref to candidate
  index: Option[Int] = None
)

sealed trait FinishReason extends EnumValue

object FinishReason {
  // FINISH_REASON_UNSPECIFIED: Default value. This value is unused.
  case object FINISH_REASON_UNSPECIFIED extends FinishReason

  // STOP: Natural stop point of the model or provided stop sequence.
  case object STOP extends FinishReason

  // MAX_TOKENS: The maximum number of tokens as specified in the request was reached.
  case object MAX_TOKENS extends FinishReason

  // SAFETY: The response candidate content was flagged for safety reasons.
  case object SAFETY extends FinishReason

  // RECITATION: The response candidate content was flagged for recitation reasons.
  case object RECITATION extends FinishReason

  // LANGUAGE: The response candidate content was flagged for using an unsupported language.
  case object LANGUAGE extends FinishReason

  // OTHER: Unknown reason.
  case object OTHER extends FinishReason

  // BLOCKLIST: Token generation stopped because the content contains forbidden terms.
  case object BLOCKLIST extends FinishReason

  // PROHIBITED_CONTENT: Token generation stopped for potentially containing prohibited content.
  case object PROHIBITED_CONTENT extends FinishReason

  // SPII: Token generation stopped because the content potentially contains Sensitive Personally Identifiable Information (SPII).
  case object SPII extends FinishReason

  // MALFORMED_FUNCTION_CALL: The function call generated by the model is invalid.
  case object MALFORMED_FUNCTION_CALL extends FinishReason

  // IMAGE_SAFETY: Token generation stopped because generated images contain safety violations.
  case object IMAGE_SAFETY extends FinishReason

  def values: Seq[FinishReason] = Seq(
    FINISH_REASON_UNSPECIFIED,
    STOP,
    MAX_TOKENS,
    SAFETY,
    RECITATION,
    LANGUAGE,
    OTHER,
    BLOCKLIST,
    PROHIBITED_CONTENT,
    SPII,
    MALFORMED_FUNCTION_CALL,
    IMAGE_SAFETY
  )
}

/**
 * A set of the feedback metadata the prompt specified in GenerateContentRequest.content.
 *
 * @param blockReason
 *   Optional. If set, the prompt was blocked and no candidates are returned. Rephrase the
 *   prompt.
 * @param safetyRatings
 *   Ratings for safety of the prompt. There is at most one rating per category.
 */
case class PromptFeedback(
  blockReason: Option[BlockReason], // enum e.g. "SAFETY", "OTHER", etc.
  safetyRatings: Seq[SafetyRating]
)

sealed trait BlockReason extends EnumValue

object BlockReason {
  // Default value. This value is unused.
  case object BLOCK_REASON_UNSPECIFIED extends BlockReason
  // Prompt was blocked due to safety reasons. Inspect safetyRatings to understand which safety category blocked it.
  case object SAFETY extends BlockReason
  // Prompt was blocked due to unknown reasons.
  case object OTHER extends BlockReason
  // Prompt was blocked due to the terms which are included from the terminology blocklist.
  case object BLOCKLIST extends BlockReason
  // Prompt was blocked due to prohibited content.
  case object PROHIBITED_CONTENT extends BlockReason
  // Candidates blocked due to unsafe image generation content.
  case object IMAGE_SAFETY extends BlockReason

  def values: Seq[BlockReason] = Seq(
    BLOCK_REASON_UNSPECIFIED,
    SAFETY,
    OTHER,
    BLOCKLIST,
    PROHIBITED_CONTENT,
    IMAGE_SAFETY
  )
}

/**
 * Safety rating for a piece of content. The safety rating contains the category of harm and
 * the harm probability level in that category for a piece of content. Content is classified
 * for safety across a number of harm categories and the probability of the harm classification
 * is included here.
 *
 * @param category
 *   The category for this rating.
 * @param probability
 *   The probability of harm for this content.
 * @param blocked
 *   Was this content blocked because of this rating?
 */
case class SafetyRating(
  category: HarmCategory,
  probability: HarmProbability,
  blocked: Option[Boolean]
)

/**
 * A collection of source attributions for a piece of content.
 *
 * @param citationSources
 *   Citations to sources for a specific response.
 */
case class CitationMetadata(
  citationSources: Seq[CitationSource] = Nil
)

/**
 * A citation to a source for a portion of a specific response.
 *
 * @param startIndex
 *   Optional. Start of segment of the response that is attributed to this source. Index
 *   indicates the start of the segment, measured in bytes.
 * @param endIndex
 *   Optional. End of the attributed segment, exclusive.
 * @param uri
 *   Optional. URI that is attributed as a source for a portion of the text.
 * @param license
 *   Optional. License for the GitHub project that is attributed as a source for segment.
 *   License info is required for code citations.
 */
case class CitationSource(
  startIndex: Option[Int],
  endIndex: Option[Int],
  uri: Option[String],
  license: Option[String]
)

/**
 * Metadata on the generation request's token usage.
 *
 * @param promptTokenCount
 *   Number of tokens in the prompt. When cachedContent is set, this is still the total effective
 *   prompt size meaning this includes the number of tokens in the cached content.
 * @param cachedContentTokenCount
 *   Number of tokens in the cached part of the prompt (the cached content).
 * @param candidatesTokenCount
 *   Total number of tokens across all the generated response candidates.
 * @param totalTokenCount
 *   Total token count for the generation request (prompt + response candidates).
 * @param promptTokensDetails
 *   Output only. List of modalities that were processed in the request input.
 * @param cacheTokensDetails
 *   Output only. List of modalities of the cached content in the request input.
 * @param candidatesTokensDetails
 *   Output only. List of modalities that were returned in the response.
 */
case class UsageMetadata(
  promptTokenCount: Int,
  cachedContentTokenCount: Option[Int] = None,
  candidatesTokenCount: Option[Int] = None,
  totalTokenCount: Int,
  promptTokensDetails: Seq[ModalityTokenCount] = Nil,
  cacheTokensDetails: Seq[ModalityTokenCount] = Nil,
  candidatesTokensDetails: Seq[ModalityTokenCount] = Nil
)

/**
 * Represents token counting info for a single modality.
 *
 * @param modality
 *   The modality associated with this token count.
 * @param tokenCount
 *   Number of tokens.
 */
case class ModalityTokenCount(
  modality: Modality,
  tokenCount: Int
)

/**
 * Logprobs Result
 *
 * @param topCandidates
 *   Length = total number of decoding steps.
 * @param chosenCandidates
 *   Length = total number of decoding steps. The chosen candidates may or may not be in
 *   topCandidates.
 */
case class LogprobsResult(
  topCandidates: Seq[TopCandidates],
  chosenCandidates: Seq[Candidate]
)

/**
 * Candidates with top log probabilities at each decoding step.
 * @param candidates Sorted by log probability in descending order.
 */
case class TopCandidates(
  candidates: Seq[Candidate]
)
