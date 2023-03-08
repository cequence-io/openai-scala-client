package io.cequence.openaiscala.domain.settings

case class CreateTranscriptionSettings(
  // ID of the model to use. Only whisper-1 is currently available.
  model: String,

  // The format of the transcript output, in one of these options: json, text, srt, verbose_json, or vtt.
  // Defaults to json.
  response_format: Option[TranscriptResponseFormatType.Value] = None,

  // The sampling temperature, between 0 and 1.
  // Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic.
  // If set to 0, the model will use log probability to automatically increase the temperature until certain thresholds are hit.
  // Defaults to 0.
  temperature: Option[Double] = None,

  // The language of the input audio.
  // Supplying the input language in ISO-639-1 ('en', 'de', 'es', etc.) format will improve accuracy and latency.
  language: Option[String] = None
)

object TranscriptResponseFormatType extends Enumeration {
  val json, text, srt, verbose_json, vtt = Value
}
