package io.cequence.openaiscala.gemini.domain.settings

import io.cequence.openaiscala.gemini.domain.{Modality, Schema}

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
  speechConfig: Option[SpeechConfig] = None
)

sealed trait SpeechConfig

object SpeechConfig {
  case class VoiceConfig(
    prebuiltVoiceConfig: PrebuiltVoiceConfig
  ) extends SpeechConfig
}

case class PrebuiltVoiceConfig(voiceName: String)
