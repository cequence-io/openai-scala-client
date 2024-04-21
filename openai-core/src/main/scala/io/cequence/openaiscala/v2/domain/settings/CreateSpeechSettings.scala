package io.cequence.openaiscala.v2.domain.settings

import io.cequence.openaiscala.domain.EnumValue

case class CreateSpeechSettings(
  // One of the available TTS models: tts-1 or tts-1-hd
  model: String,

  // The voice to use when generating the audio. Supported voices are alloy, echo, fable, onyx, nova, and shimmer.
  voice: VoiceType,

  // The format to audio in. Supported formats are mp3, opus, aac, and flac.
  // Defaults to mp3.
  response_format: Option[SpeechResponseFormatType] = None,

  // The speed of the generated audio. Select a value from 0.25 to 4.0.
  // Defaults to 1.0.
  speed: Option[Double] = None
)

sealed trait SpeechResponseFormatType extends EnumValue

object SpeechResponseFormatType {
  case object mp3 extends SpeechResponseFormatType
  case object opus extends SpeechResponseFormatType
  case object aac extends SpeechResponseFormatType
  case object flac extends SpeechResponseFormatType
}

sealed trait VoiceType extends EnumValue

object VoiceType {
  case object alloy extends VoiceType
  case object echo extends VoiceType
  case object fable extends VoiceType
  case object onyx extends VoiceType
  case object nova extends VoiceType
  case object shimmer extends VoiceType
}
