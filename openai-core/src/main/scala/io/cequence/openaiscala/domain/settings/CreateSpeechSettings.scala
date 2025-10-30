package io.cequence.openaiscala.domain.settings

import io.cequence.wsclient.domain.EnumValue

case class CreateSpeechSettings(
  // One of the available TTS models: tts-1, tts-1-hd or gpt-4o-mini-tts.
  model: String,

  // The voice to use when generating the audio. Supported voices are alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer, and verse.
  voice: VoiceType,

  // Control the voice of your generated audio with additional instructions. Does not work with tts-1 or tts-1-hd.
  instructions: Option[String] = None,

  // The format to audio in. Supported formats are mp3, opus, aac, flac, wav, and pcm.
  // Defaults to mp3.
  response_format: Option[SpeechResponseFormatType] = None,

  // The speed of the generated audio. Select a value from 0.25 to 4.0.
  // Defaults to 1.0.
  speed: Option[Double] = None,

  // The format to stream the audio in. Supported formats are sse and audio.
  // sse is not supported for tts-1 or tts-1-hd.
  // Defaults to audio.
  stream_format: Option[StreamFormatType] = None
)

sealed trait StreamFormatType extends EnumValue

object StreamFormatType {
  case object sse extends StreamFormatType
  case object audio extends StreamFormatType
}

sealed trait SpeechResponseFormatType extends EnumValue

object SpeechResponseFormatType {
  case object mp3 extends SpeechResponseFormatType
  case object opus extends SpeechResponseFormatType
  case object aac extends SpeechResponseFormatType
  case object flac extends SpeechResponseFormatType
  case object wav extends SpeechResponseFormatType
  case object pcm extends SpeechResponseFormatType
}

sealed trait VoiceType extends EnumValue

object VoiceType {
  case object alloy extends VoiceType
  case object ash extends VoiceType
  case object ballad extends VoiceType
  case object coral extends VoiceType
  case object echo extends VoiceType
  case object fable extends VoiceType
  case object onyx extends VoiceType
  case object nova extends VoiceType
  case object sage extends VoiceType
  case object shimmer extends VoiceType
  case object verse extends VoiceType
}
