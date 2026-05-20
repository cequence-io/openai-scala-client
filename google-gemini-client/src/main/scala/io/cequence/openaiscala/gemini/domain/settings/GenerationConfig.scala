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
 * Config for thinking features. On Gemini 3.x set `thinkingLevel`; on Gemini 2.5 set
 * `thinkingBudget`. Setting both on Gemini 3 can return an error.
 *
 * @param includeThoughts
 *   Indicates whether to include thoughts in the response. If true, thoughts are returned only
 *   when available.
 * @param thinkingBudget
 *   The number of thought tokens that the model should generate. Used by Gemini 2.5 models;
 *   accepted for backwards compatibility on Gemini 3 but may yield unexpected performance.
 * @param thinkingLevel
 *   Controls the maximum depth of the model's internal reasoning process before it produces a
 *   response. Valid for Gemini 3 or later models; using it on earlier models results in an
 *   error. Defaults vary by model (e.g. Pro: HIGH, 3.5 Flash: MEDIUM, 3.1 Flash-Lite: MINIMAL).
 */
case class ThinkingConfig(
  includeThoughts: Option[Boolean] = None,
  thinkingBudget: Option[Int] = None,
  thinkingLevel: Option[ThinkingLevel] = None
)
