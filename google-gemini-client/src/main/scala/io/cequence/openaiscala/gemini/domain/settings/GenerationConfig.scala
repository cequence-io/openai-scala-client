package io.cequence.openaiscala.gemini.domain.settings

import io.cequence.openaiscala.gemini.domain.{Modality, Schema, ThinkingLevel}

case class GenerationConfig(
  stopSequences: Option[Seq[String]] = None,
  responseMimeType: Option[String] = None,
  responseSchema: Option[Schema] = None,
  responseModalities: Option[Seq[Modality]] = None,
  candidateCount: Option[Int] = None,
  maxOutputTokens: Option[Int] = None,
  temperature: Option[Double] = None,
  topP: Option[Double] = None,
  topK: Option[Int] = None,
  seed: Option[Int] = None,
  presencePenalty: Option[Double] = None,
  frequencyPenalty: Option[Double] = None,
  responseLogprobs: Option[Boolean] = None,
  logprobs: Option[Int] = None,
  enableEnhancedCivicAnswers: Option[Boolean] = None,
  speechConfig: Option[SpeechConfig] = None,
  thinkingConfig: Option[ThinkingConfig] = None
)

sealed trait SpeechConfig

object SpeechConfig {
  case class VoiceConfig(
    prebuiltVoiceConfig: PrebuiltVoiceConfig
  ) extends SpeechConfig
}

case class PrebuiltVoiceConfig(voiceName: String)

/**
 * Config for thinking features.
 *
 * @param includeThoughts
 *   Indicates whether to include thoughts in the response. If true, thoughts are returned only
 *   when available. Value between 128 and 32768.
 * @param thinkingBudget
 *   The number of thought tokens that the model should generate.
 * @param thinkingLevel
 *   Controls the maximum depth of the model's internal reasoning process before it produces a
 *   response. If not specified, the default is HIGH. Recommended for Gemini 3 or later models.
 *   Use with earlier models results in an error.
 */
case class ThinkingConfig(
  includeThoughts: Option[Boolean] = None,
  thinkingBudget: Option[Int] = None,
  thinkingLevel: Option[ThinkingLevel] = None
)
