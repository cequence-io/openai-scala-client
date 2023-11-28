package io.cequence.openaiscala.examples

import io.cequence.openaiscala.domain.ModelId
import io.cequence.openaiscala.domain.settings.{CreateTranslationSettings, TranscriptResponseFormatType}

// translates to English
object CreateAudioTranslation extends Example {

  override protected def run =
    service.createAudioTranslation(
      file = new java.io.File("openai-examples/src/main/resources/wolfgang.mp3"),
      prompt = Some("Translate to English"),
      settings = CreateTranslationSettings(
        model = ModelId.whisper_1,
        temperature = Some(1),
        response_format = Some(TranscriptResponseFormatType.text)
      )
    ).map( response =>
      println(response.text)
    )
}
